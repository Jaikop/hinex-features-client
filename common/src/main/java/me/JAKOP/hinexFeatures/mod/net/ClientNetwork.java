package me.JAKOP.hinexFeatures.mod.net;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.admin.MacroRunner;
import me.JAKOP.hinexFeatures.mod.admin.WorkspaceClient;
import me.JAKOP.hinexFeatures.mod.border.BorderFog;
import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameClient;
import me.JAKOP.hinexFeatures.mod.dialog.DialogClient;
import me.JAKOP.hinexFeatures.mod.dossier.ClientDossier;
import me.JAKOP.hinexFeatures.mod.dossier.HardwareInfo;
import me.JAKOP.hinexFeatures.mod.license.LicenseChecker;
import me.JAKOP.hinexFeatures.mod.marks.PlayerMarks;
import me.JAKOP.hinexFeatures.mod.plus.PlusClient;
import me.JAKOP.hinexFeatures.mod.essence.EssenceClient;
import me.JAKOP.hinexFeatures.mod.typing.TypingIndicators;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class ClientNetwork {

    public static final int MAX_STATE_SIZE = 1 << 20;

    private static volatile @Nullable ServerState state;
    private static @Nullable ClientPacketListener connection;
    private static boolean helloPending;
    private static boolean dossierSent;

    private ClientNetwork() {
    }

    public static @Nullable ServerState state() {
        return state;
    }

    public static void tick(Minecraft minecraft) {
        ClientPacketListener current = minecraft.getConnection();
        if (current != connection) {
            connection = current;
            state = null;
            helloPending = current != null;
            dossierSent = false;
            TypingIndicators.reset();
            PlayerMarks.reset();
            PlusClient.reset();
            EssenceClient.reset();
            VfxClient.reset();
            BorderFog.reset();
            DeathGameClient.reset();
            DialogClient.reset();
            WorkspaceClient.reset();
            MacroRunner.stopAll();
        }

        if (helloPending && current != null && HinexFeaturesClient.license().status() != LicenseChecker.Status.CHECKING) {
            helloPending = !sendHello();
        }
    }

    public static void receive(byte[] data) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> receive(data));
            return;
        }

        JsonObject message;
        try {
            message = Protocol.decode(data);
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Некорректное сообщение от сервера: {}", e.toString());
            return;
        }
        if (message == null || !message.has("type")) return;

        switch (message.get("type").getAsString()) {
            case Protocol.REQUEST_HELLO -> helloPending = true;
            case Protocol.TYPING -> {
                try {
                    TypingIndicators.onRemote(UUID.fromString(message.get("uuid").getAsString()),
                            message.has("typing") && message.get("typing").getAsBoolean());
                } catch (Exception ignored) {
                }
            }
            case Protocol.STATE -> {
                state = ServerState.parse(message);
                if (!dossierSent) {
                    dossierSent = true;
                    ClientPacketListener sentFor = connection;
                    HardwareInfo.whenReady(() -> Minecraft.getInstance().execute(() -> {
                        if (connection == sentFor) sendDossier();
                    }));
                }
            }
            case Protocol.VFX, Protocol.VFX_STOP, Protocol.VANISH_FX, Protocol.VANISH_STATE ->
                    VfxClient.handle(message.get("type").getAsString(), message);
            case Protocol.DEATH_GAME -> DeathGameClient.handle(message);
            case Protocol.DIALOG, Protocol.DIALOG_CLOSE, Protocol.DIALOG_EDITOR ->
                    DialogClient.handle(message.get("type").getAsString(), message);
            case Protocol.WORKSPACE -> WorkspaceClient.handle(message);
            case Protocol.PLAYER_MARK -> PlayerMarks.handle(message);
            case Protocol.PLUS -> PlusClient.handle(message);
            case Protocol.ESSENCE -> EssenceClient.handleEssence(message);
            case Protocol.KNOWLEDGE -> EssenceClient.handleTree(message);
            case Protocol.BORDER -> BorderFog.handle(message);
            default -> {
            }
        }
    }

    public static boolean send(JsonObject message) {
        if (Minecraft.getInstance().getConnection() == null) return false;
        try {
            return HinexFeaturesClient.platform().sendToServer(Protocol.encode(message));
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Не удалось отправить сообщение серверу: {}", e.toString());
            return false;
        }
    }

    private static boolean sendHello() {
        if (Minecraft.getInstance().hasSingleplayerServer()) return true;

        JsonObject hello = Protocol.message(Protocol.HELLO);
        hello.addProperty("protocol", Protocol.VERSION);
        hello.addProperty("modVersion", HinexFeaturesClient.version());
        hello.addProperty("license", HinexFeaturesClient.license().status().name().toLowerCase(Locale.ROOT));
        hello.addProperty("tosAccepted", HinexFeaturesClient.state().tosAccepted());
        com.google.gson.JsonArray mods = new com.google.gson.JsonArray();
        HinexFeaturesClient.platform().mods().forEach(mod -> mods.add(mod.id()));
        hello.add("mods", mods);
        return send(hello);
    }

    private static void sendDossier() {
        JsonObject dossier = Protocol.message(Protocol.DOSSIER);
        try {
            dossier.add("client", ClientDossier.collect());
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Не удалось собрать данные клиента", e);
            return;
        }
        send(dossier);
    }
}
