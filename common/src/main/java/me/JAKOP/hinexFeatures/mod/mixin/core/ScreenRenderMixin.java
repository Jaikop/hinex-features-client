package me.JAKOP.hinexFeatures.mod.mixin.core;

import me.JAKOP.hinexFeatures.mod.packs.PackDownloadOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenRenderMixin {

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void hinex$overlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        PackDownloadOverlay.extract((Screen) (Object) this, g);
    }
}
