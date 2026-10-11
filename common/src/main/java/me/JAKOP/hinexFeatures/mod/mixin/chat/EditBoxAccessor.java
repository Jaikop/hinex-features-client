package me.JAKOP.hinexFeatures.mod.mixin.chat;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {

    @Accessor("highlightPos")
    int hinex$highlightPos();

    @Accessor("maxLength")
    int hinex$maxLength();
}
