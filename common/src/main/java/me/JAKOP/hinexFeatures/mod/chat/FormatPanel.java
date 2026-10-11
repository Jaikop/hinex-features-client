package me.JAKOP.hinexFeatures.mod.chat;

import com.mojang.blaze3d.Blaze3D;
import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FormatPanel {

    private static final int W = 206;
    private static final int PAD = 8;
    private static final int SV_W = 104;
    private static final int SV_H = 60;
    private static final int HUE_W = 10;
    private static final int SWATCH = 10;

    private record Tool(int x, int y, int w, int h, Component label, Component tooltip, Runnable action) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    int x;
    int y;
    int w = W;
    int h;

    private final List<Tool> tools = new ArrayList<>();
    private final String overflow;
    private EditBox input;
    private Set<String> allowed = Set.of();

    private final int[] slots = {0x4FC3FF, 0xB46CFF};
    private int activeSlot;
    private float hue;
    private float saturation;
    private float value;
    private boolean draggingSv;
    private boolean draggingHue;

    private boolean hasPalette;
    private int paletteY;
    private int swatchY;
    private int recentY;
    private int footerY;

    private boolean editingHex;
    private String hexBuffer = "";

    private String message;
    private long messageUntil;

    FormatPanel() {
        this("Не хватает места в сообщении");
    }

    public FormatPanel(String overflow) {
        this.overflow = overflow;
        loadSlot(0);
    }

    void layout(ServerState state, EditBox input, int bottom) {
        layout(state.formatTags(), input, 2, bottom, bottom);
    }

    public void layout(Set<String> allowed, EditBox input, int anchorX, int bottom, int below) {
        this.allowed = allowed;
        this.input = input;
        tools.clear();
        Font font = Minecraft.getInstance().font;

        int left = PAD;
        int cursorY = 20;

        if (allowed.contains("decorations") || allowed.contains("reset")) {
            int bx = left;
            if (allowed.contains("decorations")) {
                bx = styleButton(bx, cursorY, Component.literal("B").withStyle(Style.EMPTY.withBold(true)), "Жирный", "b");
                bx = styleButton(bx, cursorY, Component.literal("I").withStyle(Style.EMPTY.withItalic(true)), "Курсив", "i");
                bx = styleButton(bx, cursorY, Component.literal("U").withStyle(Style.EMPTY.withUnderlined(true)), "Подчёркнутый", "u");
                bx = styleButton(bx, cursorY, Component.literal("S").withStyle(Style.EMPTY.withStrikethrough(true)), "Зачёркнутый", "st");
                bx = styleButton(bx, cursorY, Component.literal("O").withStyle(Style.EMPTY.withObfuscated(true)), "Мерцающий (obfuscated)", "obf");
            }
            if (allowed.contains("reset")) {
                Component label = Component.literal("Сброс");
                tools.add(new Tool(bx, cursorY, Math.max(font.width(label) + 12, W - PAD - bx), 16, label,
                        Component.literal("Сбросить всё форматирование дальше по тексту  <reset>"),
                        () -> result(ChatInsert.insert(input, "<reset>"))));
            }
            cursorY += 20;
        }

        List<Object[]> effects = new ArrayList<>();
        effects.add(new Object[]{Draw.gradientText("Документация ↗", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, false),
                "Все теги MiniMessage (откроется в браузере)", (Runnable) FormatPanel::openDocs});
        if (allowed.contains("gradient")) effects.add(new Object[]{Draw.gradientText("Градиент", 0xFF000000 | slots[0], 0xFF000000 | slots[1], false),
                "Плавный переход от цвета A к цвету B", (Runnable) () -> apply("<gradient:" + hex(slots[0]) + ":" + hex(slots[1]) + ">", "</gradient>", true)});
        if (allowed.contains("transition")) effects.add(new Object[]{Component.literal("Смесь"),
                "Цвет посередине между A и B", (Runnable) () -> apply("<transition:" + hex(slots[0]) + ":" + hex(slots[1]) + ":0.5>", "</transition>", true)});
        if (allowed.contains("shadow")) effects.add(new Object[]{Component.literal("Тень"),
                "Тень текста цвета A", (Runnable) () -> apply("<shadow:" + hex(slots[0]) + ":0.6>", "</shadow>", true)});
        if (allowed.contains("pride")) effects.add(new Object[]{Draw.gradientText("Прайд", 0xFFE50000, 0xFF770088, false),
                "Цвета радужного флага", (Runnable) () -> apply("<pride>", "</pride>", false)});
        List<Object[]> row = new ArrayList<>();
        int rowWidth = 0;
        for (Object[] effect : effects) {
            int bw = font.width((Component) effect[0]) + 12;
            if (!row.isEmpty() && rowWidth + 3 + bw > W - 2 * PAD) {
                effectRow(row, rowWidth, cursorY);
                row.clear();
                rowWidth = 0;
                cursorY += 20;
            }
            rowWidth += (row.isEmpty() ? 0 : 3) + bw;
            row.add(effect);
        }
        if (!row.isEmpty()) {
            effectRow(row, rowWidth, cursorY);
            cursorY += 20;
        }

        hasPalette = allowed.contains("color") || allowed.contains("gradient") || allowed.contains("transition") || allowed.contains("shadow");
        if (hasPalette) {
            paletteY = cursorY + 2;
            int columnX = left + SV_W + 4 + HUE_W + 8;
            int columnW = W - PAD - columnX;
            if (allowed.contains("color")) {
                tools.add(new Tool(columnX, paletteY + 42, columnW, 16, Component.literal("Покрасить"),
                        Component.literal("Покрасить текст цветом A"), this::applyColor));
            }
            swatchY = paletteY + SV_H + 6;
            recentY = swatchY + SWATCH + 4;
            cursorY = HinexFeaturesClient.state().recentColors().isEmpty() ? swatchY + SWATCH + 6 : recentY + SWATCH + 6;
        }

        footerY = cursorY + 2;
        h = footerY + 15;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        x = Math.max(2, Math.min(anchorX, screenWidth - w - 2));
        y = bottom - h >= 2 || below + h > screenHeight - 2 ? Math.max(2, bottom - h) : below;
    }

    private void effectRow(List<Object[]> row, int rowWidth, int rowY) {
        Font font = Minecraft.getInstance().font;
        int extra = W - 2 * PAD - rowWidth;
        int ex = PAD;
        for (int i = 0; i < row.size(); i++) {
            Object[] effect = row.get(i);
            Component label = (Component) effect[0];
            int share = extra / row.size() + (i < extra % row.size() ? 1 : 0);
            int bw = font.width(label) + 12 + share;
            tools.add(new Tool(ex, rowY, bw, 16, label, Component.literal((String) effect[1]), (Runnable) effect[2]));
            ex += bw + 3;
        }
    }

    private int styleButton(int bx, int by, Component label, String tooltip, String tag) {
        tools.add(new Tool(bx, by, 22, 16, label, Component.literal(tooltip + "  <" + tag + ">"),
                () -> apply("<" + tag + ">", "</" + tag + ">", false)));
        return bx + 25;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void apply(String open, String close, boolean usesColors) {
        if (result(ChatInsert.wrap(input, open, close)) && usesColors) {
            HinexFeaturesClient.state().useColor(slots[0]);
            if (open.contains(hex(slots[1]))) HinexFeaturesClient.state().useColor(slots[1]);
        }
    }

    private void applyColor() {
        int rgb = slots[0];
        String name = null;
        for (Map.Entry<String, Integer> named : MiniPreview.NAMED.entrySet()) {
            if (named.getValue() == rgb) name = named.getKey();
        }
        String tag = name != null ? name : hex(rgb);
        if (result(ChatInsert.wrap(input, "<" + tag + ">", "</" + tag + ">"))) {
            HinexFeaturesClient.state().useColor(rgb);
        }
    }

    private static void openDocs() {
        try {
            Blaze3D.openUri(URI.create(HinexConstants.MINIMESSAGE_DOCS_URL));
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Не удалось открыть документацию MiniMessage", e);
        }
    }

    private boolean result(boolean ok) {
        if (!ok) {
            message = overflow;
            messageUntil = Util.getMillis() + 2500;
        }
        return ok;
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private void loadSlot(int slot) {
        activeSlot = slot;
        int rgb = slots[slot];
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        value = max;
        saturation = max == 0 ? 0 : delta / max;
        if (delta == 0) {
            return;
        }
        float h;
        if (max == r) h = ((g - b) / delta) % 6;
        else if (max == g) h = (b - r) / delta + 2;
        else h = (r - g) / delta + 4;
        hue = ((h / 6f) % 1f + 1f) % 1f;
    }

    private void setSlotColor(int rgb) {
        slots[activeSlot] = rgb & 0xFFFFFF;
        loadSlot(activeSlot);
    }

    private void updateFromHsv() {
        slots[activeSlot] = Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
    }

    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Draw.roundRect(g, x - 1, y + 1, w + 2, h + 1, 6, 0x50000000);
        Draw.accentPanel(g, x, y, w, h, 5, Theme.POPUP);
        g.text(font, Draw.gradientText("Форматирование", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true), x + PAD, y + 7, 0xFFFFFFFF, false);

        Tool hoveredTool = null;
        for (Tool tool : tools) {
            boolean over = tool.contains(mouseX - x, mouseY - y);
            if (over) hoveredTool = tool;
            int tx = x + tool.x();
            int ty = y + tool.y();
            Draw.panel(g, tx, ty, tool.w(), tool.h(), 3, over ? Theme.BUTTON_HOVER : Theme.BUTTON, over ? Theme.ACCENT_MID : Theme.PANEL_BORDER);
            g.text(font, tool.label(), tx + (tool.w() - font.width(tool.label()) + 1) / 2, ty + 4, Theme.TEXT, false);
        }

        if (hasPalette) extractPalette(g, font, mouseX, mouseY);

        g.fill(x + 6, y + footerY - 3, x + w - 6, y + footerY - 2, Theme.PANEL_BORDER);
        if (message != null && Util.getMillis() < messageUntil) {
            g.text(font, message, x + PAD, y + footerY + 1, Theme.DANGER, false);
        } else {
            g.text(font, "Применяется к выделенному", x + PAD, y + footerY + 1, Theme.TEXT_MUTED, false);
        }

        if (hoveredTool != null) g.setTooltipForNextFrame(font, hoveredTool.tooltip(), mouseX, mouseY);
    }

    private void extractPalette(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        int sx = x + PAD;
        int sy = y + paletteY;

        for (int col = 0; col < SV_W; col++) {
            int top = Mth.hsvToRgb(hue, (float) col / (SV_W - 1), 1f) | 0xFF000000;
            g.fillGradient(sx + col, sy, sx + col + 1, sy + SV_H, top, 0xFF000000);
        }
        int markerX = sx + Math.round(saturation * (SV_W - 1));
        int markerY = sy + Math.round((1 - value) * (SV_H - 1));
        g.outline(markerX - 2, markerY - 2, 5, 5, 0xFFFFFFFF);
        g.outline(markerX - 3, markerY - 3, 7, 7, 0xFF000000);

        int hx = sx + SV_W + 4;
        for (int row = 0; row < SV_H; row++) {
            g.fill(hx, sy + row, hx + HUE_W, sy + row + 1, Mth.hsvToRgb((float) row / (SV_H - 1), 1f, 1f) | 0xFF000000);
        }
        int hueY = sy + Math.round(hue * (SV_H - 1));
        g.fill(hx - 1, hueY - 1, hx + HUE_W + 1, hueY + 2, 0xFFFFFFFF);
        g.fill(hx, hueY, hx + HUE_W, hueY + 1, 0xFF000000);

        int cx = hx + HUE_W + 8;
        boolean twoSlots = allowed.contains("gradient") || allowed.contains("transition");
        slot(g, font, cx, sy, "A", 0, mouseX, mouseY);
        if (twoSlots) slot(g, font, cx + 24, sy, "B", 1, mouseX, mouseY);

        int fieldW = w - PAD - (cx - x);
        int fy = sy + 22;
        boolean overHex = mouseX >= cx && mouseX < cx + fieldW && mouseY >= fy && mouseY < fy + 16;
        Draw.panel(g, cx, fy, fieldW, 16, 3, Theme.PANEL_LIGHT, editingHex ? Theme.ACCENT_BLUE : overHex ? Theme.ACCENT_MID : Theme.PANEL_BORDER);
        String text = editingHex ? "#" + hexBuffer + (Util.getMillis() / 500 % 2 == 0 ? "_" : "") : hex(slots[activeSlot]);
        g.text(font, text, cx + 5, fy + 4, editingHex ? Theme.TEXT : Theme.TEXT_DIM, false);

        int index = 0;
        for (Map.Entry<String, Integer> named : MiniPreview.NAMED.entrySet()) {
            int wx = x + PAD + index * (SWATCH + 2);
            swatch(g, wx, y + swatchY, named.getValue(), mouseX, mouseY);
            if (mouseX >= wx && mouseX < wx + SWATCH && mouseY >= y + swatchY && mouseY < y + swatchY + SWATCH) {
                g.setTooltipForNextFrame(font, Component.literal("<" + named.getKey() + ">").withColor(named.getValue()), mouseX, mouseY);
            }
            index++;
        }

        List<Integer> recent = HinexFeaturesClient.state().recentColors();
        if (!recent.isEmpty()) {
            g.text(font, "Недавние", x + PAD, y + recentY + 1, Theme.TEXT_MUTED, false);
            int rx = x + PAD + font.width("Недавние") + 6;
            for (int i = 0; i < recent.size(); i++) {
                swatch(g, rx + i * (SWATCH + 2), y + recentY, recent.get(i), mouseX, mouseY);
            }
        }
    }

    private void slot(GuiGraphicsExtractor g, Font font, int sx, int sy, String label, int slot, int mouseX, int mouseY) {
        boolean active = activeSlot == slot;
        if (active) Draw.gradientRoundRect(g, sx - 1, sy - 1, 22, 18, 4, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        else Draw.roundRect(g, sx - 1, sy - 1, 22, 18, 4, Theme.PANEL_BORDER);
        Draw.roundRect(g, sx, sy, 20, 16, 3, 0xFF000000 | slots[slot]);
        float luminance = (0.299f * ((slots[slot] >> 16) & 0xFF) + 0.587f * ((slots[slot] >> 8) & 0xFF) + 0.114f * (slots[slot] & 0xFF)) / 255f;
        g.text(font, label, sx + 7, sy + 4, luminance > 0.6f ? 0xFF000000 : 0xFFFFFFFF, false);
        if (mouseX >= sx && mouseX < sx + 20 && mouseY >= sy && mouseY < sy + 16) {
            g.setTooltipForNextFrame(font, Component.literal(slot == 0 ? "Цвет A — основной" : "Цвет B — второй цвет градиента"), mouseX, mouseY);
        }
    }

    private static void swatch(GuiGraphicsExtractor g, int sx, int sy, int rgb, int mouseX, int mouseY) {
        boolean over = mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
        g.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, over ? 0xFFFFFFFF : 0xFF2A2E4A);
        g.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | rgb);
    }

    public boolean mouseClicked(double mx, double my) {
        if (!contains(mx, my)) {
            editingHex = false;
            return false;
        }
        double lx = mx - x;
        double ly = my - y;
        editingHex = false;

        for (Tool tool : tools) {
            if (tool.contains(lx, ly)) {
                tool.action().run();
                return true;
            }
        }
        if (!hasPalette) return true;

        int sx = PAD;
        int hx = sx + SV_W + 4;
        int cx = hx + HUE_W + 8;
        if (lx >= sx && lx < sx + SV_W && ly >= paletteY && ly < paletteY + SV_H) {
            draggingSv = true;
            dragSv(mx, my);
        } else if (lx >= hx && lx < hx + HUE_W && ly >= paletteY && ly < paletteY + SV_H) {
            draggingHue = true;
            dragHue(my);
        } else if (ly >= paletteY && ly < paletteY + 16 && lx >= cx && lx < cx + 20) {
            loadSlot(0);
        } else if (ly >= paletteY && ly < paletteY + 16 && lx >= cx + 24 && lx < cx + 44
                && (allowed.contains("gradient") || allowed.contains("transition"))) {
            loadSlot(1);
        } else if (ly >= paletteY + 22 && ly < paletteY + 38 && lx >= cx) {
            editingHex = true;
            hexBuffer = hex(slots[activeSlot]).substring(1);
        } else if (ly >= swatchY && ly < swatchY + SWATCH) {
            int index = (int) ((lx - PAD) / (SWATCH + 2));
            if (lx >= PAD && index < MiniPreview.NAMED.size()) {
                setSlotColor(new ArrayList<>(MiniPreview.NAMED.values()).get(index));
            }
        } else if (ly >= recentY && ly < recentY + SWATCH) {
            List<Integer> recent = HinexFeaturesClient.state().recentColors();
            int start = PAD + Minecraft.getInstance().font.width("Недавние") + 6;
            int index = (int) ((lx - start) / (SWATCH + 2));
            if (lx >= start && index < recent.size()) setSlotColor(recent.get(index));
        }
        return true;
    }

    public boolean mouseDragged(double mx, double my) {
        if (draggingSv) {
            dragSv(mx, my);
            return true;
        }
        if (draggingHue) {
            dragHue(my);
            return true;
        }
        return false;
    }

    public boolean mouseReleased() {
        boolean was = draggingSv || draggingHue;
        draggingSv = false;
        draggingHue = false;
        return was;
    }

    private void dragSv(double mx, double my) {
        saturation = Mth.clamp((float) (mx - x - PAD) / (SV_W - 1), 0f, 1f);
        value = 1f - Mth.clamp((float) (my - y - paletteY) / (SV_H - 1), 0f, 1f);
        updateFromHsv();
    }

    private void dragHue(double my) {
        hue = Mth.clamp((float) (my - y - paletteY) / (SV_H - 1), 0f, 0.999f);
        updateFromHsv();
    }

    boolean isEditingHex() {
        return editingHex;
    }

    public boolean charTyped(int codepoint) {
        if (!editingHex) return false;
        char c = Character.toLowerCase((char) codepoint);
        if (hexBuffer.length() < 6 && (c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) {
            hexBuffer += c;
        }
        return true;
    }

    public boolean keyPressed(int key) {
        if (!editingHex) return false;
        switch (key) {
            case 42 -> {
                if (!hexBuffer.isEmpty()) hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
            }
            case 40, 88 -> {
                String full = hexBuffer.length() == 3
                        ? "" + hexBuffer.charAt(0) + hexBuffer.charAt(0) + hexBuffer.charAt(1) + hexBuffer.charAt(1) + hexBuffer.charAt(2) + hexBuffer.charAt(2)
                        : hexBuffer;
                if (full.length() == 6) setSlotColor(Integer.parseInt(full, 16));
                editingHex = false;
            }
            case 41 -> editingHex = false;
            default -> {
            }
        }
        return true;
    }

    public int primaryColor() {
        return slots[0];
    }
}
