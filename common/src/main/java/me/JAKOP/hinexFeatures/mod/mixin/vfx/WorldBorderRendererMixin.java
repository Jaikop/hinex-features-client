package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import me.JAKOP.hinexFeatures.mod.border.BorderFog;
import net.minecraft.client.renderer.WorldBorderRenderer;
import net.minecraft.client.renderer.state.level.WorldBorderRenderState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public abstract class WorldBorderRendererMixin {

    @Inject(method = "extract", at = @At("TAIL"))
    private void hinex$hideWall(WorldBorder border, float deltaPartialTick, Vec3 cameraPos, double renderDistance,
                                WorldBorderRenderState state, CallbackInfo ci) {
        if (BorderFog.hidesWall()) state.alpha = 0.0;
    }
}
