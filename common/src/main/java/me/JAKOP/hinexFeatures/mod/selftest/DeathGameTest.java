package me.JAKOP.hinexFeatures.mod.selftest;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameClient;
import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameScreen;
import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameSession;
import me.JAKOP.hinexFeatures.mod.deathgame.TombGame;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.effect.MobEffectInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

final class DeathGameTest {

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");

    private static String prefix = "";
    private static boolean recording;
    private static int frame;
    private static int nextFrame;
    private static int extra;
    private static BooleanSupplier until = () -> true;
    private static boolean autopilot;
    private static int idleFrames;
    private static long lostAtMillis;

    private DeathGameTest() {
    }

    static void plan(VfxShowcase.Steps steps, Minecraft mc, BooleanSupplier joined, String connect) {
        steps.add("dg-joined", 20, 1200, joined, () -> {
            mc.options.pauseOnLostFocus = false;
            if (mc.player.isDeadOrDying()) mc.player.respawn();
            VfxClock.setFixed(true);
            HinexFeaturesClient.addTickListener(DeathGameTest::tick);
        });
        steps.add("dg-setup", 100, 0, () -> mc.player != null && !mc.player.isDeadOrDying(), () -> {
            command(mc, "gamemode survival");
            command(mc, "time set 13000");
            command(mc, "effect clear @s");
        });

        kill(steps, mc, "win");
        steps.add("dg-win-record", 1, 0, () -> true, () -> record("dg-win", 12,
                () -> session() != null && session().phase() == DeathGameSession.Phase.WON && session().allowed(), 14, true));
        steps.add("dg-win-done", 1, 6000, () -> !recording, () -> {
            DeathGameSession session = session();
            TombGame game = session != null ? session.game() : null;
            LOGGER.info("[selftest] deathgame win: phase={}, allowed={}, time={}, dots={}, moves={}",
                    session != null ? session.phase() : null, session != null && session.allowed(),
                    game != null ? String.format(Locale.ROOT, "%.1f", game.time()) : "-", game != null ? game.collected() : -1,
                    game != null ? game.moves() : -1);
            SelfTest.shot("dg-won");
        });
        respawnAndCheck(steps, mc, "win");

        kill(steps, mc, "lose");
        steps.add("dg-lose-wait-play", 1, 600, () -> phase(DeathGameSession.Phase.PLAY), () -> LOGGER.info("[selftest] deathgame lose: игра началась, стоим"));
        steps.add("dg-lose-record", 1, 600, () -> {
            DeathGameSession session = session();
            TombGame game = session != null ? session.game() : null;
            return game != null && game.y() - game.tide() < 2.5F;
        }, () -> record("dg-lose", 12, () -> phase(DeathGameSession.Phase.LOST), 30, false));
        steps.add("dg-lose-lost", 1, 3000, () -> !recording, () -> {
            LOGGER.info("[selftest] deathgame lose: death={}, ждём разрешения сервера", session() != null && session().game() != null ? session().game().death() : null);
        });
        steps.add("dg-lose-countdown-shot", 60, 0, () -> true, () -> SelfTest.shot("dg-lost-countdown"));
        steps.add("dg-lose-allowed", 1, 1200, () -> session() != null && session().allowed(), () -> {
            LOGGER.info("[selftest] deathgame lose: сервер разрешил через {} с после поражения",
                    String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - lostAtMillis) / 1000.0));
            SelfTest.shot("dg-lost-allowed");
        });
        respawnAndCheck(steps, mc, "lose");
        steps.add("dg-clear", 20, 0, () -> true, () -> command(mc, "effect clear @s"));

        kill(steps, mc, "quit");
        steps.add("dg-quit-play", 40, 600, () -> phase(DeathGameSession.Phase.PLAY), () -> {
            LOGGER.info("[selftest] deathgame quit: выходим посреди игры");
            if (mc.level != null) mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
            mc.disconnectWithSavingScreen();
            mc.gui.setScreen(new TitleScreen());
        });
        steps.add("dg-quit-reconnect", 60, 600, () -> mc.level == null, () ->
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(connect),
                        new ServerData("test", connect, ServerData.Type.OTHER), false, null));
        steps.add("dg-quit-lost", 20, 1200, () -> mc.level != null && ClientNetwork.state() != null
                && mc.gui.screen() instanceof DeathGameScreen && session() != null && session().phase() == DeathGameSession.Phase.LOST, () -> {
            LOGGER.info("[selftest] deathgame quit: после перезахода — поражение");
            SelfTest.shot("dg-quit-lost");
        });
        steps.add("dg-quit-allowed", 1, 1200, () -> session() != null && session().allowed(), () -> LOGGER.info("[selftest] deathgame quit: разрешено"));
        respawnAndCheck(steps, mc, "quit");
        steps.add("dg-clear-2", 20, 0, () -> true, () -> command(mc, "effect clear @s"));

        kill(steps, mc, "cheat");
        steps.add("dg-cheat-play", 40, 600, () -> phase(DeathGameSession.Phase.PLAY), () -> {
            LOGGER.info("[selftest] deathgame cheat: шлём возрождение без разрешения");
            mc.player.respawn();
        });
        steps.add("dg-cheat-result", 60, 0, () -> true, () -> {
            boolean dead = mc.player != null && mc.player.isDeadOrDying();
            LOGGER.info("[selftest] deathgame cheat: после попытки игрок {}", dead ? "всё ещё мёртв (сервер не пустил)" : "возродился");
        });
        steps.add("dg-cheat-effects", 30, 0, () -> true, () -> {
            if (mc.player != null && !mc.player.isDeadOrDying()) {
                LOGGER.info("[selftest] deathgame cheat: эффекты {}", effects(mc));
            } else {
                autopilot = true;
            }
        });
        steps.add("dg-cheat-finish", 1, 3600, () -> mc.player != null && (!mc.player.isDeadOrDying()
                || session() != null && session().canRespawn()), () -> {
            if (mc.player.isDeadOrDying() && session() != null) session().respawn();
            autopilot = false;
        });
        steps.add("dg-end", 60, 0, () -> true, () -> {
            command(mc, "effect clear @s");
            VfxClock.setFixed(false);
        });
    }

    static void planCamera(VfxShowcase.Steps steps, Minecraft mc, BooleanSupplier joined) {
        steps.add("cam-joined", 20, 1200, joined, () -> {
            mc.options.pauseOnLostFocus = false;
            if (mc.player.isDeadOrDying()) mc.player.respawn();
        });
        steps.add("cam-setup", 100, 0, () -> mc.player != null && !mc.player.isDeadOrDying(), () -> {
            command(mc, "time set noon");
            command(mc, "weather clear");
            mc.player.setXRot(12.0F);
            mc.player.xRotO = 12.0F;
        });
        steps.add("cam-base", 40, 0, () -> true, () -> {
            mc.gui.setScreen(null);
            SelfTest.shot("cam-0-base");
        });
        steps.add("cam-roll", 1, 0, () -> true, () -> command(mc, "vfx camera roll strength=25 duration=4 fadein=0.3"));
        steps.add("cam-roll-shot", 16, 0, () -> true, () -> SelfTest.shot("cam-1-roll"));
        steps.add("cam-zoom", 90, 0, () -> true, () -> command(mc, "vfx camera zoom strength=4 duration=1.2"));
        steps.add("cam-zoom-shot", 4, 0, () -> true, () -> SelfTest.shot("cam-2-zoom"));
        steps.add("cam-shake", 40, 0, () -> true, () -> command(mc, "vfx camera shake strength=5 duration=2"));
        steps.add("cam-shake-shot", 8, 0, () -> true, () -> SelfTest.shot("cam-3-shake"));
        steps.add("cam-shake-shot2", 3, 0, () -> true, () -> SelfTest.shot("cam-4-shake"));
        steps.add("cam-stop", 1, 0, () -> true, () -> command(mc, "vfx camera roll strength=15 duration=0 id=endless"));
        steps.add("cam-stop-cmd", 30, 0, () -> true, () -> command(mc, "vfx stop id=endless"));
        steps.add("cam-stopped-shot", 30, 0, () -> true, () -> SelfTest.shot("cam-5-stopped"));
    }

    private static void kill(VfxShowcase.Steps steps, Minecraft mc, String name) {
        steps.add("dg-" + name + "-kill", 40, 400, () -> mc.player != null && !mc.player.isDeadOrDying() && mc.gui.screen() == null, () -> {
            LOGGER.info("[selftest] deathgame {}: /kill", name);
            command(mc, "kill @s");
        });
        steps.add("dg-" + name + "-screen", 1, 400, () -> mc.gui.screen() instanceof DeathGameScreen, () ->
                LOGGER.info("[selftest] deathgame {}: экран испытания открыт (seed {})", name, session() != null ? session().seed() : 0));
    }

    private static void respawnAndCheck(VfxShowcase.Steps steps, Minecraft mc, String name) {
        steps.add("dg-" + name + "-respawn", 20, 600, () -> session() != null && session().canRespawn(), () -> session().respawn());
        steps.add("dg-" + name + "-alive", 30, 600, () -> mc.player != null && !mc.player.isDeadOrDying() && DeathGameClient.session() == null,
                () -> LOGGER.info("[selftest] deathgame {}: возродился, экран {}", name,
                        mc.gui.screen() == null ? "игра" : mc.gui.screen().getClass().getSimpleName()));
        steps.add("dg-" + name + "-effects", 30, 0, () -> true, () -> {
            LOGGER.info("[selftest] deathgame {}: эффекты после возрождения: {}", name, effects(mc));
            SelfTest.shot("dg-" + name + "-respawned");
        });
    }

    private static String effects(Minecraft mc) {
        if (mc.player == null) return "-";
        return mc.player.getActiveEffects().stream()
                .map(MobEffectInstance::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static DeathGameSession session() {
        return DeathGameClient.session();
    }

    private static boolean phase(DeathGameSession.Phase phase) {
        DeathGameSession session = session();
        return session != null && session.phase() == phase;
    }

    private static void record(String name, int fps, BooleanSupplier stop, int extraFrames, boolean pilot) {
        prefix = name;
        frame = 0;
        extra = extraFrames;
        until = stop;
        recording = true;
        autopilot = pilot;
        idleFrames = 0;
        nextFrame = VfxClock.frames() + 2;
        recordFps = fps;
    }

    private static int recordFps = 12;

    private static void tick(Minecraft mc) {
        if (phase(DeathGameSession.Phase.DYING) && lostAtMillis == 0) lostAtMillis = System.currentTimeMillis();
        if (!recording) {
            VfxClock.advance(0.05);
            pilot();
            return;
        }
        if (VfxClock.frames() < nextFrame) return;
        SelfTest.shot(String.format(Locale.ROOT, "%s-%03d", prefix, frame++));
        VfxClock.advance(1.0 / recordFps);
        pilot();
        nextFrame = VfxClock.frames() + 2;
        if (until.getAsBoolean() && --extra <= 0 || frame > 900) {
            recording = false;
            autopilot = false;
            LOGGER.info("[selftest] запись {}: {} кадров", prefix, frame);
        }
    }

    private static void pilot() {
        if (!autopilot) return;
        DeathGameSession session = session();
        if (session == null || session.phase() != DeathGameSession.Phase.PLAY || session.game() == null) return;
        TombGame game = session.game();
        if (game.moving()) {
            idleFrames = 0;
            return;
        }
        if (++idleFrames < 3) return;
        int dir = game.autopilot();
        if (dir >= 0) {
            session.input(dir);
            idleFrames = 0;
        }
    }

    private static void command(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }
}
