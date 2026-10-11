package me.JAKOP.hinexFeatures.mod.mixin.chat;

import me.JAKOP.hinexFeatures.mod.chat.ChatTools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected EditBox input;

    @Unique
    private ChatTools hinex$tools;

    private ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hinex$attachTools(CallbackInfo ci) {
        hinex$tools = ChatTools.attach(hinex$tools, input, width, height);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void hinex$extractTools(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (hinex$tools != null) hinex$tools.extract(g, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hinex$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (hinex$tools != null && hinex$tools.mouseClicked(event)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void hinex$mouseScrolled(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (hinex$tools != null && hinex$tools.mouseScrolled(x, y, scrollY)) cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void hinex$keyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (hinex$tools != null && hinex$tools.keyPressed(event)) cir.setReturnValue(true);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (hinex$tools != null && hinex$tools.mouseDragged(event)) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (hinex$tools != null && hinex$tools.mouseReleased()) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (hinex$tools != null && hinex$tools.charTyped(event)) return true;
        return super.charTyped(event);
    }
}
