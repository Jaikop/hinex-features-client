package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.camera.CameraFx;
import me.JAKOP.hinexFeatures.mod.vfx.particle.VfxParticles;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxLayer;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LightningEffect extends WorldEffect {

    private record Segment(Vec3 from, Vec3 to, float width) {
    }

    private final int color;
    private final float height;
    private final int strikes;
    private final List<Segment> bolt = new ArrayList<>();
    private int generation = -1;
    private boolean impacted;

    public LightningEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration <= 0.0 ? 0.9 : duration);
        this.color = params.color("color", 0xBFE3FF);
        this.height = params.number("height", 40.0F);
        this.strikes = Math.max(1, Math.min(6, params.integer("count", 3)));
    }

    @Override
    protected float fadeInTime() {
        return 0.01F;
    }

    @Override
    protected float fadeOutTime() {
        return 0.35F;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float age = age(frame.now);
        float strikeLength = 0.12F;
        int strike = Math.min(strikes - 1, (int) (age / (strikeLength * 1.6F)));
        if (strike != generation) {
            generation = strike;
            generate(pos);
        }
        float local = age - strike * strikeLength * 1.6F;
        float flash = local < strikeLength ? 1.0F : VfxMath.clamp01(1.0F - (local - strikeLength) / 0.25F);
        flash = Math.max(flash, strike == strikes - 1 ? fade : 0.0F);
        if (flash <= 0.01F) return;

        for (Segment segment : bolt) {
            float x0 = frame.rx(segment.from.x), y0 = frame.ry(segment.from.y), z0 = frame.rz(segment.from.z);
            float x1 = frame.rx(segment.to.x), y1 = frame.ry(segment.to.y), z1 = frame.rz(segment.to.z);
            frame.glow.ribbon(x0, y0, z0, x1, y1, z1, segment.width * 5.0F, VfxShape.BOLT, 0.0F, 1.0F, VfxMath.argb(0.35F * flash, color));
            frame.glow.ribbon(x0, y0, z0, x1, y1, z1, segment.width, VfxShape.BOLT, 0.0F, 1.0F, VfxMath.argb(flash, 0xFFFFFF));
        }
        float x = frame.rx(pos.x), y = frame.ry(pos.y), z = frame.rz(pos.z);
        frame.glow.flat(x, y + 0.04F, z, 3.0F, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.8F * flash, color));

        if (!impacted) {
            impacted = true;
            for (int i = 0; i < 50; i++) {
                float angle = rand(0.0F, VfxMath.TAU);
                float speed = rand(2.0F, 8.0F);
                VfxParticles.spawn().at(pos.x, pos.y + 0.1, pos.z).velocity(Math.cos(angle) * speed, rand(2.0F, 7.0F), Math.sin(angle) * speed)
                        .life(rand(0.4F, 1.0F)).size(0.03F, 0.01F).color(0xFFFFFFFF, VfxMath.argb(1.0F, color))
                        .look(VfxShape.SPARK, VfxLayer.GLOW).physics(-15.0F, 0.4F).stretch(0.03F).floor(pos.y + 0.02);
            }
            double distance = frame.camera.distanceTo(pos);
            if (distance < 32.0) {
                CameraFx.impulse((float) (1.8 * (1.0 - distance / 32.0)), 0.5);
            }
            if (params.bool("sound", false)) {
                frame.level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                        (float) Math.max(0.3, 3.0 - distance / 20.0), 0.9F + random.nextFloat() * 0.2F, false);
                frame.level.playLocalSound(pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.0F, 0.8F, false);
            }
        }
    }

    private void generate(Vec3 target) {
        bolt.clear();
        Vec3 top = target.add(gaussian() * 4.0, height, gaussian() * 4.0);
        branch(top, target, 0.18F, 6, true);
    }

    private void branch(Vec3 from, Vec3 to, float width, int depth, boolean main) {
        if (depth == 0) {
            bolt.add(new Segment(from, to, width));
            return;
        }
        double length = from.distanceTo(to);
        Vec3 mid = from.add(to).scale(0.5).add(gaussian() * length * 0.12, gaussian() * length * 0.05, gaussian() * length * 0.12);
        branch(from, mid, width, depth - 1, main);
        branch(mid, to, width, depth - 1, main);
        if (main && depth >= 3 && random.nextFloat() < 0.35F) {
            Vec3 direction = to.subtract(from);
            Vec3 end = mid.add(direction.x * 0.5 + gaussian() * length * 0.4, direction.y * 0.45, direction.z * 0.5 + gaussian() * length * 0.4);
            branch(mid, end, width * 0.45F, depth - 2, false);
        }
    }
}
