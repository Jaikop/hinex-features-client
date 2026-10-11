package me.JAKOP.hinexFeatures.mod.dialog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class HistoryButton extends AbstractButton {

    private final Screen parent;

    public HistoryButton(int x, int y, Screen parent) {
        super(x, y, 20, 20, Component.literal("История диалогов"));
        this.parent = parent;
        setTooltip(Tooltip.create(Component.literal("История диалогов")));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        Minecraft.getInstance().gui.setScreen(new HistoryScreen(parent));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        boolean hover = isHoveredOrFocused();
        g.fill(x, y, x + 20, y + 20, hover ? 0xFFD4A25A : 0xFF5E432A);
        g.fill(x + 1, y + 1, x + 19, y + 19, hover ? 0xFF3A2918 : 0xFF241810);
        HistoryIcon.draw(g, x + 2, y + 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
