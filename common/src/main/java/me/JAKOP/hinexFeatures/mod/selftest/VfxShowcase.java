package me.JAKOP.hinexFeatures.mod.selftest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.vanish.VanishFx;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vanish.VanishStyle;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

final class VfxShowcase {

    interface Steps {
        void add(String name, int delay, int timeout, BooleanSupplier waitFor, Runnable action);
    }

    private record WorldShot(String effect, double dx, double dy, double dz, float pitch, double duration, int frames, String params, boolean follow) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("HinexSelfTest");
    private static final int FRAME = 2;

    private static final List<WorldShot> WORLD = List.of(
            new WorldShot("beam", 0, 0, 7, -28, 4.0, 28, "", false),
            new WorldShot("blackhole", 0, 1.8, 8, -4, 6.0, 38, "", false),
            new WorldShot("highlight", 1, -1, 5, 22, 3.0, 14, "", false),
            new WorldShot("sparks", 0, 0, 6, 8, 3.0, 22, "", false),
            new WorldShot("shockwave", 0, 0, 8, 26, 1.8, 16, "size=9", false),
            new WorldShot("circle", 0, 0, 6, 38, 4.0, 26, "", false),
            new WorldShot("portal", 0, 0, 7, -4, 4.0, 26, "", false),
            new WorldShot("aura", 0, 0, 0, 8, 4.0, 22, "", true),
            new WorldShot("lightning", 0, 0, 10, -22, 1.4, 14, "count=3", false),
            new WorldShot("fireflies", 0, 0, 5, 4, 6.0, 18, "size=5 count=60", false));

    private static final Set<String> ANIMATED_SCREEN = Set.of("glitch", "vhs", "hallucination", "heartbeat", "drunk", "heat");

    private VfxShowcase() {
    }

