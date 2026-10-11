package me.JAKOP.hinexFeatures.mod.admin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class AdminButton extends AbstractButton {

    private static final int OUTLINE = 0xFF1E140A;
    private static final int BODY = 0xFFC8742C;
    private static final int DARK = 0xFF8E4E1A;
    private static final int LIGHT = 0xFFE89A50;
    private static final int SCREEN = 0xFF1A1D2E;
    private static final int TEXT = 0xFF7CFF8C;

    public AdminButton(int x, int y) {
        super(x, y, 20, 20, Component.literal("Панель управления"));
        HotKey key = MacroStore.panelKey();
        setTooltip(Tooltip.create(Component.literal("Панель управления" + (key != null ? " (" + key.display() + ")" : ""))));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        Minecraft.getInstance().gui.setScreen(new MacroScreen());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        boolean hover = isHoveredOrFocused();
        g.fill(x, y, x + 20, y + 20, hover ? 0xFF8FA8FF : 0xFF3A4270);
        g.fill(x + 1, y + 1, x + 19, y + 19, hover ? 0xFF222844 : 0xFF141830);
        drawIcon(g, x + 2, y + 2);
    }

    static void drawIcon(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y + 1, x + 15, y + 15, OUTLINE);
        g.fill(x + 2, y + 2, x + 14, y + 14, BODY);
        g.fill(x + 2, y + 2, x + 14, y + 3, LIGHT);
        g.fill(x + 2, y + 13, x + 14, y + 14, DARK);
        g.fill(x + 4, y + 4, x + 12, y + 11, OUTLINE);
        g.fill(x + 5, y + 5, x + 11, y + 10, SCREEN);
        g.fill(x + 6, y + 6, x + 7, y + 7, TEXT);
        g.fill(x + 7, y + 7, x + 8, y + 8, TEXT);
        g.fill(x + 6, y + 8, x + 7, y + 9, TEXT);
        g.fill(x + 8, y + 8, x + 10, y + 9, TEXT);
        g.fill(x + 7, y + 11, x + 9, y + 12, DARK);
        g.fill(x + 6, y + 12, x + 10, y + 13, DARK);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
