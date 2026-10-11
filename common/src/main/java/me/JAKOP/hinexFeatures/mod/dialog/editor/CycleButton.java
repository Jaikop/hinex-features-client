package me.JAKOP.hinexFeatures.mod.dialog.editor;

import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

public final class CycleButton extends HinexButton {

    private static final int LEFT = 1;
    private static final int RIGHT = 3;

    private final List<String> values;
    private final List<String> labels;
    private final Consumer<String> onChange;
    private int index;

    public CycleButton(int x, int y, int width, int height, List<String> values, List<String> labels, String current,
                       Consumer<String> onChange) {
        super(x, y, width, height, Component.empty(), Kind.SECONDARY, button -> {
        });
        this.values = values;
        this.labels = labels;
        this.onChange = onChange;
        set(current);
    }

    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == LEFT || info.button() == RIGHT;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        step(event.button() == RIGHT ? -1 : 1);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        step(1);
    }

    private void step(int direction) {
        index = Math.floorMod(index + direction, values.size());
        setMessage(Component.literal(labels.get(index)));
        onChange.accept(values.get(index));
    }

    public void set(String value) {
        index = Math.max(0, values.indexOf(value));
        setMessage(Component.literal(labels.get(index)));
    }
}