    static void plan(Steps steps, Minecraft mc, String filter) {
        Set<String> only = filter.isBlank() || filter.equals("true") ? Set.of() : new HashSet<>(Arrays.asList(filter.toLowerCase(Locale.ROOT).split(",")));
        Vec3[] base = new Vec3[1];
        UUID[] mannequin = new UUID[1];

        steps.add("vfx-setup", 80, 2400, () -> mc.level != null && mc.player != null && mc.gui.screen() == null, () -> {
            command(mc, "time set noon");
            command(mc, "weather clear");
            base[0] = mc.player.position();
            command(mc, "summon minecraft:mannequin ~ ~ ~4 {Rotation:[180f,0f]}");
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            if (!DEMO) {
                VfxClock.setFixed(true);
                HinexFeaturesClient.addTickListener(VfxShowcase::clock);
            }
        });
        if (DEMO) {
            steps.add("vfx-demo-pause", 200, 0, () -> true, () -> LOGGER.info("[selftest] demo starts"));
        }
        steps.add("vfx-find", 30, 0, () -> true, () -> {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Avatar && entity != mc.player) mannequin[0] = entity.getUUID();
            }
            LOGGER.info("[selftest] vfx mannequin: {}", mannequin[0]);
            look(mc, 0.0F, 8.0F);
        });

        for (VanishStyle style : VanishStyle.values()) {
            String name = style.name().toLowerCase(Locale.ROOT);
            if (style == VanishStyle.VANISHED || !wanted(only, name, "vanish")) continue;
            int frames = (int) Math.ceil(style.duration * 10.0);
            steps.add("vanish-" + name, 20, 0, () -> true, () -> {
                command(mc, "time set noon");
                look(mc, 0.0F, 8.0F);
                VanishFx.play(mannequin[0], false, style, style.duration, null);
            });
            record(steps, "vfx-vanish-" + name, 0, frames * 2 + 6, frames,
                    () -> VanishFx.play(mannequin[0], true, style, style.duration, null));
        }

        if (wanted(only, "ghost", "vanish")) {
            steps.add("vanish-ghost", 20, 0, () -> true, () -> VanishFx.handleState(state(false, mannequin[0])));
            record(steps, "vfx-vanish-ghost", 0, 6);
            steps.add("vanish-hud", 2, 0, () -> true, () -> {
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                VanishFx.handleState(state(true, mannequin[0]));
            });
            steps.add("vanish-hud-shot", DEMO ? 60 : 10, 0, () -> true, () -> shot("vfx-vanish-hud"));
            steps.add("vanish-reset", 2, 0, () -> true, () -> {
                VanishFx.handleState(new JsonObject());
                if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            });
        }

        for (WorldShot shot : WORLD) {
            if (!wanted(only, shot.effect, "world")) continue;
            steps.add("world-" + shot.effect, 30, 0, () -> true, () -> {
                VfxClient.stopAll();
                command(mc, "time set 12600");
                look(mc, 0.0F, shot.pitch);
                Vec3 pos = base[0].add(shot.dx, shot.dy, shot.dz);
                VfxClient.start("showcase-" + shot.effect, VfxClient.WORLD, shot.effect, shot.duration, pos,
                        shot.follow ? mannequin[0] : null, params(shot.params));
            });
            record(steps, "vfx-world-" + shot.effect, 0, shot.frames);
        }

        boolean anyScreen = ScreenEffect.KNOWN.stream().anyMatch(name -> wanted(only, name, "screen"));
        if (anyScreen) {
            steps.add("screen-scene", 30, 0, () -> true, () -> {
                VfxClient.stopAll();
                look(mc, 0.0F, 14.0F);
                VfxClient.start("scene-circle", VfxClient.WORLD, "circle", 0.0, base[0].add(0, 0, 6), null, params("size=2.5"));
                VfxClient.start("scene-fireflies", VfxClient.WORLD, "fireflies", 0.0, base[0].add(0, 0, 5), null, params("size=4 count=40"));
            });
            for (String effect : ScreenEffect.KNOWN.stream().sorted().toList()) {
                if (!wanted(only, effect, "screen")) continue;
                steps.add("screen-" + effect, 30, 0, () -> true, () ->
                        VfxClient.start("showcase-screen", VfxClient.SCREEN, effect, 3.0, Vec3.ZERO, null, params("")));
                if (ANIMATED_SCREEN.contains(effect)) {
                    record(steps, "vfx-screen-" + effect, 0, 10);
                } else {
                    steps.add("screen-" + effect + "-shot", DEMO ? 50 : 16, 0, () -> true, () -> shot("vfx-screen-" + effect));
                }
                steps.add("screen-" + effect + "-stop", 1, 0, () -> true, () -> VfxClient.stop("showcase-screen"));
            }
            steps.add("screen-done", 20, 0, () -> true, VfxClient::stopAll);
        }
    }

    private static boolean wanted(Set<String> only, String name, String group) {
        return only.isEmpty() || only.contains(name) || only.contains(group);
    }

    private static void record(Steps steps, String prefix, int from, int to) {
        record(steps, prefix, from, to, -1, null);
    }

    private static void record(Steps steps, String prefix, int from, int to, int at, Runnable event) {
        if (DEMO) {
            if (at >= from && event != null) {
                steps.add(prefix + "-play", Math.max(1, (at - from) * FRAME), 0, () -> true, event);
                steps.add(prefix + "-rest", Math.max(1, (to - at) * FRAME), 0, () -> true, () -> {
                });
            } else {
                steps.add(prefix + "-play", Math.max(1, (to - from) * FRAME), 0, () -> true, () -> {
                });
            }
            return;
        }
        steps.add(prefix + "-record", 1, 0, () -> true, () -> {
            recording = true;
            nextFrame = VfxClock.frames() + 2;
        });
        for (int i = from; i < to; i++) {
            String frame = String.format(Locale.ROOT, "%s-%02d", prefix, i);
            int index = i;
            steps.add(frame, 1, 400, () -> VfxClock.frames() >= nextFrame, () -> {
                SelfTest.shot(frame);
                VfxClock.advance(1.0 / 10.0);
                if (index + 1 == at && event != null) event.run();
                nextFrame = VfxClock.frames() + 2;
            });
        }
        steps.add(prefix + "-record-end", 1, 0, () -> true, () -> recording = false);
    }

    private static boolean recording;
    private static int nextFrame;
    private static final boolean DEMO = Boolean.getBoolean("hinex.selftest.vfx.demo");

    private static void shot(String name) {
        if (!DEMO) SelfTest.shot(name);
    }

    private static void clock(Minecraft mc) {
        if (!recording) VfxClock.advance(0.05);
    }

    private static void command(Minecraft mc, String command) {
        if (mc.player != null) mc.player.connection.sendCommand(command);
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
    }

    private static JsonObject state(boolean self, UUID visible) {
        JsonObject json = new JsonObject();
        json.addProperty("self", self);
        JsonArray array = new JsonArray();
        if (visible != null) array.add(visible.toString());
        json.add("visible", array);
        return json;
    }

    private static VfxParams params(String text) {
        JsonObject json = new JsonObject();
        for (String part : text.split(" ")) {
            int eq = part.indexOf('=');
            if (eq > 0) json.addProperty(part.substring(0, eq), part.substring(eq + 1));
        }
        return new VfxParams(json);
    }
}
