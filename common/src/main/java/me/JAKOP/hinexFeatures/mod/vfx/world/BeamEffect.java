package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.QuadSink;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class BeamEffect extends WorldEffect {

    private final int color;
    private final float radius;
    private final float height;
    private final float[] motes = new float[1];
    private boolean impacted;
    private float nextWave;

    public BeamEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0x8AD8FF);
        this.radius = params.number("size", 0.6F);
        this.height = params.number("height", 48.0F);
    }

    @Override
    protected float fadeOutTime() {
        return 0.8F;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float age = age(frame.now);
        float x = frame.rx(pos.x), y = frame.ry(pos.y), z = frame.rz(pos.z);

        float descend = VfxMath.easeInCubic(age / 0.35F);
        float top = y + height;
        float bottom = top - height * descend;
        float pulse = 1.0F + 0.12F * (float) Math.sin(age * 9.0) + 0.06F * (float) Math.sin(age * 23.0);
        float width = radius * pulse * (0.4F + 0.6F * fade);

        QuadSink glow = frame.glow;
        float v0 = (float) pos.y + height;
        float v1 = (float) pos.y + height - height * descend;
        glow.ribbon(x, top, z, x, bottom, z, width * 2.6F, VfxShape.BEAM, v0, v1, VfxMath.argb(0.35F * fade, color));
        glow.ribbon(x, top, z, x, bottom, z, width * 1.3F, VfxShape.BEAM, v0 * 1.3F, v1 * 1.3F, VfxMath.argb(0.7F * fade, color));
        glow.ribbon(x, top, z, x, bottom, z, width * 0.45F, VfxShape.BEAM, v0 * 0.7F, v1 * 0.7F, VfxMath.argb(fade, VfxMath.shade(color, 1.3F)));

        if (descend < 1.0F) return;

        if (!impacted) {
            impacted = true;
            nextWave = age;
            for (int i = 0; i < 40; i++) {
                float angle = rand(0.0F, VfxMath.TAU);
                float speed = rand(2.0F, 7.0F);
                VfxParticles.spawn().at(pos.x, pos.y + 0.1, pos.z)
                        .velocity(Math.cos(angle) * speed, rand(1.0F, 6.0F), Math.sin(angle) * speed)
                        .life(rand(0.5F, 1.2F)).size(0.035F, 0.01F).color(0xFFFFFFFF, VfxMath.argb(1.0F, color))
                        .look(VfxShape.SPARK, VfxLayer.GLOW).physics(-14.0F, 0.6F).stretch(0.04F).floor(pos.y + 0.02);
            }
            VfxParticles.spawn().at(pos.x, pos.y + 0.6, pos.z).life(0.45F).size(radius * 7.0F, radius * 2.0F)
                    .color(VfxMath.argb(1.0F, 0xFFFFFF), VfxMath.argb(0.0F, color)).look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
        }

        glow.flat(x, y + 0.03F, z, radius * 4.0F * pulse, age * 0.4F, VfxShape.GLOW, 0, VfxMath.argb(0.55F * fade, color));
        if (age >= nextWave && !isStopping()) {
            nextWave = age + 0.7F;
            VfxParticles.spawn().at(pos.x, pos.y + 0.04, pos.z).life(1.1F).size(radius * 1.5F, radius * 9.0F)
                    .color(VfxMath.argb(0.9F, color), VfxMath.argb(0.0F, color)).look(VfxShape.WAVE, VfxLayer.GLOW).fade(0.05F, 0.8F).flat();
        }
        int count = emit(26.0F * fade, frame.dt, motes);
        for (int i = 0; i < count; i++) {
            float angle = rand(0.0F, VfxMath.TAU);
            float r = rand(0.0F, radius * 1.1F);
            VfxParticles.spawn().at(pos.x + Math.cos(angle) * r, pos.y + rand(0.0F, 1.5F), pos.z + Math.sin(angle) * r)
                    .velocity(0.0, rand(2.0F, 6.0F), 0.0).life(rand(1.0F, 2.2F)).size(rand(0.03F, 0.07F), 0.0F)
                    .color(0xFFFFFFFF, VfxMath.argb(1.0F, color)).look(VfxShape.GLOW, VfxLayer.GLOW).flicker(0.3F);
        }
    }
}
