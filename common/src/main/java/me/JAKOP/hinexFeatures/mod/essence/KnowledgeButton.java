package me.JAKOP.hinexFeatures.mod.essence;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class KnowledgeButton extends AbstractButton {

    static final String[] CROWN = {
            "      ####      ",
            "    ########    ",
            "   ####+#####   ",
            "  ############  ",
            "  ##+#######+#  ",
            "  ############  ",
            "   #####+####   ",
            "    ########    ",
            "      ####      ",
    };
    static final String[] TRUNK = {
            "",
            "", "", "", "", "", "", "", "",
            "       ##       ",
            "       ##       ",
            "      ####      ",
            "     ##  ##     ",
    };
    static final int LEAVES = 0xFF8E6BD8;
    static final int WOOD = 0xFF7A5230;

    private final Screen parent;

    public KnowledgeButton(int x, int y, Screen parent) {
        super(x, y, 20, 20, Component.literal("Древо знаний"));
        this.parent = parent;
        setTooltip(Tooltip.create(Component.literal("Древо знаний · " + EssenceClient.essence() + " ✦")));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        EssenceClient.open(parent);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX();
        int y = getY();
        boolean hover = isHoveredOrFocused();
        g.fill(x, y, x + 20, y + 20, hover ? EssenceHud.ESSENCE : 0xFF3E2F5C);
        g.fill(x + 1, y + 1, x + 19, y + 19, hover ? 0xFF221A33 : 0xFF151020);
        drawIcon(g, x + 2, y + 3);
    }

    static void drawIcon(GuiGraphicsExtractor g, int x, int y) {
        Draw.icon(g, TRUNK, x, y, WOOD, 0);
        Draw.icon(g, CROWN, x, y, LEAVES, EssenceHud.ESSENCE);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
