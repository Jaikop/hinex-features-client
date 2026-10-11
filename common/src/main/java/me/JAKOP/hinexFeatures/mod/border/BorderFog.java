package me.JAKOP.hinexFeatures.mod.border;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticle;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class BorderFog {

    private record Zone(boolean enabled, int fog) {
    }

    private static final float DENSE_VISIBILITY = 10.0F;
    private static final float BORDER_VISIBILITY = 5.0F;
    private static final float VEIL_VISIBILITY = 3.0F;
    private static final Object OWNER = new Object();
    private static final float DEPTH_TRIGGER = 0.6F;

    private static boolean enabled;
    private static int dense = 32;
    private static int dayColor = 0xD6DAE2;
    private static int nightColor = 0x30343F;
    private static float lostAfter = 4.0F;
    private static float turnSeconds = 3.0F;
    private static Zone fallback = new Zone(false, 128);
    private static final Map<String, Zone> WORLDS = new HashMap<>();

    private static float haze;
    private static float inner;
    private static float veil;
    private static float lostTimer;
    private static double turnStart = -1.0;
    private static float turned;
    private static float turnSign = 1.0F;
    private static double lastFrame = -1.0;
    private static int mistColor = 0xD6DAE2;

    private BorderFog() {
    }

    public static void handle(JsonObject message) {
        enabled = message.has("enabled") && message.get("enabled").getAsBoolean();
        WORLDS.clear();
        if (!enabled) return;
        dense = Math.max(4, intOf(message, "dense", 32));
        dayColor = colorOf(message, "color", 0xD6DAE2);
        nightColor = colorOf(message, "night", 0x30343F);
        lostAfter = (float) Math.max(0.5, doubleOf(message, "lostAfter", 4.0));
        turnSeconds = (float) Math.max(0.5, doubleOf(message, "turn", 3.0));
        if (message.has("default") && message.get("default").isJsonObject()) fallback = zone(message.getAsJsonObject("default"));
        if (message.has("worlds") && message.get("worlds").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : message.getAsJsonObject("worlds").entrySet()) {
                if (entry.getValue().isJsonObject()) WORLDS.put(entry.getKey(), zone(entry.getValue().getAsJsonObject()));
            }
        }
    }

    public static void reset() {
        enabled = false;
        WORLDS.clear();
        haze = inner = veil = lostTimer = 0.0F;
        turnStart = -1.0;
        lastFrame = -1.0;
    }

    private static Zone zone(JsonObject json) {
        return new Zone(!json.has("enabled") || json.get("enabled").getAsBoolean(), Math.max(8, intOf(json, "fog", 128)));
    }

    private static @Nullable Zone active(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (!enabled || level == null || player == null || player.isCreative() || player.isSpectator()) return null;
        Zone zone = WORLDS.getOrDefault(level.dimension().identifier().toString(), fallback);
        return zone.enabled() ? zone : null;
    }

    public static boolean hidesWall() {
        return active(Minecraft.getInstance()) != null;
    }

    public static void frame(Minecraft minecraft) {
        double now = VfxClock.now();
        float dt = lastFrame < 0.0 ? 0.0F : (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        Zone zone = active(minecraft);
        LocalPlayer player = minecraft.player;
        if (zone == null || player == null || minecraft.isPaused()) {
            haze = approach(haze, 0.0F, dt * 0.8F);
            inner = approach(inner, 0.0F, dt * 0.8F);
            veil = approach(veil, 0.0F, dt * 1.5F);
            turnStart = -1.0;
            lostTimer = 0.0F;
            return;
        }
        WorldBorder border = minecraft.level.getWorldBorder();
        double distance = border.getDistanceToBorder(player);
        float targetHaze = VfxMath.smooth(0.0F, 1.0F, (float) ((zone.fog() - distance) / Math.max(1.0, zone.fog() - dense)));
        float targetInner = VfxMath.clamp01((float) ((dense - distance) / dense));
        haze = approach(haze, targetHaze, dt * 1.2F);
        inner = approach(inner, targetInner, dt * 1.2F);

        if (turnStart >= 0.0) {
            turn(player, now);
        } else if (distance <= dense && facingOutward(player, border)) {
            lostTimer += dt;
            if (lostTimer >= lostAfter || distance <= dense * DEPTH_TRIGGER) {
                turnStart = now;
                turned = 0.0F;
                turnSign = player.getRandom().nextBoolean() ? 1.0F : -1.0F;
                lostTimer = 0.0F;
            }
        } else {
            lostTimer = Math.max(0.0F, lostTimer - dt * 0.5F);
        }
        veil = approach(veil, turnStart >= 0.0 ? 1.0F : 0.0F, dt * (turnStart >= 0.0 ? 2.5F : 0.5F));
        spawnMist(minecraft, player, dt);
    }

    private static void turn(LocalPlayer player, double now) {
        float progress = VfxMath.clamp01((float) ((now - turnStart) / turnSeconds));
        float angle = 180.0F * VfxMath.easeInOut(progress);
        float step = (angle - turned) * turnSign;
        turned = angle;
        if (step != 0.0F) {
            player.turn(step / 0.15, 0.0);
            rotateMist(player.getX(), player.getZ(), step);
        }
        if (progress >= 1.0F) turnStart = -1.0;
    }

    private static boolean facingOutward(LocalPlayer player, WorldBorder border) {
        double x = player.getX(), z = player.getZ();
        double toMinX = x - border.getMinX(), toMaxX = border.getMaxX() - x;
        double toMinZ = z - border.getMinZ(), toMaxZ = border.getMaxZ() - z;
        double nearest = Math.min(Math.min(toMinX, toMaxX), Math.min(toMinZ, toMaxZ));
        double nx = nearest == toMinX ? -1 : nearest == toMaxX ? 1 : 0;
        double nz = nx != 0 ? 0 : nearest == toMinZ ? -1 : 1;
        Vec3 look = player.getViewVector(1.0F);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        return horizontal > 1.0E-4 && (look.x * nx + look.z * nz) / horizontal > 0.2;
    }

    private static float spawnDebt;

    private static void spawnMist(Minecraft minecraft, LocalPlayer player, float dt) {
        float amount = Math.max(haze * 0.6F + inner * 0.4F, veil);
        if (amount < 0.02F) return;
        spawnDebt += dt * 90.0F * amount;
        Vec3 eye = player.getEyePosition();
        float yaw = (float) Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        while (spawnDebt >= 1.0F) {
            spawnDebt -= 1.0F;
            float ahead = VfxMath.lerp(5.0F, 1.5F, veil) + VfxParticles.RANDOM.nextFloat() * VfxMath.lerp(13.0F, 4.5F, veil);
            float side = (VfxParticles.RANDOM.nextFloat() - 0.5F) * VfxMath.lerp(18.0F, 7.0F, veil);
            float up = VfxMath.lerp(-1.5F, -1.0F, veil) + VfxParticles.RANDOM.nextFloat() * VfxMath.lerp(5.0F, 2.6F, veil);
            float speed = 1.4F + VfxParticles.RANDOM.nextFloat() * 1.6F;
            float alpha = (0.16F + VfxParticles.RANDOM.nextFloat() * 0.16F) * Math.min(1.0F, amount * 1.3F) * VfxMath.lerp(1.0F, 1.7F, veil);
            int color = VfxMath.shade(mistColor, VfxMath.lerp(0.78F, 0.66F, veil));
            VfxParticle mist = VfxParticles.spawn()
                    .at(eye.x + fx * ahead - fz * side, eye.y + up, eye.z + fz * ahead + fx * side)
                    .velocity(-fx * speed + (VfxParticles.RANDOM.nextFloat() - 0.5F) * 0.3F,
                            (VfxParticles.RANDOM.nextFloat() - 0.5F) * 0.15F,
                            -fz * speed + (VfxParticles.RANDOM.nextFloat() - 0.5F) * 0.3F)
                    .life(3.0F + VfxParticles.RANDOM.nextFloat() * 2.5F)
                    .size(VfxMath.lerp(2.2F, 0.9F, veil) + VfxParticles.RANDOM.nextFloat() * 1.5F,
                            VfxMath.lerp(3.8F, 1.8F, veil) + VfxParticles.RANDOM.nextFloat() * 1.8F)
                    .color(VfxMath.argb(alpha, color), VfxMath.argb(0.0F, color))
                    .look(VfxShape.SMOKE, VfxLayer.SOFT)
                    .spin(VfxParticles.RANDOM.nextFloat() * VfxMath.TAU, (VfxParticles.RANDOM.nextFloat() - 0.5F) * 0.3F)
                    .fade(0.3F, 0.4F);
            mist.owner = OWNER;
        }
    }

    private static void rotateMist(double cx, double cz, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        VfxParticles.forEach(OWNER, p -> {
            double dx = p.x - cx, dz = p.z - cz;
            p.x = cx + dx * cos - dz * sin;
            p.z = cz + dx * sin + dz * cos;
            float vx = p.vx, vz = p.vz;
            p.vx = vx * cos - vz * sin;
            p.vz = vx * sin + vz * cos;
        });
    }

    public static void applyFog(FogData fog) {
        float strength = Math.max(haze, veil);
        if (strength <= 0.001F) return;
        float vanillaEnd = Math.max(8.0F, Math.min(fog.environmentalEnd, fog.renderDistanceEnd));
        float target = VfxMath.lerp(DENSE_VISIBILITY, BORDER_VISIBILITY, inner);
        float visibility = (float) Math.exp(VfxMath.lerp((float) Math.log(vanillaEnd), (float) Math.log(target), haze));
        visibility = VfxMath.lerp(visibility, VEIL_VISIBILITY, veil);
        fog.environmentalEnd = Math.min(fog.environmentalEnd, visibility);
        fog.environmentalStart = Math.min(fog.environmentalStart, visibility * 0.6F * (1.0F - strength));
        fog.skyEnd = Math.min(fog.skyEnd, visibility);
        fog.cloudEnd = Math.min(fog.cloudEnd, visibility);

        float light = VfxMath.clamp01(Math.max(fog.color.x(), Math.max(fog.color.y(), fog.color.z())) / 0.7F);
        mistColor = VfxMath.lerpColor(nightColor, dayColor, light);
        float blend = VfxMath.smooth(0.0F, 0.6F, strength);
        fog.color.set(VfxMath.lerp(fog.color.x(), VfxMath.red(mistColor), blend),
                VfxMath.lerp(fog.color.y(), VfxMath.green(mistColor), blend),
                VfxMath.lerp(fog.color.z(), VfxMath.blue(mistColor), blend), fog.color.w());
    }

    private static float approach(float value, float target, float step) {
        if (value < target) return Math.min(target, value + step);
        return Math.max(target, value - step);
    }

    private static int intOf(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static double doubleOf(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static int colorOf(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        String value = json.get(key).getAsString().trim();
        if (value.startsWith("#")) value = value.substring(1);
        try {
            return Integer.parseInt(value, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
