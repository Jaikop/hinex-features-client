package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenPass;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public abstract class WorldEffect {

    protected final VfxParams params;
    protected final RandomSource random = VfxParticles.RANDOM;
    protected final double start;
    protected final double duration;
    private final @Nullable UUID follow;
    protected Vec3 origin;
    private double stopAt = -1.0;

    protected WorldEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        this.params = params;
        this.origin = origin;
        this.follow = follow;
        this.start = start;
        this.duration = duration;
    }

    public Vec3 position(VfxFrame frame) {
        if (follow != null) {
            Entity entity = frame.level.getEntity(follow);
            if (entity != null) {
                origin = entity.getPosition(frame.partialTick);
            }
        }
        return origin;
    }

    protected @Nullable Entity followed(VfxFrame frame) {
        return follow == null ? null : frame.level.getEntity(follow);
    }

    public float age(double now) {
        return (float) (now - start);
    }

    public float life(double now) {
        return duration > 0.0 ? VfxMath.clamp01((float) ((now - start) / duration)) : 0.0F;
    }

    protected float fadeInTime() {
        return 0.4F;
    }

    protected float fadeOutTime() {
        return 0.6F;
    }

    public float fade(double now) {
        float in = VfxMath.clamp01(age(now) / Math.max(0.001F, fadeInTime()));
        float out = 1.0F;
        if (stopAt >= 0.0) {
            out = VfxMath.clamp01(1.0F - (float) (now - stopAt) / fadeOutTime());
        } else if (duration > 0.0) {
            out = VfxMath.clamp01((float) (start + duration - now) / fadeOutTime());
        }
        return Math.min(in, out);
    }

    public boolean isStopping() {
        return stopAt >= 0.0;
    }

    public boolean finished(double now) {
        if (stopAt >= 0.0) return now > stopAt + fadeOutTime();
        return duration > 0.0 && now > start + duration;
    }

    public void stop(double now) {
        if (stopAt < 0.0) {
            stopAt = now;
        }
    }

    public abstract void render(VfxFrame frame, float fade);

    public @Nullable ScreenPass screenPass(VfxFrame frame) {
        return null;
    }

    protected float rand(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    protected float gaussian() {
        return (float) random.nextGaussian();
    }

    protected int emit(float rate, float dt, float[] accumulator) {
        accumulator[0] += rate * dt;
        int count = (int) accumulator[0];
        accumulator[0] -= count;
        return count;
    }
}
