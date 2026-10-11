package me.JAKOP.hinexFeatures.mod.vfx;

public final class VfxClock {

    private static final long START = System.nanoTime();

    private static boolean fixed;
    private static double fixedNow;
    private static int frames;

    private VfxClock() {
    }

    public static double now() {
        return fixed ? fixedNow : real();
    }

    private static double real() {
        return (System.nanoTime() - START) / 1.0E9;
    }

    public static float shaderTime() {
        return (float) (now() % 3600.0);
    }

    public static void setFixed(boolean enabled) {
        if (enabled && !fixed) fixedNow = real();
        fixed = enabled;
    }

    public static void advance(double seconds) {
        fixedNow += seconds;
    }

    public static int frames() {
        return frames;
    }

    public static void onFrame() {
        frames++;
    }
}
