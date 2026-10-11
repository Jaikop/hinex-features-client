package me.JAKOP.hinexFeatures.mod.mixin.vfx;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {

    @Accessor("state")
    RenderSetup hinex$state();

    @Accessor("name")
    String hinex$name();

    @Invoker("create")
    static RenderType hinex$create(String name, RenderSetup state) {
        throw new AssertionError();
    }
}
