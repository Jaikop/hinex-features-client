package me.JAKOP.hinexFeatures.mod.dialog;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;

public final class DialogRenderer {

    public static final Identifier MISSING_ICON = Identifier.fromNamespaceAndPath("hinexfeatures", "missing_icon");
    public static final int LINE = 10;
    private static final int PAD = 7;
    private static final int OPTION_H = 13;

    public enum Mode {
        HUD,
        ANSWER,
        STATIC
    }

    public static final class Layout {
        public int width;
        public int height;
        public int tabHeight;
        public int boxHeight;
        public int iconSize;
        public int textX;
        public int textY;
        public int titleHeight;
        public int textHeight;
        public int answersY;
        public int answersHeight;
        public final List<int[]> options = new ArrayList<>();
        public int[] input = {0, 0, 0, 0};
    }

    private DialogRenderer() {
    }

    public static Layout layout(DialogView view, Font font, int width) {
        if (view.layout != null && view.layoutWidth == width) return view.layout;
        DialogSpec spec = view.spec;
        Layout layout = new Layout();
        layout.width = width;
        float scale = spec.scale;
        boolean hasTitle = !view.title.getString().isEmpty();
        layout.tabHeight = hasTitle && spec.titleStyle.equals("tab") ? 13 : 0;
        layout.iconSize = view.hasIcon() ? spec.iconSize : 0;
        layout.textX = PAD + (layout.iconSize > 0 ? layout.iconSize + PAD : 0);
        int textWidth = Math.max(40, width - layout.textX - PAD);
        layout.titleHeight = hasTitle && spec.titleStyle.equals("inline") ? Math.round(13 * scale) : 0;
        RichText.layout(view.text, font, Math.max(20, (int) (textWidth / scale)));
        layout.textHeight = Math.round(Math.max(1, view.text.lines()) * LINE * scale);
        layout.textY = PAD + (layout.tabHeight > 0 ? 3 : 0) + layout.titleHeight;

        layout.answersY = layout.textY + layout.textHeight + 5;
        if (spec.answerType.equals("choice")) {
            for (int i = 0; i < spec.options.size(); i++) {
                layout.options.add(new int[]{layout.textX - 2, layout.answersY + i * OPTION_H, textWidth + 4, OPTION_H - 1});
            }
            layout.answersHeight = spec.options.size() * OPTION_H + 2;
        } else if (spec.answerType.equals("text")) {
            int fieldWidth = Math.max(30, Math.round((textWidth + 4) * spec.inputWidth / 100.0F));
            layout.input = new int[]{layout.textX - 2, layout.tabHeight + layout.answersY, fieldWidth, spec.inputHeight};
            layout.answersHeight = spec.inputHeight + 4;
        }
        int content = layout.textY - PAD + layout.textHeight + (layout.answersHeight > 0 ? layout.answersHeight + 5 : 0);
        layout.boxHeight = PAD * 2 + Math.max(layout.iconSize, content) + 2;
        layout.height = layout.tabHeight + layout.boxHeight;
        view.layout = layout;
        view.layoutWidth = width;
        return layout;
    }

