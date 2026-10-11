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

public final class SparksEffect extends WorldEffect {

    private final int color;
    private final int count;
    private final float speed;
    private final boolean fountain;
    private final float[] stream = new float[1];
    private boolean burst;

    public SparksEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0xFFB347);
        this.count = Math.max(1, Math.min(600, params.integer("count", 70)));
        this.speed = params.number("speed", 1.0F);
        this.fountain = duration > 2.0 || duration <= 0.0;
    }

    @Override
    protected float fadeInTime() {
        return 0.01F;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        if (!burst) {
            burst = true;
            int amount = fountain ? count / 3 : count;
            for (int i = 0; i < amount; i++) {
                spark(pos, rand(0.0F, VfxMath.TAU), rand(3.0F, 9.0F) * speed, rand(2.0F, 8.0F) * speed);
            }
            VfxParticles.spawn().at(pos.x, pos.y + 0.2, pos.z).life(0.3F).size(1.4F, 0.4F)
                    .color(0xFFFFFFFF, VfxMath.argb(0.0F, color)).look(VfxShape.STAR, VfxLayer.GLOW).fade(0.0F, 0.9F);
        }
        if (fountain && !isStopping()) {
            int amount = emit(count * fade, frame.dt, stream);
            for (int i = 0; i < amount; i++) {
                spark(pos, rand(0.0F, VfxMath.TAU), rand(0.5F, 2.5F) * speed, rand(6.0F, 11.0F) * speed);
            }
            frame.glow.billboard(frame, frame.rx(pos.x), frame.ry(pos.y + 0.15), frame.rz(pos.z), 0.5F, 0.0F, VfxShape.GLOW, 0,
                    VfxMath.argb(0.7F * fade, color));
        }
    }

    private void spark(Vec3 pos, float angle, float horizontal, float vertical) {
        VfxParticles.spawn().at(pos.x, pos.y + 0.15, pos.z)
                .velocity(Math.cos(angle) * horizontal, vertical, Math.sin(angle) * horizontal)
                .life(rand(0.8F, 1.8F)).size(rand(0.02F, 0.045F), 0.008F)
                .color(0xFFFFFFFF, VfxMath.argb(1.0F, VfxMath.shade(color, 0.55F)))
                .look(VfxShape.SPARK, VfxLayer.GLOW).physics(-16.0F, 0.25F).stretch(0.035F).floor(pos.y + 0.01).fade(0.0F, 0.3F).flicker(0.15F);
    }
}
