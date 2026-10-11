package me.JAKOP.hinexFeatures.mod.plus;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.chat.MiniPreview;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

final class ColorPicker {

    private static final int HUE_W = 10;
    private static final int SV_H = 64;
    static final int COLUMN_W = 64;
    private static final List<Integer> NAMED = new ArrayList<>(MiniPreview.NAMED.values());

    private final IntConsumer preview;
    private final IntConsumer commit;

    private int x;
    private int y;
    private int svW;
    private int swatch;
    private int swatchY;
    private int recentY;

    private int rgb = 0xFFFFFF;
    private float hue;
    private float saturation;
    private float value = 1f;
    private boolean draggingSv;
    private boolean draggingHue;

    private boolean editingHex;
    private String hexBuffer = "";

    ColorPicker(IntConsumer preview, IntConsumer commit) {
        this.preview = preview;
        this.commit = commit;
    }

    void setBounds(int x, int y, int width) {
        this.x = x;
        this.y = y;
        svW = width - HUE_W - 4 - 8 - COLUMN_W;
        swatch = Math.min(11, (width + 2) / 16 - 2);
        swatchY = y + SV_H + 6;
        recentY = swatchY + swatch + 3;
    }

    int height() {
        return SV_H + 6 + swatch + 3 + swatch;
    }

    int columnX() {
        return x + svW + 4 + HUE_W + 8;
    }

    void setColor(int color) {
        if (draggingSv || draggingHue) return;
        rgb = color & 0xFFFFFF;
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0 ? 0 : delta / max;
        if (delta == 0) return;
        float h;
        if (max == r) h = ((g - b) / delta) % 6;
        else if (max == g) h = (b - r) / delta + 2;
        else h = (r - g) / delta + 4;
        hue = ((h / 6f) % 1f + 1f) % 1f;
    }

    boolean dragging() {
        return draggingSv || draggingHue;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private void pick(int color) {
        setColor(color);
        commit.accept(rgb);
        HinexFeaturesClient.state().useColor(rgb);
    }

    void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int selected) {
        for (int col = 0; col < svW; col++) {
            int top = Mth.hsvToRgb(hue, (float) col / (svW - 1), 1f) | 0xFF000000;
            g.fillGradient(x + col, y, x + col + 1, y + SV_H, top, 0xFF000000);
        }
        int markerX = x + Math.round(saturation * (svW - 1));
        int markerY = y + Math.round((1 - value) * (SV_H - 1));
        g.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
        g.outline(markerX - 3, markerY - 3, 7, 7, 0xFF000000);

        int hx = x + svW + 4;
        for (int row = 0; row < SV_H; row++) {
            g.fill(hx, y + row, hx + HUE_W, y + row + 1, Mth.hsvToRgb((float) row / (SV_H - 1), 1f, 1f) | 0xFF000000);
        }
        int hueY = y + Math.round(hue * (SV_H - 1));
        g.fill(hx - 1, hueY - 1, hx + HUE_W + 1, hueY + 2, 0xFFFFFFFF);
        g.fill(hx, hueY, hx + HUE_W, hueY + 1, 0xFF000000);

        int cx = columnX();
        Draw.roundRect(g, cx - 1, y - 1, COLUMN_W + 2, 18, 4, Theme.PANEL_BORDER);
        Draw.roundRect(g, cx, y, COLUMN_W, 16, 3, 0xFF000000 | rgb);
        int fy = y + 20;
        boolean overHex = mouseX >= cx && mouseX < cx + COLUMN_W && mouseY >= fy && mouseY < fy + 16;
        Draw.panel(g, cx, fy, COLUMN_W, 16, 3, Theme.PANEL_LIGHT, editingHex ? PlusButton.GOLD : overHex ? Theme.ACCENT_MID : Theme.PANEL_BORDER);
        String text = editingHex ? "#" + hexBuffer + (Util.getMillis() / 500 % 2 == 0 ? "_" : "") : hex(rgb);
        g.text(font, text, cx + 5, fy + 4, editingHex ? Theme.TEXT : Theme.TEXT_DIM, false);

        for (int i = 0; i < NAMED.size(); i++) {
            swatch(g, x + i * (swatch + 2), swatchY, NAMED.get(i), selected, mouseX, mouseY);
        }
        List<Integer> recent = HinexFeaturesClient.state().recentColors();
        for (int i = 0; i < recent.size(); i++) {
            swatch(g, x + i * (swatch + 2), recentY, recent.get(i), selected, mouseX, mouseY);
        }
    }

