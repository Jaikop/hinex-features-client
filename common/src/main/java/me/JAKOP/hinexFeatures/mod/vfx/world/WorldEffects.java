package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WorldEffects {

    @FunctionalInterface
    private interface Factory {
        WorldEffect create(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration);
    }

    private static final Map<String, Factory> FACTORIES = new LinkedHashMap<>();

    static {
        FACTORIES.put("beam", BeamEffect::new);
        FACTORIES.put("blackhole", BlackHoleEffect::new);
        FACTORIES.put("highlight", HighlightEffect::new);
        FACTORIES.put("sparks", SparksEffect::new);
        FACTORIES.put("shockwave", ShockwaveEffect::new);
        FACTORIES.put("circle", CircleEffect::new);
        FACTORIES.put("portal", PortalEffect::new);
        FACTORIES.put("aura", AuraEffect::new);
        FACTORIES.put("lightning", LightningEffect::new);
        FACTORIES.put("fireflies", FirefliesEffect::new);
    }

    private WorldEffects() {
    }

    public static boolean exists(String name) {
        return FACTORIES.containsKey(name);
    }

    public static @Nullable WorldEffect create(String name, VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        Factory factory = FACTORIES.get(name);
        return factory == null ? null : factory.create(params, origin, follow, start, duration);
    }
}
