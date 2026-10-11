package me.JAKOP.hinexFeatures.mod.dialog.editor;

import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

public final class ColorSwatch extends AbstractButton {

    private final IntSupplier color;
    private final Consumer<ColorSwatch> open;

    public ColorSwatch(int x, int y, int size, String tooltip, IntSupplier color, Consumer<ColorSwatch> open) {
        super(x, y, size, size, Component.literal(tooltip));
        this.color = color;
        this.open = open;
        setTooltip(Tooltip.create(Component.literal(tooltip)));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        open.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        int s = getWidth();
        g.fill(x, y, x + s, y + getHeight(), isHoveredOrFocused() ? 0xFFFFFFFF : Theme.PANEL_BORDER);
        int rgb = color.getAsInt();
        if (rgb < 0) {
            g.fill(x + 1, y + 1, x + s - 1, y + getHeight() - 1, 0xFF20233A);
            for (int i = 1; i < s - 1; i++) g.fill(x + i, y + getHeight() - 1 - i, x + i + 1, y + getHeight() - i, 0xFFFF5C7A);
        } else {
            g.fill(x + 1, y + 1, x + s - 1, y + getHeight() - 1, 0xFF000000 | rgb);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
