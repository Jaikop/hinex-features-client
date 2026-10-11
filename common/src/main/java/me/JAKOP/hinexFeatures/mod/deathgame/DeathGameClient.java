package me.JAKOP.hinexFeatures.mod.deathgame;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.mixin.deathgame.DeathScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

public final class DeathGameClient {

    private static @Nullable DeathGameSession session;

    private DeathGameClient() {
    }

    public static @Nullable DeathGameSession session() {
        return session;
    }

    public static void handle(JsonObject message) {
        String action = message.has("action") ? message.get("action").getAsString() : "";
        try {
            switch (action) {
                case "start" -> {
                    if (session != null) session.end();
                    session = DeathGameSession.start(message);
                    openIfDead();
                }
                case "lost" -> {
                    if (session == null) session = DeathGameSession.lostOnly(message);
                    else session.onLost(message);
                    openIfDead();
                }
                case "allow" -> {
                    if (session == null) {
                        if (message.has("won") && message.get("won").getAsBoolean()) {
                            session = DeathGameSession.wonOnly();
                            openIfDead();
                        }
                    } else {
                        session.onAllow();
                    }
                }
                case "cancel" -> cancel();
                default -> {
                }
            }
        } catch (RuntimeException e) {
            HinexFeaturesClient.LOGGER.error("Испытание после смерти: сообщение {} не обработано", action, e);
            cancel();
        }
    }

    private static void cancel() {
        DeathGameSession old = session;
        session = null;
        if (old != null) old.end();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof DeathGameScreen) minecraft.gui.setScreen(null);
    }

    public static void reset() {
        if (session != null) session.end();
        session = null;
        DeathGameMusic.stopNow();
    }

    private static void openIfDead() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen instanceof DeathScreen || (screen == null && isDead(minecraft.player))) {
            minecraft.gui.setScreen(screen);
        }
    }

    private static boolean isDead(@Nullable LocalPlayer player) {
        return player != null && player.isDeadOrDying();
    }

    public static boolean intercept(@Nullable Screen screen) {
        if (session == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        boolean death = screen instanceof DeathScreen
                || (screen == null && minecraft.level != null && isDead(minecraft.player) && minecraft.player.shouldShowDeathScreen());
        if (!death) return false;
        if (screen instanceof DeathScreen deathScreen) session.setCause(((DeathScreenAccessor) deathScreen).hinex$cause());
        minecraft.gui.setScreen(new DeathGameScreen(session));
        return true;
    }

    public static void tick(Minecraft minecraft) {
        DeathGameSession current = session;
        if (current == null) return;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (player.isDeadOrDying()) {
            current.markDeathSeen();
            Screen screen = minecraft.gui.screen();
            if (screen instanceof DeathScreen) minecraft.gui.setScreen(screen);
        } else if (current.sawDeath()) {
            session = null;
            current.end();
            if (minecraft.gui.screen() instanceof DeathGameScreen) minecraft.gui.setScreen(null);
        } else if (current.age() > 15.0) {
            session = null;
            current.end();
        }
    }

    public static boolean suppressMusic() {
        return session != null;
    }
}
