package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.JAKOP.hinexFeatures.mod.vfx.render.HinexPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RenderPipelines.class)
public abstract class RenderPipelinesMixin {

    @Inject(method = "optionalPipelines", at = @At("RETURN"), cancellable = true)
    private static void hinex$addPipelines(CallbackInfoReturnable<List<RenderPipeline>> cir) {
        List<RenderPipeline> pipelines = new ArrayList<>(cir.getReturnValue());
        pipelines.addAll(HinexPipelines.all());
        cir.setReturnValue(pipelines);
    }
}
