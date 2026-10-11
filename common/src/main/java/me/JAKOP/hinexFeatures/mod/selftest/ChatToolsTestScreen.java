package me.JAKOP.hinexFeatures.mod.selftest;

import me.JAKOP.hinexFeatures.mod.chat.ChatTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class ChatToolsTestScreen extends Screen {

    private final String text;
    private EditBox input;
    private ChatTools tools;

    ChatToolsTestScreen(String text) {
        super(Component.literal("chat test"));
        this.text = text;
    }

    @Override
    protected void init() {
        input = new EditBox(font, 4, height - 12, width - 4, 12, Component.literal("chat"));
        input.setMaxLength(256);
        input.setBordered(false);
        input.setValue(text);
        input.setCanLoseFocus(false);
        addRenderableWidget(input);
        tools = ChatTools.attach(tools, input, width, height);
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(input);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(2, height - 14, width - 2, height - 2, 0x80000000);
        super.extractRenderState(g, mouseX, mouseY, a);
        if (tools != null) tools.extract(g, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tools != null && tools.mouseClicked(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }
}
