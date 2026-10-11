package me.JAKOP.hinexFeatures.mod.plus;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class PlusButton extends AbstractButton {

    static final int GOLD = 0xFFFFC857;
    static final int PINK = 0xFFFF7AC8;
    static final int SPARK = 0xFFFFF4D6;

    static final String[] ICON = {
            "       #        ",
            "       #     +  ",
            "       #    +++ ",
            "      ###    +  ",
            "      ###       ",
            "     ##+##      ",
            "   ###+++###    ",
            "#####+++++##### ",
            "   ###+++###    ",
            "     ##+##      ",
            "      ###       ",
            "      ###       ",
            "       #        ",
            "       #        ",
            "       #        ",
    };

    private final Screen parent;

    public PlusButton(int x, int y, Screen parent) {
        super(x, y, 20, 20, Component.literal("Plus"));
        this.parent = parent;
        setTooltip(Tooltip.create(Component.literal("Plus — цвет ника и суффикс в табе, эмодзи")));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        Minecraft.getInstance().gui.setScreen(new PlusScreen(parent));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        boolean hover = isHoveredOrFocused();
        g.fill(x, y, x + 20, y + 20, hover ? GOLD : 0xFF6B4A1E);
        g.fill(x + 1, y + 1, x + 19, y + 19, hover ? 0xFF2E2210 : 0xFF1C140A);
        Draw.icon(g, ICON, x + 2, y + 3, GOLD, SPARK);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
