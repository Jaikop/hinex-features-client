package me.JAKOP.hinexFeatures.mod.vanish;

import org.jspecify.annotations.Nullable;

public interface VfxRenderState {

    boolean hinex$hidden();

    void hinex$setHidden(boolean hidden);

    @Nullable DissolveInstance hinex$dissolve();

    void hinex$setDissolve(@Nullable DissolveInstance instance);
}
