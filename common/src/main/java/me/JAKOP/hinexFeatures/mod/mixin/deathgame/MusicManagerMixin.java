package me.JAKOP.hinexFeatures.mod.mixin.deathgame;

import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameClient;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hinex$quietDuringDeathGame(CallbackInfo ci) {
        if (DeathGameClient.suppressMusic()) ci.cancel();
    }
}
