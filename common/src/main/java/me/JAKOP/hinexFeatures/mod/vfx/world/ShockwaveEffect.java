package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.camera.CameraFx;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenPass;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class ShockwaveEffect extends WorldEffect {

    private final int color;
    private final float size;
    private final float[] dust = new float[1];
    private boolean started;
    private float currentRadius;

    public ShockwaveEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration <= 0.0 ? 1.6 : duration);
        this.color = params.color("color", 0x9FD8FF);
        this.size = params.number("size", 12.0F);
    }

    @Override
    protected float fadeInTime() {
        return 0.01F;
    }

    @Override
    protected float fadeOutTime() {
        return (float) Math.max(0.3, duration * 0.6);
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float t = life(frame.now);
        float radius = size * VfxMath.easeOutCubic(t);
        currentRadius = radius;
        float x = frame.rx(pos.x), y = frame.ry(pos.y) + 0.05F, z = frame.rz(pos.z);
        if (!started) {
            started = true;
            VfxParticles.spawn().at(pos.x, pos.y + 0.5, pos.z).life(0.35F).size(2.5F, 0.8F)
                    .color(0xFFFFFFFF, VfxMath.argb(0.0F, color)).look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
            double distance = frame.camera.distanceTo(pos);
            if (distance < size * 1.6) {
                CameraFx.impulse((float) (2.2 * (1.0 - distance / (size * 1.6))), 0.6);
            }
        }
        frame.glow.flat(x, y, z, Math.max(0.1F, radius), 0.0F, VfxShape.WAVE, 0, VfxMath.argb(fade, color));
        frame.glow.flat(x, y + 0.01F, z, Math.max(0.1F, radius * 0.72F), 0.0F, VfxShape.WAVE, 1, VfxMath.argb(0.5F * fade, 0xFFFFFF));
        int count = emit(160.0F * fade, frame.dt, dust);
        for (int i = 0; i < count; i++) {
            float angle = rand(0.0F, VfxMath.TAU);
            VfxParticles.spawn().at(pos.x + Math.cos(angle) * radius, pos.y + 0.1, pos.z + Math.sin(angle) * radius)
                    .velocity(Math.cos(angle) * 2.0, rand(0.5F, 2.0F), Math.sin(angle) * 2.0)
                    .life(rand(0.6F, 1.3F)).size(rand(0.25F, 0.5F), rand(0.6F, 1.0F))
                    .color(VfxMath.argb(0.35F, 0xB9B2A8), VfxMath.argb(0.0F, 0x8F8A82))
                    .look(VfxShape.SMOKE, VfxLayer.SOFT).physics(0.5F, 0.8F).spin(0.0F, rand(-1.0F, 1.0F));
        }
    }

    @Override
    public @Nullable ScreenPass screenPass(VfxFrame frame) {
        if (currentRadius < 0.2F) return null;
        Vec3 pos = origin;
        float x = frame.rx(pos.x), y = frame.ry(pos.y), z = frame.rz(pos.z);
        Vector3f screen = frame.project(x, y, z);
        if (screen == null || screen.z > 80.0F) return null;
        float projected = frame.projectedRadius(x, y, z, currentRadius);
        float fade = fade(frame.now);
        return new ScreenPass("ripple", fade, (float) frame.now % 3600.0F, life(frame.now), 0.0F, color, 0.0F,
                screen.x, screen.y, projected, 0.025F * fade, 1.0F);
    }
}
