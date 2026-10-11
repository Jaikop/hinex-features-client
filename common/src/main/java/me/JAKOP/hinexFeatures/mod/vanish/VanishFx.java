package me.JAKOP.hinexFeatures.mod.vanish;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticle;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class VanishFx {

    private static final RandomSource RANDOM = VfxParticles.RANDOM;
    private static final Map<UUID, DissolveInstance> ACTIVE = new HashMap<>();
    private static final Map<UUID, DissolveInstance> GHOSTS = new HashMap<>();
    private static final Set<UUID> VISIBLE_VANISHED = new HashSet<>();
    private static volatile Set<UUID> UNLISTED = Set.of();
    private static boolean selfVanished;

    private VanishFx() {
    }

    public static void handle(JsonObject message) {
        try {
            UUID uuid = UUID.fromString(message.get("uuid").getAsString());
            boolean appear = message.has("appear") && message.get("appear").getAsBoolean();
            VanishStyle style = VanishStyle.parse(message.has("style") ? message.get("style").getAsString() : "standard");
            double duration = message.has("duration") ? message.get("duration").getAsDouble() : style.duration;
            Vec3 fallback = null;
            if (message.has("pos")) {
                JsonArray pos = message.getAsJsonArray("pos");
                fallback = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
            }
            play(uuid, appear, style, duration, fallback);
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Некорректный vanish_fx: {}", e.toString());
        }
    }

    public static void handleState(JsonObject message) {
        selfVanished = message.has("self") && message.get("self").getAsBoolean();
        VISIBLE_VANISHED.clear();
        if (message.has("visible")) {
            for (JsonElement element : message.getAsJsonArray("visible")) {
                try {
                    VISIBLE_VANISHED.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        GHOSTS.keySet().retainAll(VISIBLE_VANISHED);

        Set<UUID> unlisted = new HashSet<>();
        if (message.has("unlisted")) {
            for (JsonElement element : message.getAsJsonArray("unlisted")) {
                try {
                    unlisted.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        UNLISTED = Set.copyOf(unlisted);
    }

    public static boolean isUnlisted(UUID player) {
        return UNLISTED.contains(player);
    }

    public static boolean anyUnlisted() {
        return !UNLISTED.isEmpty();
    }

    public static void play(UUID uuid, boolean appear, VanishStyle style, double duration, @Nullable Vec3 fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        DissolveInstance instance = new DissolveInstance(uuid, style, appear, VfxClock.now(), Math.max(0.2, duration));
        Entity entity = minecraft.level.getEntity(uuid);
        if (entity != null) {
            instance.track(entity.getX(), entity.getY(), entity.getZ(), entity.getBbHeight(), entity.getBbWidth());
            if (!appear && entity instanceof Avatar) {
                float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                EntityRenderState snapshot = minecraft.getEntityRenderDispatcher().extractEntity(entity, partialTick);
                snapshot.nameTag = null;
                snapshot.scoreText = null;
                snapshot.shadowPieces.clear();
                snapshot.shadowRadius = 0.0F;
                snapshot.displayFireAnimation = false;
                snapshot.outlineColor = 0;
                snapshot.leashStates = null;
                if (snapshot instanceof VfxRenderState vfx) {
                    vfx.hinex$setHidden(false);
                    vfx.hinex$setDissolve(instance);
                }
                instance.snapshot = snapshot;
                Vec3 pos = entity.getPosition(partialTick);
                instance.track(pos.x, pos.y, pos.z, entity.getBbHeight(), entity.getBbWidth());
            }
        } else if (fallback != null) {
            instance.track(fallback.x, fallback.y, fallback.z, 1.8F, 0.6F);
        } else {
            return;
        }
        ACTIVE.put(uuid, instance);
    }

    public static void reset() {
        ACTIVE.clear();
        GHOSTS.clear();
        VISIBLE_VANISHED.clear();
        UNLISTED = Set.of();
        selfVanished = false;
    }

    public static boolean selfVanished() {
        return selfVanished;
    }

    public static boolean hasEffects() {
        return !ACTIVE.isEmpty();
    }

    public static boolean hideLive(UUID uuid) {
        DissolveInstance instance = ACTIVE.get(uuid);
        return instance != null && !instance.appear;
    }

    public static @Nullable DissolveInstance liveInstance(UUID uuid) {
        DissolveInstance active = ACTIVE.get(uuid);
        if (active != null) {
            return active.appear ? active : null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean self = minecraft.player != null && minecraft.player.getUUID().equals(uuid) && selfVanished;
        if (!self && !VISIBLE_VANISHED.contains(uuid)) return null;
        return GHOSTS.computeIfAbsent(uuid, id -> new DissolveInstance(id, VanishStyle.VANISHED, false, VfxClock.now(), 0.0));
    }

    public static void submitGhosts(LevelRenderState state, SubmitNodeCollector collector) {
        if (ACTIVE.isEmpty()) return;
        double now = VfxClock.now();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Vec3 camera = state.cameraRenderState.pos;
        Iterator<DissolveInstance> iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            DissolveInstance instance = iterator.next();
            if (instance.finished(now)) {
                iterator.remove();
                continue;
            }
            if (instance.snapshot == null) continue;
            try {
                dispatcher.submit(instance.snapshot, state.cameraRenderState, instance.x - camera.x, instance.y - camera.y,
                        instance.z - camera.z, new PoseStack(), new DissolveCollector(collector, instance));
            } catch (RuntimeException e) {
                HinexFeaturesClient.LOGGER.warn("Не удалось нарисовать исчезающего игрока", e);
                instance.snapshot = null;
            }
        }
    }

    public static void renderParticles(VfxFrame frame) {
        for (DissolveInstance instance : ACTIVE.values()) {
            float t = instance.time(frame.now);
            if (t >= 1.0F) continue;
            float p = instance.progress(frame.now);
            try {
                switch (instance.style) {
                    case ASH -> ash(frame, instance, p);
                    case HOLOGRAM -> hologram(frame, instance, t, p);
                    case GLOW -> glow(frame, instance, p);
                    case GLITCH -> glitch(frame, instance, p);
                    case VOID -> voidStyle(frame, instance, t, p);
                    case FROST -> frost(frame, instance, p);
                    default -> standard(frame, instance, t);
                }
            } catch (RuntimeException e) {
                HinexFeaturesClient.LOGGER.warn("Частицы ваниша", e);
            }
        }
    }

    private static int emit(DissolveInstance instance, int slot, float rate, float dt) {
        instance.emitters[slot] += rate * dt;
        int count = (int) instance.emitters[slot];
        instance.emitters[slot] -= count;
        return count;
    }

    private static float rand(float min, float max) {
        return min + RANDOM.nextFloat() * (max - min);
    }

    private static double[] bodyPoint(DissolveInstance instance, float heightFraction, float jitter) {
        float angle = rand(0.0F, VfxMath.TAU);
        float radius = (float) Math.sqrt(RANDOM.nextFloat()) * instance.width * 0.55F;
        float h = VfxMath.clamp01(heightFraction + (RANDOM.nextFloat() - 0.5F) * jitter);
        return new double[]{instance.x + Math.cos(angle) * radius, instance.y + h * instance.height, instance.z + Math.sin(angle) * radius};
    }

    private static VfxParticle inbound(DissolveInstance instance, double[] target, float distance, float life) {
        float angle = rand(0.0F, VfxMath.TAU);
        double sx = target[0] + Math.cos(angle) * distance;
        double sy = target[1] + rand(-0.4F, 0.9F) * distance;
        double sz = target[2] + Math.sin(angle) * distance;
        return VfxParticles.spawn().at(sx, sy, sz)
                .velocity((target[0] - sx) / life, (target[1] - sy) / life, (target[2] - sz) / life)
                .life(life).fade(0.3F, 0.15F);
    }

    private static void ash(VfxFrame frame, DissolveInstance instance, float p) {
        if (p <= 0.02F || p >= 0.995F) return;
        float front = 1.0F - p;
        int count = emit(instance, 0, 240.0F, frame.dt);
        for (int i = 0; i < count; i++) {
            double[] point = bodyPoint(instance, front, 0.12F);
            boolean ember = RANDOM.nextFloat() < 0.4F;
            if (instance.appear) {
                VfxParticle particle = inbound(instance, point, rand(0.6F, 1.4F), rand(0.5F, 0.9F)).size(ember ? 0.03F : 0.05F, 0.015F);
                if (ember) particle.color(VfxMath.argb(1.0F, instance.style.color), VfxMath.argb(1.0F, instance.style.color2)).look(VfxShape.GLOW, VfxLayer.GLOW);
                else particle.color(0xE62A2522, 0xFF3A322D).look(VfxShape.FLAKE, VfxLayer.SOFT).spin(0.0F, rand(-4.0F, 4.0F));
                continue;
            }
            float windX = 0.55F + rand(-0.3F, 0.3F);
            VfxParticle particle = VfxParticles.spawn().at(point[0], point[1], point[2])
                    .velocity(windX + rand(-0.3F, 0.3F), rand(0.5F, 1.4F), rand(-0.35F, 0.35F));
            if (ember) {
                particle.life(rand(0.8F, 1.7F)).size(rand(0.018F, 0.034F), 0.004F)
                        .color(VfxMath.argb(1.0F, 0xFFF1B0), VfxMath.argb(0.9F, instance.style.color2))
                        .look(VfxShape.GLOW, VfxLayer.GLOW).physics(0.6F, 0.25F).flicker(0.55F);
            } else {
                particle.life(rand(1.6F, 2.8F)).size(rand(0.035F, 0.065F), rand(0.02F, 0.04F))
                        .color(0xF0302925, 0x00403833).look(VfxShape.FLAKE, VfxLayer.SOFT)
                        .physics(0.35F, 0.3F).spin(0.0F, rand(-5.0F, 5.0F)).fade(0.05F, 0.5F);
            }
        }
    }

    private static void hologram(VfxFrame frame, DissolveInstance instance, float t, float p) {
        int color = instance.style.color;
        float x = frame.rx(instance.x), y = frame.ry(instance.y), z = frame.rz(instance.z);
        float envelope = VfxMath.fade(t, 0.12F, 0.2F);
        frame.glow.flat(x, y + 0.04F, z, instance.width * 1.6F, (float) frame.now * 0.6F, VfxShape.GRID, 0, VfxMath.argb(0.9F * envelope, color));
        frame.glow.flat(x, y + 0.03F, z, instance.width * 2.2F, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.35F * envelope, color));
        float collapse = VfxMath.smooth(0.8F, 0.95F, p) * (1.0F - VfxMath.smooth(0.95F, 1.0F, p));
        if (collapse > 0.01F) {
            float mid = y + instance.height * 0.5F;
            float half = instance.width * (0.8F + collapse * 1.6F);
            frame.glow.ribbon(x - frame.right.x * half, mid, z - frame.right.z * half, x + frame.right.x * half, mid, z + frame.right.z * half,
                    0.05F + collapse * 0.08F, VfxShape.BOLT, 0.0F, 1.0F, VfxMath.argb(collapse, 0xFFFFFF));
            frame.glow.billboard(frame, x, mid, z, 0.6F * collapse, 0.0F, VfxShape.STAR, 0, VfxMath.argb(collapse, color));
        }
        int count = emit(instance, 0, 90.0F * envelope, frame.dt);
        for (int i = 0; i < count; i++) {
            double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
            VfxParticles.spawn().at(point[0], point[1], point[2]).velocity(0.0, rand(0.3F, 1.1F), 0.0)
                    .life(rand(0.5F, 1.1F)).size(rand(0.02F, 0.04F), 0.0F)
                    .color(VfxMath.argb(1.0F, RANDOM.nextFloat() < 0.8F ? color : instance.style.color2), VfxMath.argb(0.0F, 0xFFFFFF))
                    .look(VfxShape.SQUARE, VfxLayer.GLOW).flicker(0.6F);
        }
    }

    private static void glow(VfxFrame frame, DissolveInstance instance, float p) {
        int color = instance.style.color;
        int color2 = instance.style.color2;
        double cx = instance.x, cy = instance.y + instance.height * 0.5, cz = instance.z;
        float charge = VfxMath.smooth(0.0F, 0.42F, p) * (1.0F - VfxMath.smooth(0.42F, 0.5F, p));
        if (charge > 0.01F) {
            frame.glow.billboard(frame, frame.rx(cx), frame.ry(cy), frame.rz(cz), instance.height * 1.1F * charge, 0.0F, VfxShape.GLOW, 0,
                    VfxMath.argb(0.5F * charge, color));
            int count = emit(instance, 0, 70.0F * charge, frame.dt);
            for (int i = 0; i < count; i++) {
                float angle = rand(0.0F, VfxMath.TAU);
                float distance = rand(0.8F, 1.6F);
                VfxParticles.spawn().at(cx + Math.cos(angle) * distance, cy + rand(-0.9F, 0.9F), cz + Math.sin(angle) * distance)
                        .attract(cx, cy, cz, 12.0F, 4.0F).physics(0.0F, 0.5F).life(0.6F).size(0.04F, 0.01F)
                        .color(0xFFFFFFFF, VfxMath.argb(1.0F, color)).look(VfxShape.SPARK, VfxLayer.GLOW).stretch(0.05F);
            }
        }
        if (!instance.burstDone && p >= 0.45F == !instance.appear) {
            instance.burstDone = true;
            VfxParticles.spawn().at(cx, cy, cz).life(0.5F).size(instance.height * 1.2F, instance.height * 2.6F)
                    .color(0xFFFFFFFF, VfxMath.argb(0.0F, color)).look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
            for (int i = 0; i < 40; i++) {
                double theta = rand(0.0F, VfxMath.TAU);
                float speed = rand(2.0F, 5.0F);
                VfxParticles.spawn().at(cx, cy + rand(-0.6F, 0.6F), cz).velocity(Math.cos(theta) * speed, rand(-1.0F, 2.0F), Math.sin(theta) * speed)
                        .life(rand(0.5F, 1.0F)).size(0.07F, 0.0F).color(0xFFFFFFFF, VfxMath.argb(1.0F, color))
                        .look(VfxShape.STAR, VfxLayer.GLOW).physics(0.0F, 0.9F).spin(0.0F, 6.0F);
            }
        }
        float smoke = VfxMath.smooth(0.52F, 0.6F, p) * (1.0F - VfxMath.smooth(0.97F, 1.0F, p));
        if (smoke > 0.01F) {
            int count = emit(instance, 1, 90.0F * smoke, frame.dt);
            for (int i = 0; i < count; i++) {
                double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
                boolean spark = RANDOM.nextFloat() < 0.25F;
                VfxParticle particle = VfxParticles.spawn().at(point[0], point[1], point[2])
                        .velocity(rand(-0.3F, 0.3F), rand(0.2F, 0.8F), rand(-0.3F, 0.3F));
                if (spark) {
                    particle.life(rand(0.6F, 1.2F)).size(0.03F, 0.0F).color(VfxMath.argb(1.0F, color2), VfxMath.argb(0.0F, 0x20002A))
                            .look(VfxShape.GLOW, VfxLayer.GLOW).flicker(0.4F);
                } else {
                    particle.life(rand(1.0F, 1.8F)).size(rand(0.15F, 0.25F), rand(0.45F, 0.7F)).color(0xC0100618, 0x00180A26)
                            .look(VfxShape.SMOKE, VfxLayer.SOFT).physics(0.3F, 0.5F).spin(0.0F, rand(-1.0F, 1.0F)).fade(0.15F, 0.6F);
                }
            }
        }
    }

    private static void glitch(VfxFrame frame, DissolveInstance instance, float p) {
        if (p <= 0.01F || p >= 0.995F) return;
        int count = emit(instance, 0, 160.0F, frame.dt);
        for (int i = 0; i < count; i++) {
            double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
            float k = RANDOM.nextFloat();
            int color = k < 0.4F ? instance.style.color : k < 0.8F ? instance.style.color2 : 0xFFFFFF;
            float angle = rand(0.0F, VfxMath.TAU);
            float speed = rand(0.5F, 2.8F);
            VfxParticles.spawn().at(point[0], point[1], point[2]).velocity(Math.cos(angle) * speed, rand(-0.2F, 0.6F), Math.sin(angle) * speed)
                    .life(rand(0.25F, 0.7F)).size(rand(0.03F, 0.07F), rand(0.02F, 0.05F)).color(VfxMath.argb(1.0F, color), 0)
                    .look(VfxShape.SQUARE, VfxLayer.GLOW).physics(0.0F, 0.95F).flicker(0.7F).fade(0.0F, 0.1F);
        }
        if (RANDOM.nextFloat() < 0.35F) {
            float h = frame.ry(instance.y + RANDOM.nextFloat() * instance.height);
            float x = frame.rx(instance.x), z = frame.rz(instance.z);
            float half = instance.width * rand(0.7F, 1.6F);
            frame.glow.ribbon(x - frame.right.x * half, h, z - frame.right.z * half, x + frame.right.x * half, h, z + frame.right.z * half,
                    rand(0.01F, 0.04F), VfxShape.BOLT, 0.0F, 1.0F, VfxMath.argb(0.8F, RANDOM.nextBoolean() ? instance.style.color : instance.style.color2));
        }
    }

    private static void voidStyle(VfxFrame frame, DissolveInstance instance, float t, float p) {
        double cx = instance.x, cy = instance.y + instance.height * 0.62, cz = instance.z;
        int color = instance.style.color;
        int count = emit(instance, 0, 140.0F * VfxMath.fade(t, 0.1F, 0.25F), frame.dt);
        for (int i = 0; i < count; i++) {
            double[] point = bodyPoint(instance, p, 0.3F);
            boolean wisp = RANDOM.nextFloat() < 0.3F;
            VfxParticle particle;
            if (instance.appear) {
                float angle = rand(0.0F, VfxMath.TAU);
                float speed = rand(0.8F, 2.2F);
                particle = VfxParticles.spawn().at(cx, cy, cz).velocity(Math.cos(angle) * speed, rand(-1.2F, 0.6F), Math.sin(angle) * speed)
                        .physics(0.0F, 0.7F);
            } else {
                particle = VfxParticles.spawn().at(point[0], point[1], point[2]).attract(cx, cy, cz, 10.0F, 5.0F).physics(0.0F, 0.4F);
            }
            if (wisp) {
                particle.life(rand(0.8F, 1.4F)).size(rand(0.12F, 0.2F), 0.02F).color(0xB0120424, 0).look(VfxShape.SMOKE, VfxLayer.SOFT)
                        .spin(0.0F, rand(-2.0F, 2.0F));
            } else {
                particle.life(rand(0.6F, 1.2F)).size(rand(0.025F, 0.045F), 0.005F)
                        .color(VfxMath.argb(1.0F, RANDOM.nextBoolean() ? color : 0xE6D2FF), VfxMath.argb(1.0F, instance.style.color2))
                        .look(VfxShape.SPARK, VfxLayer.GLOW).stretch(0.05F);
            }
        }
        float hole = VfxMath.smooth(0.2F, 0.6F, t) * (1.0F - VfxMath.smooth(0.9F, 1.0F, t));
        if (hole > 0.01F) {
            float x = frame.rx(cx), y = frame.ry(cy), z = frame.rz(cz);
            frame.glow.billboard(frame, x, y, z, 0.55F * hole, (float) frame.now * 2.0F, VfxShape.RING, 0, VfxMath.argb(0.8F * hole, color));
            frame.soft.billboard(frame, x, y, z, 0.28F * hole, 0.0F, VfxShape.HOLE, 0, VfxMath.argb(hole, 0x000000));
        }
        if (!instance.finalDone && t > 0.92F) {
            instance.finalDone = true;
            VfxParticles.spawn().at(cx, cy, cz).life(0.45F).size(0.4F, 2.4F).color(0xFFFFFFFF, VfxMath.argb(0.0F, color))
                    .look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
            VfxParticles.spawn().at(cx, instance.y + 0.05, cz).life(0.9F).size(0.3F, 3.5F).color(VfxMath.argb(0.9F, color), 0)
                    .look(VfxShape.WAVE, VfxLayer.GLOW).fade(0.0F, 0.8F).flat();
        }
    }

    private static void frost(VfxFrame frame, DissolveInstance instance, float p) {
        int color = instance.style.color;
        float mist = VfxMath.smooth(0.05F, 0.3F, p) * (1.0F - VfxMath.smooth(0.55F, 0.7F, p));
        int count = emit(instance, 0, 40.0F * mist, frame.dt);
        for (int i = 0; i < count; i++) {
            double[] point = bodyPoint(instance, RANDOM.nextFloat() * 0.6F, 0.0F);
            VfxParticles.spawn().at(point[0], point[1], point[2]).velocity(rand(-0.2F, 0.2F), rand(-0.1F, 0.2F), rand(-0.2F, 0.2F))
                    .life(rand(1.0F, 1.6F)).size(0.2F, 0.5F).color(0x50D8F4FF, 0).look(VfxShape.SMOKE, VfxLayer.SOFT).spin(0.0F, 0.6F);
        }
        int sparkles = emit(instance, 1, 30.0F * VfxMath.smooth(0.1F, 0.5F, p), frame.dt);
        for (int i = 0; i < sparkles; i++) {
            double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
            VfxParticles.spawn().at(point[0], point[1], point[2]).life(rand(0.3F, 0.6F)).size(0.06F, 0.0F)
                    .color(0xFFFFFFFF, VfxMath.argb(0.0F, color)).look(VfxShape.STAR, VfxLayer.GLOW).spin(0.0F, 4.0F);
        }
        if (!instance.burstDone && p >= 0.55F == !instance.appear) {
            instance.burstDone = true;
            for (int i = 0; i < 70; i++) {
                double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
                VfxParticle shard;
                if (instance.appear) {
                    shard = inbound(instance, point, rand(1.0F, 2.2F), rand(0.35F, 0.6F));
                } else {
                    float angle = rand(0.0F, VfxMath.TAU);
                    float speed = rand(1.5F, 5.0F);
                    shard = VfxParticles.spawn().at(point[0], point[1], point[2])
                            .velocity(Math.cos(angle) * speed, rand(1.0F, 4.0F), Math.sin(angle) * speed)
                            .physics(-14.0F, 0.2F).floor(instance.y + 0.02).life(rand(0.9F, 1.6F));
                }
                shard.size(rand(0.05F, 0.12F), rand(0.03F, 0.08F)).color(0xE8E4F8FF, 0x00A8D8FF)
                        .look(VfxShape.SHARD, VfxLayer.SOFT).spin(rand(0.0F, 6.0F), rand(-8.0F, 8.0F)).fade(0.0F, 0.3F);
            }
            VfxParticles.spawn().at(instance.x, instance.y + instance.height * 0.5, instance.z).life(0.35F).size(0.8F, 2.0F)
                    .color(0xFFFFFFFF, VfxMath.argb(0.0F, color)).look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
        }
    }

    private static void standard(VfxFrame frame, DissolveInstance instance, float t) {
        if (instance.burstDone || t < (instance.appear ? 0.05F : 0.15F)) return;
        instance.burstDone = true;
        for (int i = 0; i < 28; i++) {
            double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
            VfxParticles.spawn().at(point[0], point[1], point[2]).velocity(rand(-0.6F, 0.6F), rand(0.0F, 0.6F), rand(-0.6F, 0.6F))
                    .life(rand(0.6F, 1.1F)).size(rand(0.18F, 0.28F), rand(0.45F, 0.7F)).color(0xD8FFFFFF, 0x00E0E8F0)
                    .look(VfxShape.SMOKE, VfxLayer.SOFT).physics(0.3F, 0.8F).spin(0.0F, rand(-1.5F, 1.5F)).fade(0.05F, 0.6F);
        }
        for (int i = 0; i < 12; i++) {
            double[] point = bodyPoint(instance, RANDOM.nextFloat(), 0.0F);
            VfxParticles.spawn().at(point[0], point[1], point[2]).velocity(0.0, rand(0.3F, 0.9F), 0.0).life(rand(0.4F, 0.8F)).size(0.05F, 0.0F)
                    .color(0xFFFFFFFF, VfxMath.argb(0.0F, instance.style.color2)).look(VfxShape.STAR, VfxLayer.GLOW).spin(0.0F, 5.0F);
        }
    }
}
