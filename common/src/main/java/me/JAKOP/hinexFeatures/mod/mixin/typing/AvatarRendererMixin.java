package me.JAKOP.hinexFeatures.mod.mixin.typing;

import me.JAKOP.hinexFeatures.mod.marks.MarkRenderState;
import me.JAKOP.hinexFeatures.mod.marks.PlayerMarks;
import me.JAKOP.hinexFeatures.mod.typing.TypingIndicators;
import me.JAKOP.hinexFeatures.mod.typing.TypingRenderState;
import me.JAKOP.hinexFeatures.mod.vanish.DissolveInstance;
import me.JAKOP.hinexFeatures.mod.vanish.VanishFx;
import me.JAKOP.hinexFeatures.mod.vanish.VfxRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void hinex$extractTyping(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean typing = entity != minecraft.player
                && TypingIndicators.isTyping(entity.getUUID())
                && !entity.isSpectator()
                && (minecraft.player == null || !entity.isInvisibleTo(minecraft.player));
        PlayerMarks.Mark mark = PlayerMarks.get(entity.getUUID());
        if (mark != null && (entity.isSpectator() || VanishFx.hideLive(entity.getUUID())
                || minecraft.player != null && entity != minecraft.player && entity.isInvisibleTo(minecraft.player))) {
            mark = null;
        }
        Vec3 anchor = typing || mark != null ? entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks)) : null;
        ((TypingRenderState) state).hinex$setTyping(typing && anchor != null, anchor);
        ((MarkRenderState) state).hinex$setMark(anchor == null ? null : mark);

        VfxRenderState vfx = (VfxRenderState) state;
        vfx.hinex$setHidden(VanishFx.hideLive(entity.getUUID()));
        DissolveInstance dissolve = VanishFx.liveInstance(entity.getUUID());
        if (dissolve != null) {
            dissolve.track(state.x, state.y, state.z, entity.getBbHeight(), entity.getBbWidth());
        }
        vfx.hinex$setDissolve(dissolve);
    }
}
