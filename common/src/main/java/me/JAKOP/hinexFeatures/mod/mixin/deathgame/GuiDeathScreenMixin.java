package me.JAKOP.hinexFeatures.mod.mixin.deathgame;

import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiDeathScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void hinex$deathGame(@Nullable Screen screen, CallbackInfo ci) {
        if (DeathGameClient.intercept(screen)) ci.cancel();
    }
}
