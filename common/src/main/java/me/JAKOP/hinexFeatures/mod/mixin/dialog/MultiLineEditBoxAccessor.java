package me.JAKOP.hinexFeatures.mod.mixin.dialog;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {

    @Accessor("textField")
    MultilineTextField hinex$textField();
}