    public static Layout draw(GuiGraphicsExtractor g, Font font, DialogView view, int x, int y, int width, float alpha,
                              Mode mode, int hover, float textTime) {
        DialogSpec spec = view.spec;
        Layout layout = layout(view, font, width);
        if (alpha <= 0.02F) return layout;
        float clock = view.age();
        int boxY = y + layout.tabHeight;
        boolean shadow = !light(spec);

        drawBox(g, spec, x, boxY, width, layout.boxHeight, alpha, clock);

        if (layout.tabHeight > 0) {
            int tabWidth = Math.min(width - 8, font.width(view.title) + 15);
            int tabY = y;
            int bg = ARGB.color(Math.min(1.0F, spec.opacity + 0.08F) * alpha, spec.background);
            Draw.roundRect(g, x + 6, tabY, tabWidth, layout.tabHeight + 4, 3, ARGB.color(alpha, spec.border));
            Draw.roundRect(g, x + 7, tabY + 1, tabWidth - 2, layout.tabHeight + 3, 2, bg);
            titleText(g, font, view.title, x + 13, tabY + 3, spec.titleColor, alpha, shadow);
        }

        if (layout.iconSize > 0) {
            int size = layout.iconSize;
            int ix = x + PAD;
            int iy = boxY + PAD;
            g.fill(ix - 1, iy - 1, ix + size + 1, iy + size + 1, ARGB.color(alpha * 0.9F, spec.border));
            g.fill(ix, iy, ix + size, iy + size, ARGB.color(alpha, ARGB.scaleRGB(spec.background | 0xFF000000, 0.6F) & 0xFFFFFF));
            drawIcon(g, font, view, ix, iy, size, alpha);
        }

        int textX = x + layout.textX;
        int textY = boxY + layout.textY;
        if (layout.titleHeight > 0) {
            g.pose().pushMatrix();
            g.pose().translate(textX, boxY + PAD);
            g.pose().scale(spec.scale, spec.scale);
            titleText(g, font, view.title, 0, 0, spec.titleColor, alpha, shadow);
            g.pose().popMatrix();
            int lineY = textY - 3;
            g.fill(textX, lineY, textX + Math.min(width - layout.textX - PAD, font.width(view.title) + 30), lineY + 1,
                    ARGB.color(alpha * 0.45F, spec.border));
        }

        float time = mode == Mode.STATIC ? textTime : view.age();
        g.pose().pushMatrix();
        g.pose().translate(textX, textY);
        g.pose().scale(spec.scale, spec.scale);
        float reveal = spec.appear.equals("fade") ? 0.35F : spec.appear.equals("instant") ? 0.0F : 0.12F;
        int[] cursor = RichText.draw(g, font, view.text, 0, 0, LINE, time, clock, alpha, reveal, shadow);
        if (cursor != null && (int) (clock * 3.0F) % 2 == 0) {
            g.fill(cursor[0], cursor[1] + 8, cursor[0] + 4, cursor[1] + 9, ARGB.color(alpha * 0.7F, spec.textColor));
        }
        g.pose().popMatrix();

        boolean typed = time >= view.text.duration;
        if (typed && spec.answerType.equals("choice")) {
            for (int i = 0; i < layout.options.size(); i++) {
                int[] rect = layout.options.get(i);
                int ox = x + rect[0];
                int oy = boxY + rect[1];
                boolean active = i == hover && mode == Mode.ANSWER;
                if (active) g.fill(ox, oy, ox + rect[2], oy + rect[3], ARGB.color(alpha * 0.22F, spec.border));
                int badge = ARGB.color(alpha * (active ? 1.0F : 0.75F), spec.border);
                g.fill(ox + 2, oy + 1, ox + 12, oy + 11, badge);
                text(g, font, Component.literal(String.valueOf(i + 1)), ox + 5, oy + 2, spec.background, alpha, false);
                int color = active && shadow ? 0xFFFFFF : spec.textColor;
                text(g, font, Component.literal(spec.options.get(i)), ox + 16, oy + 2, color, alpha * (mode == Mode.HUD ? 0.8F : 1.0F), shadow);
            }
        } else if (typed && spec.answerType.equals("text") && mode != Mode.ANSWER) {
            int[] field = layout.input;
            int fx = x + field[0];
            int fy = y + field[1];
            inputField(g, spec, fx, fy, field[2], field[3], alpha);
            String hint = font.plainSubstrByWidth(spec.placeholder.isEmpty() ? "Ваш ответ…" : spec.placeholder, field[2] - 8);
            text(g, font, Component.literal(hint), fx + 4, fy + (field[3] - 8) / 2, ARGB.scaleRGB(spec.inputColor | 0xFF000000, 0.6F), alpha, false);
        }
        if (typed && spec.hasAnswers() && spec.hint && mode == Mode.HUD) {
            String hint = "ответить ▸";
            int pulse = (int) (180 + 75 * Math.sin(clock * 4.0F));
            text(g, font, Component.literal(hint), x + width - PAD - font.width(hint), boxY + layout.boxHeight - 11,
                    spec.border, alpha * pulse / 255.0F, false);
        }

        if (mode == Mode.HUD && !spec.hasAnswers() && typed) {
            float remaining = view.remaining();
            int barWidth = Math.round((width - 12) * remaining);
            g.fill(x + 6, boxY + layout.boxHeight - 3, x + 6 + barWidth, boxY + layout.boxHeight - 2,
                    ARGB.color(alpha * 0.55F, spec.border));
        }
        return layout;
    }

