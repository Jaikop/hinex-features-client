package me.JAKOP.hinexFeatures.mod.mixin.prints;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.KnowledgeBookItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KnowledgeBookItem.class)
public abstract class KnowledgeBookItemMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void hinex$ourItem(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (level.isClientSide() && data != null && !data.copyTag().getStringOr("hinexfeatures_item", "").isEmpty()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
