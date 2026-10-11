package me.JAKOP.hinexFeatures.mod.vfx.screen;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;

import java.util.Locale;
import java.util.Set;

public final class ScreenEffect {

    public static final Set<String> KNOWN = Set.of(
            "glitch", "vhs", "lens", "hallucination", "drunk", "flash", "noir", "nightvision",
            "thermal", "heartbeat", "heat", "pixel", "chromatic", "fear", "underwater");

    private final String effect;
    private final double start;
    private final double duration;
    private final float intensity;
    private final float fadeIn;
    private final float fadeOut;
    private final int tint;
    private final float speed;
    private final float seed;
    private double stopAt = -1.0;

    public ScreenEffect(String effect, VfxParams params, double start, double duration) {
        this.effect = effect.toLowerCase(Locale.ROOT);
        this.start = start;
        this.duration = duration;
        this.intensity = VfxMath.clamp01(params.number("intensity", 1.0F));
        boolean flash = this.effect.equals("flash");
        this.fadeIn = params.number("fadein", flash ? 0.02F : 0.35F);
        this.fadeOut = params.number("fadeout", flash ? (float) Math.max(0.3, duration * 0.85) : 0.6F);
        this.tint = params.color("color", switch (this.effect) {
            case "heartbeat" -> 0xB0101A;
            case "underwater" -> 0x1E5A8C;
            case "lens" -> 0xFFE7B0;
            default -> 0xFFFFFF;
        });
        this.speed = params.number("speed", 1.0F);
        this.seed = (float) (Math.random() * 100.0);
    }

    public String effect() {
        return effect;
    }

    public void stop(double now) {
        if (stopAt < 0.0) stopAt = now;
    }

    public boolean finished(double now) {
        if (stopAt >= 0.0) return now > stopAt + fadeOut;
        return duration > 0.0 && now > start + duration;
    }

    public ScreenPass pass(double now) {
        float age = (float) (now - start);
        float in = fadeIn <= 0.0F ? 1.0F : VfxMath.clamp01(age / fadeIn);
        float out = 1.0F;
        if (stopAt >= 0.0) {
            out = VfxMath.clamp01(1.0F - (float) (now - stopAt) / Math.max(0.01F, fadeOut));
        } else if (duration > 0.0) {
            out = VfxMath.clamp01((float) (start + duration - now) / Math.max(0.01F, fadeOut));
        }
        float life = duration > 0.0 ? VfxMath.clamp01((float) (age / duration)) : 0.0F;
        float strength = intensity * Math.min(in, out);
        return ScreenPass.simple(effect, strength, (age * speed) % 3600.0F, life, seed, tint, speed);
    }
}
