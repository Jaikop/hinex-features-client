package me.JAKOP.hinexFeatures.mod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.function.Consumer;

public class HinexCheckbox extends AbstractButton {

    private static final String[] CHECK = {
            "       #",
            "      ##",
            "#    ## ",
            "##  ##  ",
            " ####   ",
            "  ##    ",
    };

    private final Consumer<Boolean> onChange;
    private boolean checked;
    private float hover;

    public HinexCheckbox(int x, int y, int width, Component message, Consumer<Boolean> onChange) {
        super(x, y, width, 14, message);
        this.onChange = onChange;
    }

    public boolean checked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        checked = !checked;
        onChange.accept(checked);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        hover += ((isHoveredOrFocused() ? 1f : 0f) - hover) * 0.35f;
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();

        if (checked) {
            Draw.gradientRoundRect(g, x, y, 14, 14, 3, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
            Draw.icon(g, CHECK, x + 3, y + 4, 0xFF0B0D1C, 0);
        } else {
            Draw.panel(g, x, y, 14, 14, 3, Theme.BUTTON, ARGB.srgbLerp(hover, Theme.PANEL_BORDER, Theme.ACCENT_MID));
        }
        g.text(font, getMessage(), x + 20, y + 3, ARGB.srgbLerp(hover, Theme.TEXT_DIM, Theme.TEXT), false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
