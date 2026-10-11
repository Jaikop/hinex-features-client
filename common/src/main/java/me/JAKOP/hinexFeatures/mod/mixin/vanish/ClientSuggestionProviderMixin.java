package me.JAKOP.hinexFeatures.mod.mixin.vanish;

import me.JAKOP.hinexFeatures.mod.vanish.VanishFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(ClientSuggestionProvider.class)
public abstract class ClientSuggestionProviderMixin {

    @Inject(method = {"getOnlinePlayerNames", "getCustomTabSuggestions"}, at = @At("RETURN"), cancellable = true)
    private void hinex$hideUnlisted(CallbackInfoReturnable<Collection<String>> cir) {
        if (!VanishFx.anyUnlisted()) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        cir.setReturnValue(cir.getReturnValue().stream().filter(name -> {
            PlayerInfo info = connection.getPlayerInfo(name);
            return info == null || !VanishFx.isUnlisted(info.getProfile().id());
        }).toList());
    }
}
