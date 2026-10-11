package me.JAKOP.hinexFeatures.mod.vfx.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CameraFx {

    public static final Set<String> KNOWN = Set.of("shake", "roll", "zoom");

    private record Effect(String id, String kind, double start, double duration, float strength, float speed, float fadeIn, float fadeOut) {

        float envelope(double now) {
            float age = (float) (now - start);
            float in = fadeIn <= 0.0F ? 1.0F : VfxMath.clamp01(age / fadeIn);
            float out = duration > 0.0 ? VfxMath.clamp01((float) (start + duration - now) / fadeOut) : 1.0F;
            return Math.min(in, out);
        }
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();

    private CameraFx() {
    }

    public static void start(String id, String kind, VfxParams params, double duration) {
        String name = kind.toLowerCase(Locale.ROOT);
        float strength = params.number("strength", switch (name) {
            case "roll" -> 6.0F;
            case "zoom" -> 1.0F;
            default -> 1.0F;
        });
        float speed = params.number("speed", 1.0F);
        float fadeIn = params.number("fadein", name.equals("zoom") ? 0.08F : 0.1F);
        float fadeOut = params.number("fadeout", (float) Math.max(0.2, Math.min(1.0, duration * 0.5)));
        EFFECTS.removeIf(effect -> effect.id.equals(id));
        EFFECTS.add(new Effect(id, name, VfxClock.now(), duration, strength, speed, fadeIn, fadeOut));
    }

    public static void impulse(float strength, double duration) {
        if (strength <= 0.01F) return;
        EFFECTS.add(new Effect("", "shake", VfxClock.now(), duration, strength, 1.4F, 0.02F, (float) duration * 0.8F));
    }

    public static void stop(String id) {
        double now = VfxClock.now();
        EFFECTS.replaceAll(effect -> {
            if (!effect.id.equals(id) || id.isEmpty()) return effect;
            float fade = Math.min(effect.fadeOut, 0.4F);
            double duration = now - effect.start + fade;
            if (effect.duration > 0.0 && effect.duration < duration) return effect;
            return new Effect(effect.id, effect.kind, effect.start, duration, effect.strength, effect.speed, effect.fadeIn, fade);
        });
    }

    public static void clear() {
        EFFECTS.clear();
    }

    public static void apply(PoseStack poseStack) {
        if (EFFECTS.isEmpty()) return;
        double now = VfxClock.now();
        float shake = 0.0F;
        float shakeSpeed = 1.0F;
        float roll = 0.0F;
        float zoom = 1.0F;
        Iterator<Effect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            Effect effect = iterator.next();
            if (effect.duration > 0.0 && now > effect.start + effect.duration) {
                iterator.remove();
                continue;
            }
            float envelope = effect.envelope(now);
            float t = (float) (now - effect.start) * effect.speed;
            switch (effect.kind) {
                case "shake" -> {
                    shake += effect.strength * envelope;
                    shakeSpeed = Math.max(shakeSpeed, effect.speed);
                }
                case "roll" -> roll += (float) Math.sin(t * 1.3) * effect.strength * envelope;
                case "zoom" -> {
                    float kick = (float) Math.sin(Math.min(1.0F, t / 0.35F) * Math.PI);
                    zoom += 0.12F * effect.strength * envelope * Math.max(kick, envelope * 0.4F);
                }
                default -> {
                }
            }
        }
        if (shake > 0.0F) {
            double t = now * 22.0 * shakeSpeed;
            float yaw = noise(t, 1.3) * shake * 0.9F;
            float pitch = noise(t, 7.1) * shake * 0.9F;
            float tilt = noise(t, 13.7) * shake * 1.4F;
            poseStack.rotate(Axis.ZP.rotationDegrees(tilt));
            poseStack.rotate(Axis.XP.rotationDegrees(pitch));
            poseStack.rotate(Axis.YP.rotationDegrees(yaw));
        }
        if (roll != 0.0F) {
            poseStack.rotate(Axis.ZP.rotationDegrees(roll));
        }
        if (zoom != 1.0F) {
            poseStack.scale(zoom, zoom, 1.0F);
        }
    }

    private static float noise(double t, double offset) {
        return (float) ((Math.sin(t + offset) * 0.5 + Math.sin(t * 2.17 + offset * 1.7) * 0.3 + Math.sin(t * 4.31 + offset * 2.3) * 0.2));
    }
}
