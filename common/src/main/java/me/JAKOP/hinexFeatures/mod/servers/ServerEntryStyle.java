package me.JAKOP.hinexFeatures.mod.servers;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class ServerEntryStyle {

    private ServerEntryStyle() {
    }

    public static void background(GuiGraphicsExtractor g, int x, int y, int w, int h, PinnedServers.Pin pin) {
        if (pin.main()) {
            float pulse = (float) Math.sin(Util.getMillis() / 900.0) * 0.5f + 0.5f;
            int alpha = 0x30 + (int) (pulse * 0x14);
            Draw.gradientRoundRect(g, x, y, w, h, 4, (alpha << 24) | (Theme.ACCENT_BLUE & 0xFFFFFF),
                    (alpha << 24) | (Theme.ACCENT_PURPLE & 0xFFFFFF));
            bar(g, x, y, h, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        } else {
            Draw.roundRect(g, x, y, w, h, 4, 0x283A4270);
            bar(g, x, y, h, 0xFF5A6394, 0xFF5A6394);
        }
    }

    private static void bar(GuiGraphicsExtractor g, int x, int y, int h, int top, int bottom) {
        g.fillGradient(x, y + 2, x + 1, y + h - 2, top, bottom);
        g.fillGradient(x + 1, y + 1, x + 2, y + h - 1, top, bottom);
    }

    private static final Component BADGE = Draw.gradientText("HINEX", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true);

    public static int badgeWidth() {
        return Minecraft.getInstance().font.width(BADGE) + 8;
    }

    public static void badge(GuiGraphicsExtractor g, int x, int y) {
        Draw.panel(g, x, y, badgeWidth(), 11, 3, 0xC0101222, 0xFF3A3F6E);
        g.text(Minecraft.getInstance().font, BADGE, x + 4, y + 2, 0xFFFFFFFF, false);
    }

    public static void separator(GuiGraphicsExtractor g, int x, int y, int w, String label) {
        Font font = Minecraft.getInstance().font;
        int centerY = y + PinnedServers.SEPARATOR_HEIGHT / 2;
        if (label == null || label.isEmpty()) {
            Draw.gradientLine(g, x + 8, centerY, w - 16, 1, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
            return;
        }
        int textWidth = font.width(label);
        int textX = x + (w - textWidth) / 2;
        int lineWidth = (w - textWidth) / 2 - 14;
        Draw.gradientLine(g, x + 8, centerY, lineWidth, 1, 0x004FC3FF, Theme.ACCENT_BLUE);
        Draw.gradientLine(g, textX + textWidth + 6, centerY, lineWidth, 1, Theme.ACCENT_PURPLE, 0x00B46CFF);
        g.text(font, label, textX, centerY - 4, Theme.TEXT_DIM, false);
    }
}
