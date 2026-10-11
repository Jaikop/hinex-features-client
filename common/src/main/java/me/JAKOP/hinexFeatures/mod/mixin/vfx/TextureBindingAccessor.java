package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface TextureBindingAccessor {

    @Accessor("location")
    Identifier hinex$location();
}
