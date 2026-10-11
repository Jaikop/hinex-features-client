package me.JAKOP.hinexFeatures.mod.mixin.prints;

import me.JAKOP.hinexFeatures.mod.prints.PrintsClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "openItemGui", at = @At("HEAD"), cancellable = true)
    private void hinex$printsBook(ItemStack stack, InteractionHand hand, CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (PrintsClient.isFilledSample(self.getItemInHand(other))) ci.cancel();
    }
}
