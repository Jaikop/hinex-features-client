package me.JAKOP.hinexFeatures.mod.vfx;

public final class VfxMath {

    public static final float TAU = (float) (Math.PI * 2.0);

    private VfxMath() {
    }

    public static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float smooth(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0F - 2.0F * t);
    }

    public static float easeOutCubic(float t) {
        float f = 1.0F - clamp01(t);
        return 1.0F - f * f * f;
    }

    public static float easeInCubic(float t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3.0) + c1 * (float) Math.pow(t - 1.0F, 2.0);
    }

    public static float fade(float t, float fadeIn, float fadeOut) {
        float in = fadeIn <= 0.0F ? 1.0F : clamp01(t / fadeIn);
        float out = fadeOut <= 0.0F ? 1.0F : clamp01((1.0F - t) / fadeOut);
        return Math.min(in, out);
    }

    public static int argb(float a, int rgb) {
        return ((int) (clamp01(a) * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }

    public static int multiplyAlpha(int color, float factor) {
        int alpha = (int) ((color >>> 24) * clamp01(factor));
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    public static int lerpColor(int from, int to, float t) {
        t = clamp01(t);
        int a = (int) lerp(from >>> 24, to >>> 24, t);
        int r = (int) lerp(from >> 16 & 255, to >> 16 & 255, t);
        int g = (int) lerp(from >> 8 & 255, to >> 8 & 255, t);
        int b = (int) lerp(from & 255, to & 255, t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static int shade(int rgb, float factor) {
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * factor));
        int g = Math.min(255, (int) ((rgb >> 8 & 255) * factor));
        int b = Math.min(255, (int) ((rgb & 255) * factor));
        return r << 16 | g << 8 | b;
    }

    public static float red(int rgb) {
        return (rgb >> 16 & 255) / 255.0F;
    }

    public static float green(int rgb) {
        return (rgb >> 8 & 255) / 255.0F;
    }

    public static float blue(int rgb) {
        return (rgb & 255) / 255.0F;
    }
}