    public static void inputField(GuiGraphicsExtractor g, DialogSpec spec, int x, int y, int w, int h, float alpha) {
        if (spec.inputOpacity > 0.0F) g.fill(x, y, x + w, y + h, ARGB.color(alpha * spec.inputOpacity, spec.inputBackground));
        int border = ARGB.color(alpha * 0.85F, spec.inputBorder);
        switch (spec.inputStyle) {
            case "line" -> g.fill(x, y + h - 1, x + w, y + h, border);
            case "none" -> {
            }
            default -> g.outline(x, y, w, h, border);
        }
    }

    private static void drawIcon(GuiGraphicsExtractor g, Font font, DialogView view, int x, int y, int size, float alpha) {
        if (view.iconProfile() != null) {
            PlayerFaceExtractor.extractRenderState(g, view.iconProfile(), x, y, size);
            return;
        }
        Identifier texture = view.iconTexture();
        if (texture == null) return;
        if (texture.equals(MISSING_ICON)) {
            text(g, font, Component.literal("?"), x + size / 2 - 2, y + size / 2 - 4, 0x8A7A66, alpha, false);
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, size, size, size, size, size, size, ARGB.white(alpha));
    }

    private static void drawBox(GuiGraphicsExtractor g, DialogSpec spec, int x, int y, int w, int h, float alpha, float clock) {
        int bg = ARGB.color(spec.opacity * alpha, spec.background);
        int bg2 = spec.background2 >= 0 ? ARGB.color(spec.opacity * alpha, spec.background2) : bg;
        int border = ARGB.color(alpha, spec.border);
        switch (spec.borderStyle) {
            case "none" -> fill(g, x, y, w, h, bg, bg2);
            case "line" -> {
                fill(g, x, y, w, h, bg, bg2);
                g.outline(x, y, w, h, border);
            }
            case "double" -> {
                fill(g, x, y, w, h, bg, bg2);
                g.outline(x, y, w, h, border);
                g.outline(x + 2, y + 2, w - 4, h - 4, ARGB.color(alpha * 0.6F, spec.border));
            }
            case "thick" -> {
                fill(g, x, y, w, h, bg, bg2);
                g.outline(x, y, w, h, border);
                g.outline(x + 1, y + 1, w - 2, h - 2, border);
            }
            case "glow" -> {
                float pulse = 0.75F + 0.25F * (float) Math.sin(clock * 2.5F);
                for (int i = 4; i >= 1; i--) {
                    Draw.roundRect(g, x - i, y - i, w + i * 2, h + i * 2, 4 + i, ARGB.color(alpha * 0.07F * pulse, spec.border));
                }
                Draw.roundRect(g, x, y, w, h, 4, border);
                roundFill(g, x + 1, y + 1, w - 2, h - 2, 3, bg, bg2);
            }
            case "ornate" -> {
                fill(g, x, y, w, h, bg, bg2);
                g.outline(x, y, w, h, ARGB.color(alpha * 0.7F, spec.border));
                int c = 8;
                int l = x - 1;
                int r = x + w + 1;
                int t = y - 1;
                int b = y + h + 1;
                g.fill(l, t, l + c, t + 3, border);
                g.fill(l, t, l + 3, t + c, border);
                g.fill(r - c, t, r, t + 3, border);
                g.fill(r - 3, t, r, t + c, border);
                g.fill(l, b - 3, l + c, b, border);
                g.fill(l, b - c, l + 3, b, border);
                g.fill(r - c, b - 3, r, b, border);
                g.fill(r - 3, b - c, r, b, border);
                for (int cy : new int[]{y, y + h - 1}) {
                    int cx = x + w / 2;
                    g.fill(cx - 1, cy - 2, cx + 2, cy + 3, border);
                    g.fill(cx - 2, cy - 1, cx + 3, cy + 2, border);
                }
            }
            case "pixel" -> {
                int s = 2;
                g.fill(x + s * 2, y, x + w - s * 2, y + h, border);
                g.fill(x + s, y + s, x + w - s, y + h - s, border);
                g.fill(x, y + s * 2, x + w, y + h - s * 2, border);
                int inner = ARGB.color(Math.max(spec.opacity, 0.0F) * alpha, spec.background);
                g.fill(x + s * 2, y + s, x + w - s * 2, y + h - s, inner);
                g.fill(x + s, y + s * 2, x + w - s, y + h - s * 2, inner);
            }
            default -> {
                Draw.roundRect(g, x, y, w, h, 4, border);
                roundFill(g, x + 1, y + 1, w - 2, h - 2, 3, bg, bg2);
            }
        }
    }

