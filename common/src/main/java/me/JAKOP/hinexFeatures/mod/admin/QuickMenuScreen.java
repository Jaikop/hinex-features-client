package me.JAKOP.hinexFeatures.mod.admin;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.List;

public final class QuickMenuScreen extends Screen {

    private final HotKey key;
    private final List<Macro> macros;
    private int hovered = -1;
    private float opened;

    public QuickMenuScreen(HotKey key) {
        super(Component.literal("Быстрое меню"));
        this.key = key;
        this.macros = MacroStore.quickMacros();
    }

    private int radius() {
        return Math.min(90, Math.min(width, height) / 3);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x90000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        opened = Math.min(1.0F, opened + 0.2F);
        int cx = width / 2;
        int cy = height / 2;
        int radius = Math.round(radius() * (0.8F + 0.2F * opened));
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        hovered = -1;
        if (dx * dx + dy * dy > 18 * 18 && !macros.isEmpty()) {
            double angle = Math.atan2(dy, dx) + Math.PI / 2;
            if (angle < 0) angle += Math.PI * 2;
            hovered = (int) Math.round(angle / (Math.PI * 2) * macros.size()) % macros.size();
        }
        Draw.roundRect(g, cx - 16, cy - 16, 32, 32, 16, 0xC0101224);
        g.centeredText(font, hovered >= 0 ? "▶" : "✕", cx, cy - 4, hovered >= 0 ? Theme.ACCENT_BLUE : Theme.TEXT_MUTED);
        for (int i = 0; i < macros.size(); i++) {
            double angle = i / (double) macros.size() * Math.PI * 2 - Math.PI / 2;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            String name = font.plainSubstrByWidth(macros.get(i).name, 150);
            int w = Math.max(40, font.width(name) + 14);
            boolean active = i == hovered;
            int alpha = Math.round(255 * opened);
            if (active) {
                Draw.gradientRoundRect(g, x - w / 2 - 1, y - 10, w + 2, 20, 5, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
                Draw.roundRect(g, x - w / 2 + 1, y - 8, w - 2, 16, 4, 0xF0141830);
            } else {
                Draw.roundRect(g, x - w / 2, y - 9, w, 18, 5, ARGB.color(alpha * 3 / 4, 0x141830));
            }
            g.centeredText(font, name, x, y - 4, active ? 0xFFFFFFFF : Theme.TEXT_DIM);
            if (MacroRunner.running(macros.get(i))) g.text(font, "●", x + w / 2 - 8, y - 12, Theme.ACCENT_BLUE, false);
        }
        String hint = "Отпустите " + key.display() + " на макросе";
        g.centeredText(font, hint, cx, cy + radius + 18, Theme.TEXT_MUTED);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == key.key()) {
            choose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        choose();
        return true;
    }

    private void choose() {
        Macro macro = hovered >= 0 && hovered < macros.size() ? macros.get(hovered) : null;
        onClose();
        if (macro != null) {
            if (MacroRunner.running(macro)) MacroRunner.stop(macro);
            else MacroRunner.start(macro);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
