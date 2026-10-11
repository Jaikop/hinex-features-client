package me.JAKOP.hinexFeatures.mod.vanish;

import me.JAKOP.hinexFeatures.mod.mixin.vfx.RenderSetupAccessor;
import me.JAKOP.hinexFeatures.mod.mixin.vfx.RenderTypeAccessor;
import me.JAKOP.hinexFeatures.mod.mixin.vfx.TextureBindingAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

final class DissolveTextures {

    private static final Map<RenderType, Optional<Identifier>> CACHE = new IdentityHashMap<>();

    private DissolveTextures() {
    }

    static @Nullable Identifier textureOf(RenderType renderType) {
        if (CACHE.size() > 2048) CACHE.clear();
        return CACHE.computeIfAbsent(renderType, DissolveTextures::lookup).orElse(null);
    }

    private static Optional<Identifier> lookup(RenderType renderType) {
        RenderTypeAccessor accessor = (RenderTypeAccessor) renderType;
        String name = accessor.hinex$name();
        if (name.contains("glint") || name.contains("outline") || name.contains("crumbling")) return Optional.empty();
        Map<String, ?> textures = ((RenderSetupAccessor) (Object) accessor.hinex$state()).hinex$textures();
        Object binding = textures.get("Sampler0");
        if (binding == null) return Optional.empty();
        return Optional.ofNullable(((TextureBindingAccessor) binding).hinex$location());
    }
}
