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

public final class PortalEffect extends WorldEffect {

    private final int color;
    private final int color2;
    private final float height;
    private final float[] motes = new float[1];

    public PortalEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0x8A5CFF);
        this.color2 = params.color("color2", 0x4FC3FF);
        this.height = params.number("size", 3.0F);
    }

    @Override
    protected float fadeInTime() {
        return 0.7F;
    }

    @Override
    protected float fadeOutTime() {
        return 0.7F;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float age = age(frame.now);
        float open = VfxMath.easeOutBack(age / 0.8F) * fade;
        float halfHeight = height * 0.5F * Math.max(0.02F, open);
        float halfWidth = height * 0.34F * Math.max(0.05F, Math.min(1.0F, open * 1.3F));
        double cy = pos.y + height * 0.5;
        float x = frame.rx(pos.x), y = frame.ry(cy), z = frame.rz(pos.z);

        frame.glow.upright(frame, x, y, z, halfWidth * 1.6F, halfHeight * 1.3F, VfxShape.GLOW, 0, VfxMath.argb(0.4F * fade, color));
        frame.glow.upright(frame, x, y, z, halfWidth, halfHeight, VfxShape.PORTAL, 5, VfxMath.argb(fade, color));
        frame.glow.upright(frame, x, y, z, halfWidth * 0.7F, halfHeight * 0.7F, VfxShape.PORTAL, 9, VfxMath.argb(0.6F * fade, color2));
        frame.soft.upright(frame, x, y, z, halfWidth * 0.45F, halfHeight * 0.55F, VfxShape.GLOW, 0, VfxMath.argb(0.55F * fade, 0x05000A));

        int count = emit(70.0F * fade, frame.dt, motes);
        for (int i = 0; i < count; i++) {
            double angle = rand(0.0F, VfxMath.TAU);
            float distance = rand(height * 0.8F, height * 1.6F);
            VfxParticles.spawn().at(pos.x + Math.cos(angle) * distance, cy + gaussian() * height * 0.35, pos.z + Math.sin(angle) * distance)
                    .attract(pos.x, cy, pos.z, 14.0F, 3.5F).physics(0.0F, 0.4F)
                    .life(rand(1.0F, 1.8F)).size(rand(0.03F, 0.06F), 0.01F)
                    .color(VfxMath.argb(1.0F, random.nextBoolean() ? color : color2), 0xFFFFFFFF)
                    .look(VfxShape.SPARK, VfxLayer.GLOW).stretch(0.06F).fade(0.25F, 0.2F);
        }
    }
}
