package me.JAKOP.hinexFeatures.mod.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;

public final class Draw {

    private Draw() {
    }

    public static void crispBold(GuiGraphicsExtractor g, Font font, Component plain, float x, float y, int color, boolean shadow) {
        if (shadow) {
            int shadowColor = ARGB.color(ARGB.alpha(color), ARGB.scaleRGB(color, 0.25F) & 0xFFFFFF);
            textAt(g, font, plain, x + 1.0F, y + 1.0F, shadowColor);
            textAt(g, font, plain, x + 1.5F, y + 1.0F, shadowColor);
        }
        textAt(g, font, plain, x, y, color);
        textAt(g, font, plain, x + 0.5F, y, color);
    }

    private static void textAt(GuiGraphicsExtractor g, Font font, Component text, float x, float y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            int in = inset(r, i);
            g.fill(x + in, y + i, x + w - in, y + i + 1, color);
            g.fill(x + in, y + h - i - 1, x + w - in, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
        roundRect(g, x, y, w, h, r, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void gradientRoundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int left, int right) {
        r = Math.min(r, Math.min(w, h) / 2);
        int col = 0;
        while (col < w) {
            int edge = Math.min(col, w - 1 - col);
            int stripe = edge < r ? 1 : Math.min(2, w - r - col);
            stripe = Math.max(1, stripe);
            int color = ARGB.srgbLerp(w <= 1 ? 0 : (float) col / (w - 1), left, right);
            int top = 0;
            if (edge < r) {
                while (top < r && inset(r, top) > edge) top++;
            }
            g.fill(x + col, y + top, x + col + stripe, y + h - top, color);
            col += stripe;
        }
    }

    public static void accentPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill) {
        gradientRoundRect(g, x, y, w, h, r, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void gradientLine(GuiGraphicsExtractor g, int x, int y, int w, int h, int left, int right) {
        for (int col = 0; col < w; col += 2) {
            int color = ARGB.srgbLerp(w <= 1 ? 0 : (float) col / (w - 1), left, right);
            g.fill(x + col, y, x + Math.min(col + 2, w), y + h, color);
        }
    }

    public static MutableComponent gradientText(String text, int left, int right, boolean bold) {
        MutableComponent result = Component.empty();
        int length = text.codePointCount(0, text.length());
        int index = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            float t = length <= 1 ? 0 : (float) index / (length - 1);
            result.append(Component.literal(Character.toString(cp))
                    .withStyle(Style.EMPTY.withColor(ARGB.srgbLerp(t, left, right) & 0xFFFFFF).withBold(bold)));
            i += Character.charCount(cp);
            index++;
        }
        return result;
    }

    public static void scaledCenteredText(GuiGraphicsExtractor g, Font font, Component text, int centerX, int y, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(centerX, y);
        g.pose().scale(scale, scale);
        g.text(font, text, -font.width(text) / 2, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    public static void icon(GuiGraphicsExtractor g, String[] rows, int x, int y, int color, int accent) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int start = -1;
            char current = ' ';
            for (int col = 0; col <= line.length(); col++) {
                char c = col < line.length() ? line.charAt(col) : ' ';
                if (c != current) {
                    if (start >= 0 && (current == '#' || current == '+')) {
                        g.fill(x + start, y + row, x + col, y + row + 1, current == '#' ? color : accent);
                    }
                    start = col;
                    current = c;
                }
            }
        }
    }

    public static int withAlpha(int color, float alpha) {
        return ARGB.color(Math.round(ARGB.alpha(color) * alpha), color & 0xFFFFFF);
    }
}
