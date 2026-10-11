package me.JAKOP.hinexFeatures.mod.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import me.JAKOP.hinexFeatures.mod.vanish.VanishFx;
import me.JAKOP.hinexFeatures.mod.vfx.camera.CameraFx;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenEffect;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenPass;
import me.JAKOP.hinexFeatures.mod.vfx.world.WorldEffect;
import me.JAKOP.hinexFeatures.mod.vfx.world.WorldEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class VfxClient {

    public static final String WORLD = "world";
    public static final String SCREEN = "screen";
    public static final String CAMERA = "camera";

    private static final Map<String, WorldEffect> WORLD_EFFECTS = new LinkedHashMap<>();
    private static final Map<String, ScreenEffect> SCREEN_EFFECTS = new LinkedHashMap<>();
    private static final List<ScreenPass> WORLD_SCREEN_PASSES = new ArrayList<>();
    private static double lastFrame = -1.0;
    private static int counter;

    private VfxClient() {
    }

    public static void handle(String type, JsonObject message) {
        switch (type) {
            case Protocol.VFX -> start(message);
            case Protocol.VFX_STOP -> {
                if (message.has("id")) stop(message.get("id").getAsString());
                else stopAll();
            }
            case Protocol.VANISH_FX -> VanishFx.handle(message);
            case Protocol.VANISH_STATE -> VanishFx.handleState(message);
            default -> {
            }
        }
    }

    private static void start(JsonObject message) {
        try {
            String id = message.has("id") ? message.get("id").getAsString() : "vfx-" + (++counter);
            String kind = message.get("kind").getAsString();
            String effect = message.get("effect").getAsString();
            double duration = message.has("duration") ? message.get("duration").getAsDouble() : 5.0;
            Vec3 pos = Vec3.ZERO;
            if (message.has("pos")) {
                JsonArray array = message.getAsJsonArray("pos");
                pos = new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
            }
            UUID follow = message.has("follow") ? UUID.fromString(message.get("follow").getAsString()) : null;
            VfxParams params = new VfxParams(message.has("params") ? message.getAsJsonObject("params") : new JsonObject());
            start(id, kind, effect, duration, pos, follow, params);
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Некорректный эффект от сервера: {}", e.toString());
        }
    }

    public static boolean start(String id, String kind, String effect, double duration, Vec3 pos, @Nullable UUID follow, VfxParams params) {
        String name = effect.toLowerCase(Locale.ROOT);
        double now = VfxClock.now();
        switch (kind) {
            case WORLD -> {
                WorldEffect created = WorldEffects.create(name, params, pos, follow, now, duration);
                if (created == null) return false;
                WorldEffect previous = WORLD_EFFECTS.put(id, created);
                if (previous != null) previous.stop(now);
                return true;
            }
            case SCREEN -> {
                if (!ScreenEffect.KNOWN.contains(name)) return false;
                SCREEN_EFFECTS.put(id, new ScreenEffect(name, params, now, duration));
                return true;
            }
            case CAMERA -> {
                if (!CameraFx.KNOWN.contains(name)) return false;
                CameraFx.start(id, name, params, duration);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public static void stop(String id) {
        double now = VfxClock.now();
        WorldEffect world = WORLD_EFFECTS.get(id);
        if (world != null) world.stop(now);
        ScreenEffect screen = SCREEN_EFFECTS.get(id);
        if (screen != null) screen.stop(now);
        CameraFx.stop(id);
    }

    public static void stopAll() {
        double now = VfxClock.now();
        WORLD_EFFECTS.values().forEach(effect -> effect.stop(now));
        SCREEN_EFFECTS.values().forEach(effect -> effect.stop(now));
        CameraFx.clear();
    }

    public static void reset() {
        WORLD_EFFECTS.clear();
        SCREEN_EFFECTS.clear();
        WORLD_SCREEN_PASSES.clear();
        CameraFx.clear();
        VfxParticles.clear();
        VanishFx.reset();
    }

    public static boolean hasWorldContent() {
        return !WORLD_EFFECTS.isEmpty() || VfxParticles.count() > 0 || VanishFx.hasEffects();
    }

    public static @Nullable VfxFrame buildWorldFrame(CameraRenderState camera, float aspect) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        double now = VfxClock.now();
        float dt = lastFrame < 0.0 ? 0.0F : (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        VfxFrame frame = new VfxFrame(minecraft, minecraft.level, camera.pos, camera.orientation, camera.projectionMatrix,
                camera.viewRotationMatrix, aspect, now, dt, partialTick);

        WORLD_SCREEN_PASSES.clear();
        Iterator<WorldEffect> iterator = WORLD_EFFECTS.values().iterator();
        while (iterator.hasNext()) {
            WorldEffect effect = iterator.next();
            if (effect.finished(now)) {
                iterator.remove();
                continue;
            }
            try {
                effect.render(frame, effect.fade(now));
                ScreenPass pass = effect.screenPass(frame);
                if (pass != null && pass.intensity() > 0.01F) WORLD_SCREEN_PASSES.add(pass);
            } catch (RuntimeException e) {
                HinexFeaturesClient.LOGGER.warn("Эффект упал и удалён", e);
                iterator.remove();
            }
        }
        VanishFx.renderParticles(frame);
        VfxParticles.update(frame);
        return frame;
    }

    public static List<ScreenPass> screenPasses() {
        if (SCREEN_EFFECTS.isEmpty() && WORLD_SCREEN_PASSES.isEmpty()) return Collections.emptyList();
        double now = VfxClock.now();
        List<ScreenPass> passes = new ArrayList<>(WORLD_SCREEN_PASSES);
        WORLD_SCREEN_PASSES.clear();
        Iterator<ScreenEffect> iterator = SCREEN_EFFECTS.values().iterator();
        while (iterator.hasNext()) {
            ScreenEffect effect = iterator.next();
            if (effect.finished(now)) {
                iterator.remove();
                continue;
            }
            ScreenPass pass = effect.pass(now);
            if (pass.intensity() > 0.005F) passes.add(pass);
        }
        return passes;
    }

    public static int activeWorldEffects() {
        return WORLD_EFFECTS.size();
    }

    public static int activeScreenEffects() {
        return SCREEN_EFFECTS.size();
    }
}
