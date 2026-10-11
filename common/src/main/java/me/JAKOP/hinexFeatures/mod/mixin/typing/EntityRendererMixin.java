package me.JAKOP.hinexFeatures.mod.mixin.typing;

import com.mojang.blaze3d.vertex.PoseStack;
import me.JAKOP.hinexFeatures.mod.marks.MarkRenderState;
import me.JAKOP.hinexFeatures.mod.marks.PlayerMarks;
import me.JAKOP.hinexFeatures.mod.typing.TypingIndicators;
import me.JAKOP.hinexFeatures.mod.typing.TypingRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "submit", at = @At("TAIL"))
    private void hinex$submitTypingBubble(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                          CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof TypingRenderState typing) || typing.hinex$bubbleAnchor() == null) return;
        int lines = state.nameTag == null ? 0 : state.scoreText == null ? 1 : 2;
        if (typing.hinex$typing()) {
            TypingIndicators.submitBubble(poseStack, collector, camera, typing.hinex$bubbleAnchor(), lines);
            lines++;
        }
        PlayerMarks.Mark mark = state instanceof MarkRenderState marked ? marked.hinex$mark() : null;
        if (mark != null) PlayerMarks.submit(poseStack, collector, camera, typing.hinex$bubbleAnchor(), mark, lines);
    }
}
