package me.JAKOP.hinexFeatures.mod.admin;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

final class AdminHeader {

    static final int HEIGHT = 24;
    static final String TITLE = "ПАНЕЛЬ УПРАВЛЕНИЯ";

    private AdminHeader() {
    }

    static void draw(GuiGraphicsExtractor g, Font font, int width, boolean local, int mouseX, int mouseY, List<Object[]> clickables,
                     Runnable close) {
        Component title = Draw.gradientText(TITLE, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true);
        g.text(font, title, 8, 9, 0xFFFFFFFF, true);
        int x = 8 + font.width(title) + 12;
        String[] titles = {"Локально", "Глобально"};
        for (int i = 0; i < 2; i++) {
            boolean active = (i == 0) == local;
            int w = font.width(titles[i]) + 12;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= 4 && mouseY < 20;
            if (active) Draw.gradientRoundRect(g, x, 4, w, 16, 3, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
            else if (hover) Draw.roundRect(g, x, 4, w, 16, 3, Theme.BUTTON_HOVER);
            g.text(font, titles[i], x + 6, 8, active ? 0xFF0B0D1C : hover ? Theme.TEXT : Theme.TEXT_DIM, false);
            boolean toLocal = i == 0;
            if (!active) {
                clickables.add(new Object[]{x, 4, w, 16, (Runnable) () -> Minecraft.getInstance().gui.setScreen(
                        toLocal ? new MacroScreen() : new WorkspaceScreen())});
            }
            x += w + 4;
        }
        int closeX = width - 16;
        boolean hover = mouseX >= closeX - 3 && mouseX < closeX + 10 && mouseY >= 4 && mouseY < 20;
        g.text(font, "✕", closeX, 8, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
        clickables.add(new Object[]{closeX - 3, 4, 13, 16, close});
        g.fill(8, 22, width - 8, 23, 0x40FFFFFF);
    }

    static boolean click(List<Object[]> clickables, double mx, double my) {
        for (Object[] clickable : List.copyOf(clickables)) {
            int x = (int) clickable[0];
            int y = (int) clickable[1];
            if (mx >= x && mx < x + (int) clickable[2] && my >= y && my < y + (int) clickable[3]) {
                ((Runnable) clickable[4]).run();
                return true;
            }
        }
        return false;
    }

    static void background(Screen screen, GuiGraphicsExtractor g) {
        g.fillGradient(0, 0, screen.width, screen.height, Theme.SCRIM_TOP, Theme.SCRIM_BOTTOM);
    }
}
