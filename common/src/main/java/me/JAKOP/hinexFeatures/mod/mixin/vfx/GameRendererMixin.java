package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.border.BorderFog;
import net.minecraft.client.Minecraft;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vfx.camera.CameraFx;
import me.JAKOP.hinexFeatures.mod.vfx.render.ScreenVfxRenderer;
import me.JAKOP.hinexFeatures.mod.vfx.render.WorldVfxRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    public abstract RenderTarget mainRenderTarget();

    @Inject(method = "render", at = @At("HEAD"))
    private void hinex$borderFog(CallbackInfo ci) {
        try {
            BorderFog.frame(Minecraft.getInstance());
        } catch (RuntimeException e) {
            HinexFeaturesClient.LOGGER.error("Туман у границы", e);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER))
    private void hinex$screenEffects(CallbackInfo ci) {
        VfxClock.onFrame();
        try {
            WorldVfxRenderer.renderLate(mainRenderTarget());
            ScreenVfxRenderer.render(mainRenderTarget());
        } catch (RuntimeException e) {
            HinexFeaturesClient.LOGGER.error("Экранные эффекты", e);
        }
    }

    @Inject(method = "bobHurt", at = @At("RETURN"))
    private void hinex$cameraEffects(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        CameraFx.apply(poseStack);
    }
}
