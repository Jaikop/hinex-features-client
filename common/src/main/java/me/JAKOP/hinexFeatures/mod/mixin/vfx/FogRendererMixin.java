package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import me.JAKOP.hinexFeatures.mod.border.BorderFog;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void hinex$borderFog(CallbackInfoReturnable<FogData> cir) {
        BorderFog.applyFog(cir.getReturnValue());
    }
}
