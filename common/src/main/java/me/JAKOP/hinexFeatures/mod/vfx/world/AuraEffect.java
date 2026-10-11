package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class AuraEffect extends WorldEffect {

    private final int color;
    private final int color2;
    private final float scale;
    private final float[] motes = new float[1];
    private final float[] spiral = new float[1];

    public AuraEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0xFFD24A);
        this.color2 = params.color("color2", 0xFFF3B0);
        this.scale = params.number("size", 1.0F);
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        Entity entity = followed(frame);
        float height = entity != null ? entity.getBbHeight() : 1.8F;
        float width = (entity != null ? entity.getBbWidth() : 0.6F) * 1.3F * scale;
        float age = age(frame.now);
        float x = frame.rx(pos.x), y = frame.ry(pos.y), z = frame.rz(pos.z);

        frame.glow.billboard(frame, x, y + height * 0.5F, z, height * 0.9F * scale, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.22F * fade, color));
        float bob = (float) Math.sin(age * 2.0) * 0.15F;
        frame.glow.flat(x, y + 0.05F, z, width * 1.1F, age * 1.2F, VfxShape.RING, 0, VfxMath.argb(0.8F * fade, color));
        frame.glow.flat(x, y + height * 0.5F + bob, z, width * 0.95F, -age * 1.6F, VfxShape.RING, 1, VfxMath.argb(0.5F * fade, color2));
        frame.glow.flat(x, y + height + 0.25F - bob * 0.5F, z, width * 0.45F, age * 2.0F, VfxShape.RING, 2, VfxMath.argb(0.7F * fade, color2));

        int spiralCount = emit(36.0F * fade, frame.dt, spiral);
        for (int i = 0; i < spiralCount; i++) {
            double angle = age * 5.0 + (i % 2) * Math.PI + rand(-0.2F, 0.2F);
            VfxParticles.spawn().at(pos.x + Math.cos(angle) * width, pos.y + rand(0.0F, 0.3F), pos.z + Math.sin(angle) * width)
                    .velocity(0.0, rand(1.2F, 1.8F), 0.0).attract(pos.x, pos.y, pos.z, 0.0F, 0.0F)
                    .life(rand(1.0F, 1.4F)).size(0.05F, 0.0F).color(VfxMath.argb(1.0F, color2), VfxMath.argb(0.0F, color))
                    .look(VfxShape.GLOW, VfxLayer.GLOW);
        }
        int count = emit(18.0F * fade, frame.dt, motes);
        for (int i = 0; i < count; i++) {
            float angle = rand(0.0F, VfxMath.TAU);
            float r = rand(0.1F, width);
            VfxParticles.spawn().at(pos.x + Math.cos(angle) * r, pos.y + rand(0.0F, height), pos.z + Math.sin(angle) * r)
                    .velocity(0.0, rand(0.4F, 1.0F), 0.0).life(rand(0.8F, 1.5F)).size(rand(0.04F, 0.09F), 0.0F)
                    .color(VfxMath.argb(1.0F, color), VfxMath.argb(0.0F, 0xFFFFFF)).look(VfxShape.STAR, VfxLayer.GLOW)
                    .spin(0.0F, rand(-3.0F, 3.0F)).flicker(0.2F);
        }
    }
}
