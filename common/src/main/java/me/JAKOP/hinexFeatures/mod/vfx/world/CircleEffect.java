package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class CircleEffect extends WorldEffect {

    private final int color;
    private final int color2;
    private final float radius;
    private final float[] motes = new float[1];

    public CircleEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0xB46CFF);
        this.color2 = params.color("color2", 0x4FC3FF);
        this.radius = params.number("size", 3.0F);
    }

    @Override
    protected float fadeInTime() {
        return 0.9F;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float age = age(frame.now);
        float open = VfxMath.easeOutBack(age / 0.9F);
        float r = radius * Math.max(0.01F, open);
        float x = frame.rx(pos.x), y = frame.ry(pos.y) + 0.03F, z = frame.rz(pos.z);

        frame.soft.flat(x, y - 0.005F, z, r * 1.08F, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.45F * fade, 0x0A0418));
        frame.glow.flat(x, y, z, r, age * 0.25F, VfxShape.RUNES, 3, VfxMath.argb(fade, color));
        frame.glow.flat(x, y + 0.01F, z, r * 0.52F, -age * 0.6F, VfxShape.RUNES, 11, VfxMath.argb(0.85F * fade, color2));
        frame.glow.flat(x, y - 0.01F, z, r * 1.25F, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.25F * fade, color));

        for (int i = 0; i < 6; i++) {
            double angle = i * VfxMath.TAU / 6.0 + age * 0.25;
            float bx = x + (float) Math.cos(angle) * r * 0.8F;
            float bz = z + (float) Math.sin(angle) * r * 0.8F;
            float h = 1.2F + 0.4F * (float) Math.sin(age * 3.0 + i);
            frame.glow.ribbon(bx, y, bz, bx, y + h * fade, bz, 0.06F, VfxShape.BEAM, 0.0F, h, VfxMath.argb(0.6F * fade, color2));
        }

        int count = emit(40.0F * fade * (radius / 3.0F), frame.dt, motes);
        for (int i = 0; i < count; i++) {
            float angle = rand(0.0F, VfxMath.TAU);
            float d = (float) Math.sqrt(random.nextFloat()) * r;
            VfxParticles.spawn().at(pos.x + Math.cos(angle) * d, pos.y + 0.05, pos.z + Math.sin(angle) * d)
                    .velocity(0.0, rand(0.6F, 1.6F), 0.0).life(rand(1.2F, 2.4F)).size(rand(0.03F, 0.06F), 0.0F)
                    .color(VfxMath.argb(1.0F, random.nextBoolean() ? color : color2), VfxMath.argb(0.0F, 0xFFFFFF))
                    .look(VfxShape.GLOW, VfxLayer.GLOW).flicker(0.3F);
        }
    }
}
