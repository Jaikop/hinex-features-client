package me.JAKOP.hinexFeatures.mod.mixin.typing;

import me.JAKOP.hinexFeatures.mod.marks.MarkRenderState;
import me.JAKOP.hinexFeatures.mod.marks.PlayerMarks;
import me.JAKOP.hinexFeatures.mod.typing.TypingRenderState;
import me.JAKOP.hinexFeatures.mod.vanish.DissolveInstance;
import me.JAKOP.hinexFeatures.mod.vanish.VfxRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public abstract class AvatarRenderStateMixin implements TypingRenderState, VfxRenderState, MarkRenderState {

    @Unique
    private boolean hinex$typing;

    @Unique
    private PlayerMarks.@Nullable Mark hinex$mark;

    @Override
    public PlayerMarks.@Nullable Mark hinex$mark() {
        return hinex$mark;
    }

    @Override
    public void hinex$setMark(PlayerMarks.@Nullable Mark mark) {
        hinex$mark = mark;
    }

    @Unique
    private @Nullable Vec3 hinex$anchor;

    @Unique
    private boolean hinex$hidden;

    @Unique
    private @Nullable DissolveInstance hinex$dissolve;

    @Override
    public boolean hinex$typing() {
        return hinex$typing;
    }

    @Override
    public @Nullable Vec3 hinex$bubbleAnchor() {
        return hinex$anchor;
    }

    @Override
    public void hinex$setTyping(boolean typing, @Nullable Vec3 anchor) {
        hinex$typing = typing;
        hinex$anchor = anchor;
    }

    @Override
    public boolean hinex$hidden() {
        return hinex$hidden;
    }

    @Override
    public void hinex$setHidden(boolean hidden) {
        hinex$hidden = hidden;
    }

    @Override
    public @Nullable DissolveInstance hinex$dissolve() {
        return hinex$dissolve;
    }

    @Override
    public void hinex$setDissolve(@Nullable DissolveInstance instance) {
        hinex$dissolve = instance;
    }
}
