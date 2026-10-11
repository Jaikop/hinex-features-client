package me.JAKOP.hinexFeatures.mod.mixin.admin;

import me.JAKOP.hinexFeatures.mod.admin.AdminKeys;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void hinex$adminKeys(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 1 && AdminKeys.onPress(event)) ci.cancel();
    }
}
