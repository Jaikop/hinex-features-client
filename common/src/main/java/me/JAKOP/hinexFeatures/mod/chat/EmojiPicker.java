package me.JAKOP.hinexFeatures.mod.chat;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class EmojiPicker {

    private static final int COLS = 9;
    private static final int CELL = 18;
    private static final int MAX_VISIBLE_ROWS = 5;

    int x;
    int y;
    int w;
    int h;

    private int gridY;
    private int recentY;
    private int visibleRows;
    private int scroll;
    private List<ServerState.Emoji> emojis = List.of();
    private List<ServerState.Emoji> recent = List.of();
    private ServerState.Emoji hovered;

    void layout(ServerState state, int bottom) {
        layout(state.emojis(), 2, bottom, bottom);
    }

    public void layout(List<ServerState.Emoji> all, int left, int bottom, int below) {
        emojis = all;
        List<ServerState.Emoji> recentList = new ArrayList<>();
        for (String symbol : HinexFeaturesClient.state().recentEmojis()) {
            for (ServerState.Emoji emoji : emojis) {
                if (emoji.symbol().equals(symbol)) recentList.add(emoji);
            }
        }
        recent = recentList.subList(0, Math.min(COLS, recentList.size()));

        int rows = Math.max(1, Mth.positiveCeilDiv(emojis.size(), COLS));
        visibleRows = Math.min(MAX_VISIBLE_ROWS, rows);
        scroll = Mth.clamp(scroll, 0, rows - visibleRows);

        w = COLS * CELL + 14;
        int recentHeight = recent.isEmpty() ? 0 : 11 + CELL + 5;
        h = 20 + recentHeight + 11 + visibleRows * CELL + 19;
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        x = Math.max(2, Math.min(left, screenWidth - w - 2));
        y = bottom - h >= 2 || below + h > screenHeight - 2 ? Math.max(2, bottom - h) : below;
        recentY = y + 20;
        gridY = y + 20 + recentHeight + 11;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        Draw.roundRect(g, x - 1, y + 1, w + 2, h + 1, 6, 0x50000000);
        Draw.accentPanel(g, x, y, w, h, 5, Theme.POPUP);
        g.text(font, Draw.gradientText("Эмодзи", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true), x + 7, y + 7, 0xFFFFFFFF, false);
        String count = String.valueOf(emojis.size());
        g.text(font, count, x + w - 6 - font.width(count), y + 7, Theme.TEXT_MUTED, false);

        hovered = null;
        if (!recent.isEmpty()) {
            g.text(font, "Недавние", x + 7, recentY, Theme.TEXT_MUTED, false);
            for (int i = 0; i < recent.size(); i++) {
                cell(g, font, recent.get(i), x + 7 + i * CELL, recentY + 11, mouseX, mouseY);
            }
        }

        g.text(font, "Все", x + 7, gridY - 11, Theme.TEXT_MUTED, false);
        if (emojis.isEmpty()) {
            g.text(font, "На сервере нет эмодзи", x + 7, gridY + 4, Theme.TEXT_DIM, false);
        }
        g.enableScissor(x + 1, gridY, x + w - 1, gridY + visibleRows * CELL);
        for (int i = scroll * COLS; i < Math.min(emojis.size(), (scroll + visibleRows) * COLS); i++) {
            int row = i / COLS - scroll;
            int col = i % COLS;
            cell(g, font, emojis.get(i), x + 7 + col * CELL, gridY + row * CELL, mouseX, mouseY);
        }
        g.disableScissor();

        int rows = Mth.positiveCeilDiv(emojis.size(), COLS);
        if (rows > visibleRows) {
            int trackH = visibleRows * CELL;
            int thumbH = Math.max(8, trackH * visibleRows / rows);
            int thumbY = gridY + (trackH - thumbH) * scroll / (rows - visibleRows);
            g.fill(x + w - 5, gridY, x + w - 3, gridY + trackH, 0x30FFFFFF);
            g.fillGradient(x + w - 5, thumbY, x + w - 3, thumbY + thumbH, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        }

        int footerY = y + h - 14;
        g.fill(x + 6, footerY - 3, x + w - 6, footerY - 2, Theme.PANEL_BORDER);
        if (hovered != null) {
            g.text(font, ":" + hovered.name() + ":", x + 7, footerY, Theme.ACCENT_BLUE, false);
        } else {
            g.text(font, "Shift+клик — несколько", x + 7, footerY, Theme.TEXT_MUTED, false);
        }
    }

    private void cell(GuiGraphicsExtractor g, Font font, ServerState.Emoji emoji, int cx, int cy, int mouseX, int mouseY) {
        boolean over = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
        if (over) {
            hovered = emoji;
            Draw.roundRect(g, cx, cy, CELL, CELL, 3, 0x40818FFF);
        }
        g.text(font, emoji.symbol(), cx + (CELL - font.width(emoji.symbol()) + 1) / 2, cy + 5, 0xFFFFFFFF, false);
    }

    public boolean mouseClicked(double mx, double my, boolean shift, EditBox input) {
        if (!contains(mx, my)) return false;
        ServerState.Emoji emoji = emojiAt(mx, my);
        if (emoji != null && ChatInsert.insert(input, emoji.symbol())) {
            HinexFeaturesClient.state().useEmoji(emoji.symbol());
            return !shift;
        }
        return false;
    }

    private ServerState.Emoji emojiAt(double mx, double my) {
        int col = (int) ((mx - x - 7) / CELL);
        if (mx < x + 7 || col < 0 || col >= COLS) return null;
        if (!recent.isEmpty() && my >= recentY + 11 && my < recentY + 11 + CELL) {
            return col < recent.size() ? recent.get(col) : null;
        }
        if (my >= gridY && my < gridY + visibleRows * CELL) {
            int index = ((int) ((my - gridY) / CELL) + scroll) * COLS + col;
            return index < emojis.size() ? emojis.get(index) : null;
        }
        return null;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!contains(mx, my)) return false;
        int rows = Mth.positiveCeilDiv(emojis.size(), COLS);
        scroll = Mth.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, rows - visibleRows));
        return true;
    }
}
