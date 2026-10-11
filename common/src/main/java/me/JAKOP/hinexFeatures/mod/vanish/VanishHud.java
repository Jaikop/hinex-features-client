package me.JAKOP.hinexFeatures.mod.vanish;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class VanishHud {

    private static final String[] EYE = {
            "   ####   ",
            " ##    ## ",
            "#   ++   #",
            "#  ++++  #",
            "#   ++   #",
            " ##    ## ",
            "   ####   ",
    };
    private static final String[] SLASH = {
            "         #",
            "       ## ",
            "     ##   ",
            "   ##     ",
            " ##       ",
            "#         ",
    };

    private VanishHud() {
    }

    public static void extract(GuiGraphicsExtractor graphics) {
        if (!VanishFx.selfVanished()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Component label = Draw.gradientText("ВАНИШ", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true);
        int textWidth = minecraft.font.width(label);
        int width = textWidth + 30;
        int height = 15;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 4;
        float pulse = 0.75F + 0.25F * (float) Math.sin(Util.getMillis() / 400.0);
        Draw.gradientRoundRect(graphics, x, y, width, height, 7, Draw.withAlpha(Theme.ACCENT_BLUE, pulse), Draw.withAlpha(Theme.ACCENT_PURPLE, pulse));
        Draw.roundRect(graphics, x + 1, y + 1, width - 2, height - 2, 6, 0xE0101326);
        Draw.icon(graphics, EYE, x + 7, y + 4, 0xFFB9C8FF, Theme.ACCENT_PURPLE | 0xFF000000);
        Draw.icon(graphics, SLASH, x + 7, y + 4, 0xFFFF6B8A, 0);
        graphics.text(minecraft.font, label, x + 21, y + 4, 0xFFFFFFFF, false);
    }
}