    private static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
        if (top == bottom) g.fill(x, y, x + w, y + h, top);
        else g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    private static void roundFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int top, int bottom) {
        if (top == bottom) {
            Draw.roundRect(g, x, y, w, h, r, top);
            return;
        }
        for (int row = 0; row < h; row++) {
            int inset = row < r ? r - (int) Math.round(Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)))
                    : row >= h - r ? r - (int) Math.round(Math.sqrt(r * r - (row - (h - r) + 0.5) * (row - (h - r) + 0.5))) : 0;
            int color = ARGB.srgbLerp(h <= 1 ? 0.0F : row / (float) (h - 1), top, bottom);
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
        }
    }

    static boolean light(DialogSpec spec) {
        int rgb = spec.background;
        float luminance = (0.2126F * ((rgb >> 16) & 0xFF) + 0.7152F * ((rgb >> 8) & 0xFF) + 0.0722F * (rgb & 0xFF)) / 255.0F;
        return luminance > 0.55F && spec.opacity > 0.5F;
    }

    static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int rgb, float alpha, boolean shadow) {
        if (alpha < 0.04F) return;
        g.text(font, text, x, y, ARGB.color(Math.min(1.0F, alpha), rgb & 0xFFFFFF), shadow);
    }

    private static void titleText(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int rgb, float alpha, boolean shadow) {
        if (alpha < 0.04F) return;
        Draw.crispBold(g, font, title, x, y, ARGB.color(Math.min(1.0F, alpha), rgb & 0xFFFFFF), shadow);
    }

    public static float[] appear(DialogView view) {
        float age = view.age();
        float p = Math.min(1.0F, age / 0.3F);
        float eased = 1.0F - (1.0F - p) * (1.0F - p);
        float alpha = view.closeFade();
        float dx = 0.0F;
        float dy = 0.0F;
        float scale = 1.0F;
        switch (view.spec.window) {
            case "slide" -> {
                dy = (1.0F - eased) * 10.0F;
                alpha *= p;
            }
            case "fade" -> alpha *= p;
            case "pop" -> {
                float back = 1.0F + 2.7F * (float) Math.pow(p - 1.0F, 3) + 1.7F * (float) Math.pow(p - 1.0F, 2);
                scale = 0.85F + 0.15F * back;
                alpha *= Math.min(1.0F, p * 2.0F);
            }
            case "shake" -> {
                alpha *= Math.min(1.0F, p * 2.0F);
                float strength = Math.max(0.0F, 1.0F - age / 0.5F);
                dx = (float) Math.sin(age * 70.0F) * 4.0F * strength;
            }
            default -> {
            }
        }
        return new float[]{dx, dy, scale, alpha};
    }
}
