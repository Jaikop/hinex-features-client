package me.JAKOP.hinexFeatures.mod.vfx.screen;

public record ScreenPass(String effect, float intensity, float time, float life, float seed, int tint, float extra,
                         float pointX, float pointY, float radius, float strength, float speed) {

    public static ScreenPass simple(String effect, float intensity, float time, float life, float seed, int tint, float speed) {
        return new ScreenPass(effect, intensity, time, life, seed, tint, 0.0F, 0.5F, 0.5F, 0.0F, 0.0F, speed);
    }
}