    private void swatch(GuiGraphicsExtractor g, int sx, int sy, int color, int selected, int mouseX, int mouseY) {
        boolean over = mouseX >= sx && mouseX < sx + swatch && mouseY >= sy && mouseY < sy + swatch;
        int frame = color == selected ? PlusButton.GOLD : over ? 0xFFFFFFFF : Theme.PANEL_BORDER;
        g.fill(sx - 1, sy - 1, sx + swatch + 1, sy + swatch + 1, frame);
        g.fill(sx, sy, sx + swatch, sy + swatch, 0xFF000000 | color);
    }

    boolean mouseClicked(double mx, double my) {
        boolean wasEditing = editingHex;
        editingHex = false;
        int hx = x + svW + 4;
        int cx = columnX();
        if (mx >= x && mx < x + svW && my >= y && my < y + SV_H) {
            draggingSv = true;
            dragSv(mx, my);
            return true;
        }
        if (mx >= hx && mx < hx + HUE_W && my >= y && my < y + SV_H) {
            draggingHue = true;
            dragHue(my);
            return true;
        }
        if (mx >= cx && mx < cx + COLUMN_W && my >= y + 20 && my < y + 36) {
            editingHex = !wasEditing;
            hexBuffer = hex(rgb).substring(1);
            return true;
        }
        int index = (int) ((mx - x) / (swatch + 2));
        if (mx >= x && index >= 0) {
            if (my >= swatchY && my < swatchY + swatch && index < NAMED.size()) {
                pick(NAMED.get(index));
                return true;
            }
            List<Integer> recent = HinexFeaturesClient.state().recentColors();
            if (!recent.isEmpty() && my >= recentY && my < recentY + swatch && index < recent.size()) {
                pick(recent.get(index));
                return true;
            }
        }
        return false;
    }

    boolean mouseDragged(double mx, double my) {
        if (draggingSv) dragSv(mx, my);
        else if (draggingHue) dragHue(my);
        else return false;
        return true;
    }

    boolean mouseReleased() {
        if (!dragging()) return false;
        draggingSv = false;
        draggingHue = false;
        commit.accept(rgb);
        HinexFeaturesClient.state().useColor(rgb);
        return true;
    }

    private void dragSv(double mx, double my) {
        saturation = Mth.clamp((float) (mx - x) / (svW - 1), 0f, 1f);
        value = 1f - Mth.clamp((float) (my - y) / (SV_H - 1), 0f, 1f);
        rgb = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
        preview.accept(rgb);
    }

    private void dragHue(double my) {
        hue = Mth.clamp((float) (my - y) / (SV_H - 1), 0f, 0.999f);
        rgb = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
        preview.accept(rgb);
    }

    boolean editingHex() {
        return editingHex;
    }

    void stopEditing() {
        editingHex = false;
    }

    boolean charTyped(int codepoint) {
        if (!editingHex) return false;
        char c = Character.toLowerCase((char) codepoint);
        if (hexBuffer.length() < 6 && (c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) hexBuffer += c;
        return true;
    }

    boolean keyPressed(int key) {
        if (!editingHex) return false;
        switch (key) {
            case 42 -> {
                if (!hexBuffer.isEmpty()) hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
            }
            case 40, 88 -> {
                String full = hexBuffer.length() == 3
                        ? "" + hexBuffer.charAt(0) + hexBuffer.charAt(0) + hexBuffer.charAt(1) + hexBuffer.charAt(1) + hexBuffer.charAt(2) + hexBuffer.charAt(2)
                        : hexBuffer;
                editingHex = false;
                if (full.length() == 6) pick(Integer.parseInt(full, 16));
            }
            case 41 -> editingHex = false;
            default -> {
            }
        }
        return true;
    }
}
