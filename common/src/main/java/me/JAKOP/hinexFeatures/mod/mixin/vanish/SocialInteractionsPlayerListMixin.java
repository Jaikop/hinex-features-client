package me.JAKOP.hinexFeatures.mod.mixin.vanish;

import me.JAKOP.hinexFeatures.mod.vanish.VanishFx;
import net.minecraft.client.gui.screens.social.PlayerEntry;
import net.minecraft.client.gui.screens.social.SocialInteractionsPlayerList;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(SocialInteractionsPlayerList.class)
public abstract class SocialInteractionsPlayerListMixin {

    @ModifyVariable(method = "updateFiltersAndScroll", at = @At("HEAD"), argsOnly = true)
    private Collection<PlayerEntry> hinex$hideUnlisted(Collection<PlayerEntry> entries) {
        if (!VanishFx.anyUnlisted()) return entries;
        return entries.stream().filter(entry -> !VanishFx.isUnlisted(entry.getPlayerId())).toList();
    }

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void hinex$skipUnlisted(PlayerInfo info, SocialInteractionsScreen.Page page, CallbackInfo ci) {
        if (VanishFx.isUnlisted(info.getProfile().id())) ci.cancel();
    }
}
