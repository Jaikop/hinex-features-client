package me.JAKOP.hinexFeatures.mod.typing;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public interface TypingRenderState {

    boolean hinex$typing();

    @Nullable Vec3 hinex$bubbleAnchor();

    void hinex$setTyping(boolean typing, @Nullable Vec3 anchor);
}
