package me.JAKOP.hinexFeatures.mod.dialog.editor;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class Slider extends AbstractSliderButton {

    private final double min;
    private final double max;
    private final double step;
    private final DoubleFunction<String> label;
    private final DoubleConsumer onChange;

    public Slider(int x, int y, int width, int height, double min, double max, double step, double initial,
                  DoubleFunction<String> label, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), (clamp(initial, min, max) - min) / (max - min));
        this.min = min;
        this.max = max;
        this.step = step;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public double current() {
        double raw = min + value * (max - min);
        return step > 0 ? Math.round(raw / step) * step : raw;
    }

    public void set(double newValue) {
        value = (clamp(newValue, min, max) - min) / (max - min);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label.apply(current())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(current());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = isHoveredOrFocused();
        Draw.panel(g, x, y, w, h, 3, Theme.BUTTON, hover ? Theme.ACCENT_MID : Theme.PANEL_BORDER);
        int fill = (int) Math.round((w - 2) * value);
        if (fill > 0) Draw.gradientRoundRect(g, x + 1, y + 1, fill, h - 2, 2, ARGB.color(0.55F, Theme.ACCENT_BLUE & 0xFFFFFF),
                ARGB.color(0.55F, Theme.ACCENT_PURPLE & 0xFFFFFF));
        int knob = x + 1 + fill;
        g.fill(Math.max(x + 1, knob - 1), y + 1, Math.min(x + w - 1, knob + 1), y + h - 1, 0xFFFFFFFF);
        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        g.text(font, message, x + (w - font.width(message)) / 2, y + (h - 8) / 2, Theme.TEXT, true);
    }
}
