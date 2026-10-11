package me.JAKOP.hinexFeatures.mod.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.function.Consumer;

public class HinexButton extends AbstractButton {

    public enum Kind { PRIMARY, SECONDARY, LINK }

    private final Kind kind;
    private final Consumer<HinexButton> onPress;
    private float hover;

    public HinexButton(int x, int y, int width, int height, Component message, Kind kind, Consumer<HinexButton> onPress) {
        super(x, y, width, height, message);
        this.kind = kind;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        hover += ((isHoveredOrFocused() && active ? 1f : 0f) - hover) * 0.35f;
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        switch (kind) {
            case PRIMARY -> {
                if (active) {
                    int left = ARGB.srgbLerp(hover * 0.35f, Theme.ACCENT_BLUE, 0xFFFFFFFF);
                    int right = ARGB.srgbLerp(hover * 0.35f, Theme.ACCENT_PURPLE, 0xFFFFFFFF);
                    Draw.gradientRoundRect(g, x, y, w, h, 4, left, right);
                } else {
                    Draw.panel(g, x, y, w, h, 4, Theme.BUTTON_DISABLED, Theme.PANEL_BORDER);
                }
                int color = active ? 0xFF0B0D1C : Theme.TEXT_MUTED;
                g.text(font, getMessage(), x + (w - font.width(getMessage())) / 2, y + (h - 8) / 2, color, false);
            }
            case SECONDARY -> {
                int fill = active ? ARGB.srgbLerp(hover, Theme.BUTTON, Theme.BUTTON_HOVER) : Theme.BUTTON_DISABLED;
                int border = active ? ARGB.srgbLerp(hover, Theme.PANEL_BORDER, Theme.ACCENT_MID) : Theme.PANEL_BORDER;
                Draw.panel(g, x, y, w, h, 4, fill, border);
                int color = active ? Theme.TEXT : Theme.TEXT_MUTED;
                g.text(font, getMessage(), x + (w - font.width(getMessage())) / 2, y + (h - 8) / 2, color, false);
            }
            case LINK -> {
                int color = ARGB.srgbLerp(hover, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
                int textX = x + (w - font.width(getMessage())) / 2;
                int textY = y + (h - 8) / 2;
                g.text(font, getMessage(), textX, textY, color, false);
                g.fill(textX, textY + 9, textX + font.width(getMessage()), textY + 10, Draw.withAlpha(color, 0.4f + hover * 0.6f));
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
