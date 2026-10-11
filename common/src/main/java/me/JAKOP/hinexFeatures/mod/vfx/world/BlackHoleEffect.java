package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.camera.CameraFx;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenPass;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class BlackHoleEffect extends WorldEffect {

    private final int color;
    private final int color2;
    private final float radius;
    private final float strength;
    private final float[] debris = new float[1];
    private final float tilt;
    private boolean collapsed;
    private float currentRadius;

    public BlackHoleEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0xFF9A3C);
        this.color2 = params.color("color2", 0x8A5CFF);
        this.radius = params.number("size", 1.4F);
        this.strength = params.number("strength", 1.0F);
        this.tilt = rand(0.16F, 0.26F);
    }

    @Override
    protected float fadeInTime() {
        return 1.0F;
    }

    @Override
    protected float fadeOutTime() {
        return 0.5F;
    }

    private float radiusAt(double now) {
        float age = age(now);
        float grow = VfxMath.easeOutBack(age / 1.2F);
        float collapse = 1.0F;
        double end = duration > 0.0 ? start + duration : Double.MAX_VALUE;
        if (now > end - 0.5) {
            collapse = VfxMath.clamp01((float) (end - now) / 0.5F);
            collapse = collapse * collapse;
        }
        if (isStopping()) collapse = Math.min(collapse, fade(now));
        return radius * grow * collapse;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float x = frame.rx(pos.x), y = frame.ry(pos.y), z = frame.rz(pos.z);
        float r = radiusAt(frame.now);
        currentRadius = r;
        float age = age(frame.now);

        if (!collapsed && duration > 0.0 && frame.now > start + duration - 0.12) {
            collapsed = true;
            collapse(frame, pos);
        }
        if (r < 0.01F) return;

        float spin = age * 0.8F;
        Vector3f axisA = new Vector3f((float) Math.cos(spin), 0.0F, (float) Math.sin(spin)).mul(r * 4.2F);
        Vector3f axisB = new Vector3f((float) -Math.sin(spin), 0.0F, (float) Math.cos(spin)).rotateX(tilt).mul(r * 4.2F);
        frame.glow.plane(x, y, z, axisA, axisB, VfxShape.SWIRL, 0, VfxMath.argb(fade, color));
        frame.glow.plane(x, y, z, new Vector3f(axisA).mul(0.72F), new Vector3f(axisB).mul(0.72F), VfxShape.SWIRL, 7,
                VfxMath.argb(0.85F * fade, VfxMath.shade(color, 1.25F)));
        frame.glow.billboard(frame, x, y, z, r * 3.5F, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.35F * fade, color2));
        frame.glow.billboard(frame, x, y, z, r * 1.25F, age * 0.5F, VfxShape.RING, 0, VfxMath.argb(0.9F * fade, VfxMath.shade(color, 1.2F)));
        frame.soft.billboard(frame, x, y, z, r * 1.7F, 0.0F, VfxShape.HOLE, 0, VfxMath.argb(fade, 0x000000));

        int count = emit(90.0F * fade * Math.max(0.3F, r / radius), frame.dt, debris);
        for (int i = 0; i < count; i++) {
            double angle = rand(0.0F, VfxMath.TAU);
            float distance = rand(r * 3.5F, r * 7.0F);
            double px = pos.x + Math.cos(angle) * distance;
            double pz = pos.z + Math.sin(angle) * distance;
            double py = pos.y + gaussian() * r * 0.8F;
            boolean hot = random.nextFloat() < 0.6F;
            VfxParticles.spawn().at(px, py, pz)
                    .velocity(-Math.sin(angle) * 2.5, 0.0, Math.cos(angle) * 2.5)
                    .attract(pos.x, pos.y, pos.z, 26.0F * strength, 5.0F)
                    .life(rand(1.2F, 2.2F)).size(rand(0.03F, 0.07F), 0.005F)
                    .color(VfxMath.argb(1.0F, hot ? color : color2), VfxMath.argb(1.0F, 0xFFFFFF))
                    .look(VfxShape.SPARK, VfxLayer.GLOW).stretch(0.05F).physics(0.0F, 0.35F).fade(0.2F, 0.15F);
        }
    }

    private void collapse(VfxFrame frame, Vec3 pos) {
        VfxParticles.spawn().at(pos.x, pos.y, pos.z).life(0.6F).size(radius * 2.0F, radius * 14.0F)
                .color(VfxMath.argb(1.0F, 0xFFFFFF), VfxMath.argb(0.0F, color2)).look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
        VfxParticles.spawn().at(pos.x, pos.y, pos.z).life(1.2F).size(radius, radius * 16.0F)
                .color(VfxMath.argb(0.9F, color), VfxMath.argb(0.0F, color2)).look(VfxShape.WAVE, VfxLayer.GLOW).fade(0.02F, 0.8F).flat();
        for (int i = 0; i < 90; i++) {
            double theta = rand(0.0F, VfxMath.TAU);
            double phi = Math.acos(rand(-1.0F, 1.0F));
            float speed = rand(4.0F, 14.0F);
            VfxParticles.spawn().at(pos.x, pos.y, pos.z)
                    .velocity(Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed)
                    .life(rand(0.5F, 1.2F)).size(0.05F, 0.01F).color(0xFFFFFFFF, VfxMath.argb(1.0F, color2))
                    .look(VfxShape.SPARK, VfxLayer.GLOW).stretch(0.03F).physics(0.0F, 0.8F);
        }
        double distance = frame.camera.distanceTo(pos);
        CameraFx.impulse((float) (2.5 / Math.max(1.0, distance / 8.0)), 0.7);
    }

    @Override
    public @Nullable ScreenPass screenPass(VfxFrame frame) {
        float r = currentRadius;
        if (r < 0.05F) return null;
        Vec3 pos = origin;
        float x = frame.rx(pos.x), y = frame.ry(pos.y), z = frame.rz(pos.z);
        Vector3f screen = frame.project(x, y, z);
        if (screen == null || screen.z > 96.0F) return null;
        HitResult hit = frame.level.clip(new ClipContext(frame.camera, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(pos) > 1.0) return null;
        float projected = frame.projectedRadius(x, y, z, r);
        if (projected < 0.002F) return null;
        float fade = fade(frame.now);
        return new ScreenPass("lensing", fade, (float) frame.now % 3600.0F, life(frame.now), 0.0F, color2, 0.0F,
                screen.x, screen.y, projected, 0.9F * strength, 1.0F);
    }
}
