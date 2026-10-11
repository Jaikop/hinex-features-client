package me.JAKOP.hinexFeatures.mod.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameClient;
import me.JAKOP.hinexFeatures.mod.dialog.editor.DialogEditorScreen;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class DialogClient {

    public record EditorData(Map<String, JsonObject> templates, List<JsonObject> sent) {
    }

    private static final Deque<DialogView> QUEUE = new ArrayDeque<>();
    private static final Random RANDOM = new Random();
    private static @Nullable DialogView current;
    private static int voiced;
    private static boolean answered;
    private static EditorData editorData = new EditorData(Map.of(), List.of());
    private static @Nullable Component editorResult;
    private static int editorVersion;

    private DialogClient() {
    }

    public static void handle(String type, JsonObject message) {
        switch (type) {
            case Protocol.DIALOG -> {
                if (message.has("dialog") && message.get("dialog").isJsonObject()) show(message.getAsJsonObject("dialog"));
            }
            case Protocol.DIALOG_CLOSE -> {
                String id = message.has("id") ? message.get("id").getAsString() : null;
                if (id == null) QUEUE.clear();
                else QUEUE.removeIf(view -> view.spec.id.equals(id));
                if (current != null && (id == null || current.spec.id.equals(id))) current.close();
                if (Minecraft.getInstance().gui.screen() instanceof DialogAnswerScreen screen
                        && (id == null || screen.view().spec.id.equals(id))) {
                    screen.closeSilently();
                }
            }
            case Protocol.DIALOG_EDITOR -> editor(message);
            default -> {
            }
        }
    }

    public static void show(JsonObject json) {
        DialogView view;
        try {
            view = DialogView.of(json);
        } catch (RuntimeException e) {
            HinexFeaturesClient.LOGGER.warn("Некорректный диалог от сервера: {}", e.toString());
            return;
        }
        if (view.spec.replace) {
            QUEUE.clear();
            if (current != null) current.close();
            QUEUE.addFirst(view);
        } else {
            QUEUE.addLast(view);
        }
    }

    private static void start(DialogView view) {
        current = view;
        voiced = 0;
        answered = false;
        view.restart();
        DialogHistory.add(view.spec.json);
        if (!view.spec.sound.isEmpty()) playSound(view.spec.sound);
    }

    public static void tick(Minecraft minecraft) {
        if (current != null && current.closed()) current = null;
        if (current == null && !QUEUE.isEmpty() && minecraft.player != null) start(QUEUE.pollFirst());
        DialogView view = current;
        if (view == null || view.closing()) return;

        if (!view.spec.hasAnswers()) {
            if (view.expired()) finish();
            return;
        }
        if (!answered && view.typed() && minecraft.gui.screen() == null && minecraft.player != null
                && !minecraft.player.isDeadOrDying() && DeathGameClient.session() == null) {
            minecraft.gui.setScreen(new DialogAnswerScreen(view));
        }
        if (!answered && view.spec.timeout > 0.0F && view.age() > view.text.duration + view.spec.timeout) {
            answer(view, -1, null, "timeout");
        }
    }

    public static void extractHud(GuiGraphicsExtractor g) {
        DialogView view = current;
        if (view == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof DialogAnswerScreen) return;
        voice(view);
        draw(g, view, DialogRenderer.Mode.HUD, -1);
    }

    public record Placement(float x, float y, float scale, int width, DialogRenderer.Layout layout) {

        public double screenX(int local) {
            return x + local * scale;
        }

        public double screenY(int local) {
            return y + local * scale;
        }
    }

    public static Placement draw(GuiGraphicsExtractor g, DialogView view, DialogRenderer.Mode mode, int hover) {
        Minecraft minecraft = Minecraft.getInstance();
        Placement placement = placement(view, g.guiWidth(), g.guiHeight());
        float[] appear = DialogRenderer.appear(view);
        int width = placement.width();
        int height = placement.layout().height;
        g.pose().pushMatrix();
        g.pose().translate(placement.x() + appear[0], placement.y() + appear[1]);
        g.pose().scale(placement.scale(), placement.scale());
        g.pose().translate(width / 2.0F, height / 2.0F);
        g.pose().scale(appear[2], appear[2]);
        g.pose().translate(-width / 2.0F, -height / 2.0F);
        DialogRenderer.draw(g, minecraft.font, view, 0, 0, width, appear[3], mode, hover, 0.0F);
        g.pose().popMatrix();
        return placement;
    }

    public static Placement placement(DialogView view, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        float scale = 1.0F;
        int width = Math.min(view.spec.width, screenWidth - 16);
        DialogRenderer.Layout layout = DialogRenderer.layout(view, minecraft.font, width);
        float w = width * scale;
        float h = layout.height * scale;
        int lift = view.spec.offset;
        float x = (screenWidth - w) / 2.0F;
        float y = switch (view.spec.position) {
            case "top" -> 20 - lift;
            case "center" -> (screenHeight - h) / 2.0F - lift;
            default -> screenHeight - bottomReserve(minecraft) - h - lift;
        };
        y = Math.max(2, Math.min(screenHeight - h - 2, y));
        return new Placement(x, y, scale, width, layout);
    }

    private static int bottomReserve(Minecraft minecraft) {
        if (minecraft.gameMode == null || minecraft.player == null) return 26;
        if (!minecraft.gameMode.canHurtPlayer()) return 28;
        var player = minecraft.player;
        int reserve = 43;
        if (player.getArmorValue() > 0 || player.getAbsorptionAmount() > 0 || player.getAirSupply() < player.getMaxAirSupply()) {
            reserve += 10;
        }
        if (player.getMaxHealth() + player.getAbsorptionAmount() > 20.0F) reserve += 10;
        return reserve;
    }

    public static boolean hidesActionBar() {
        DialogView view = current;
        return view != null && !view.closed() && view.spec.position.equals("bottom");
    }

    private static void voice(DialogView view) {
        if (view.spec.voice.equals("none") || view.closing()) return;
        int typed = view.text.typed(view.age());
        if (typed <= voiced) return;
        if (typed - voiced >= 2 || typed == view.text.visible) {
            voiced = typed;
            Holder<SoundEvent> sound;
            float pitch;
            switch (view.spec.voice) {
                case "low" -> {
                    sound = SoundEvents.NOTE_BLOCK_BASS;
                    pitch = 0.9F;
                }
                case "high" -> {
                    sound = SoundEvents.NOTE_BLOCK_CHIME;
                    pitch = 1.4F;
                }
                case "soft" -> {
                    sound = SoundEvents.NOTE_BLOCK_HAT;
                    pitch = 1.6F;
                }
                case "robot" -> {
                    sound = SoundEvents.NOTE_BLOCK_BIT;
                    pitch = 0.7F;
                }
                default -> {
                    sound = SoundEvents.NOTE_BLOCK_BIT;
                    pitch = 1.3F;
                }
            }
            pitch *= 0.92F + RANDOM.nextFloat() * 0.16F;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, 0.18F));
        }
    }

    private static void playSound(String id) {
        Identifier identifier = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (identifier == null) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getOptional(identifier).orElseGet(() -> SoundEvent.createVariableRangeEvent(identifier));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, 0.6F));
    }

    public static void answer(DialogView view, int choice, @Nullable String text, @Nullable String reason) {
        if (answered && view == current) return;
        if (view == current) answered = true;
        JsonObject message = Protocol.message(Protocol.DIALOG_ANSWER);
        message.addProperty("id", view.spec.id);
        String shown;
        if ("dismissed".equals(reason)) {
            message.addProperty("dismissed", true);
            shown = "— без ответа";
        } else if ("timeout".equals(reason)) {
            message.addProperty("timeout", true);
            shown = "— время вышло";
        } else if (choice >= 0) {
            message.addProperty("choice", choice);
            shown = view.spec.options.get(choice);
        } else {
            message.addProperty("text", text == null ? "" : text);
            shown = text == null ? "" : text;
        }
        if (!view.spec.id.isEmpty()) {
            ClientNetwork.send(message);
            DialogHistory.answer(view.spec.id, shown);
        }
        if (Minecraft.getInstance().gui.screen() instanceof DialogAnswerScreen screen && screen.view() == view) {
            screen.closeSilently();
        }
        if (view == current) finish();
    }

    private static void finish() {
        if (current != null) current.close();
    }

    public static @Nullable DialogView current() {
        return current;
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
    }

    private static void editor(JsonObject message) {
        Map<String, JsonObject> templates = new LinkedHashMap<>();
        if (message.has("templates") && message.get("templates").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : message.getAsJsonObject("templates").entrySet()) {
                if (entry.getValue().isJsonObject()) templates.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        }
        List<JsonObject> sent = new ArrayList<>();
        if (message.has("sent") && message.get("sent").isJsonArray()) {
            JsonArray array = message.getAsJsonArray("sent");
            for (JsonElement element : array) if (element.isJsonObject()) sent.add(element.getAsJsonObject());
        }
        editorData = new EditorData(templates, sent);
        if (message.has("result") && message.get("result").isJsonObject()) {
            JsonObject result = message.getAsJsonObject("result");
            editorResult = parseComponent(result.has("text") ? result.get("text").getAsString() : "");
        }
        editorVersion++;
        boolean open = message.has("open") && message.get("open").getAsBoolean();
        Minecraft minecraft = Minecraft.getInstance();
        if (open && !(minecraft.gui.screen() instanceof DialogEditorScreen)) minecraft.gui.setScreen(new DialogEditorScreen());
    }

    private static @Nullable Component parseComponent(String json) {
        if (json.isEmpty()) return null;
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElse(null);
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }

    public static void editorAction(JsonObject action) {
        JsonObject message = Protocol.message(Protocol.DIALOG_EDITOR);
        action.entrySet().forEach(entry -> message.add(entry.getKey(), entry.getValue()));
        ClientNetwork.send(message);
    }

    public static EditorData editorData() {
        return editorData;
    }

    public static int editorVersion() {
        return editorVersion;
    }

    public static @Nullable Component takeEditorResult() {
        Component result = editorResult;
        editorResult = null;
        return result;
    }
}
