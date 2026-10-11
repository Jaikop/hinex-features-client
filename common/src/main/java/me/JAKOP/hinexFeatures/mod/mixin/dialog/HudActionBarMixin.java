package me.JAKOP.hinexFeatures.mod.mixin.dialog;

import me.JAKOP.hinexFeatures.mod.dialog.DialogClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudActionBarMixin {

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void hinex$hideUnderDialog(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (DialogClient.hidesActionBar()) ci.cancel();
    }
}
