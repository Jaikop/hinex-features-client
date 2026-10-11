package me.JAKOP.hinexFeatures.mod.vanish;

import java.util.Locale;

public enum VanishStyle {
    STANDARD(0, 0xFFFFFF, 0xCFE8FF, 0.9),
    ASH(1, 0xFFB347, 0xD4380F, 2.6),
    HOLOGRAM(2, 0x4FE3FF, 0xB46CFF, 1.9),
    GLOW(3, 0xFFE3A0, 0x8A5CFF, 2.3),
    GLITCH(4, 0xFF3B6B, 0x3BFFE8, 1.6),
    VOID(5, 0xB46CFF, 0x3A0CA3, 2.3),
    FROST(6, 0xC9F1FF, 0x4F9BFF, 2.3),
    VANISHED(9, 0x7FB8FF, 0xB46CFF, 0.0);

    public final int id;
    public final int color;
    public final int color2;
    public final double duration;

    VanishStyle(int id, int color, int color2, double duration) {
        this.id = id;
        this.color = color;
        this.color2 = color2;
        this.duration = duration;
    }

    public static VanishStyle parse(String name) {
        try {
            VanishStyle style = valueOf(name.toUpperCase(Locale.ROOT));
            return style == VANISHED ? STANDARD : style;
        } catch (IllegalArgumentException e) {
            return STANDARD;
        }
    }
}
