package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import me.JAKOP.hinexFeatures.mod.vanish.DissolveCollector;
import me.JAKOP.hinexFeatures.mod.vanish.DissolveInstance;
import me.JAKOP.hinexFeatures.mod.vanish.VfxRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void hinex$vanish(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
                              SubmitNodeCollector collector, CallbackInfo ci) {
        if (collector instanceof DissolveCollector || !(state instanceof VfxRenderState vfx)) return;
        if (vfx.hinex$hidden()) {
            ci.cancel();
            return;
        }
        DissolveInstance dissolve = vfx.hinex$dissolve();
        if (dissolve != null) {
            ci.cancel();
            ((EntityRenderDispatcher) (Object) this).submit(state, camera, x, y, z, poseStack, new DissolveCollector(collector, dissolve));
        }
    }
}
