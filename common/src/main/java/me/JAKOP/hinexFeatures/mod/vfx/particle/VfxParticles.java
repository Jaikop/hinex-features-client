package me.JAKOP.hinexFeatures.mod.vfx.particle;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.render.QuadSink;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class VfxParticles {

    public static final int LIMIT = 6000;

    private static final List<VfxParticle> PARTICLES = new ArrayList<>(1024);
    public static final RandomSource RANDOM = RandomSource.create();

    private VfxParticles() {
    }

    public static VfxParticle spawn() {
        VfxParticle particle = new VfxParticle();
        particle.seed = RANDOM.nextInt(1024);
        particle.rotation = RANDOM.nextFloat() * VfxMath.TAU;
        if (PARTICLES.size() >= LIMIT) {
            PARTICLES.remove(0);
        }
        PARTICLES.add(particle);
        return particle;
    }

    public static int count() {
        return PARTICLES.size();
    }

    public static void clear() {
        PARTICLES.clear();
    }

    public static void forEach(Object owner, java.util.function.Consumer<VfxParticle> action) {
        for (VfxParticle particle : PARTICLES) {
            if (particle.owner == owner) action.accept(particle);
        }
    }

    public static void update(VfxFrame frame) {
        float dt = frame.dt;
        for (int i = PARTICLES.size() - 1; i >= 0; i--) {
            VfxParticle p = PARTICLES.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                PARTICLES.set(i, PARTICLES.get(PARTICLES.size() - 1));
                PARTICLES.remove(PARTICLES.size() - 1);
                continue;
            }
            step(p, dt);
            draw(frame, p);
        }
    }

    private static void step(VfxParticle p, float dt) {
        if (p.attract != 0.0F) {
            float dx = (float) (p.attractX - p.x), dy = (float) (p.attractY - p.y), dz = (float) (p.attractZ - p.z);
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.05F;
            float pull = p.attract * dt / dist;
            p.vx += dx * pull;
            p.vy += dy * pull;
            p.vz += dz * pull;
            if (p.swirl != 0.0F) {
                float horizontal = (float) Math.sqrt(dx * dx + dz * dz) + 0.05F;
                p.vx += -dz / horizontal * p.swirl * dt;
                p.vz += dx / horizontal * p.swirl * dt;
            }
        }
        p.vy += p.gravity * dt;
        if (p.drag > 0.0F) {
            float keep = (float) Math.pow(Math.max(0.0F, 1.0F - p.drag), dt);
            p.vx *= keep;
            p.vy *= keep;
            p.vz *= keep;
        }
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.z += p.vz * dt;
        if (!Double.isNaN(p.floorY) && p.y < p.floorY) {
            p.y = p.floorY;
            p.vy = Math.abs(p.vy) * 0.35F;
            p.vx *= 0.6F;
            p.vz *= 0.6F;
        }
        p.rotation += p.spin * dt;
    }

    private static void draw(VfxFrame frame, VfxParticle p) {
        float t = p.age / p.life;
        float alpha = VfxMath.fade(t, p.fadeIn, p.fadeOut);
        if (p.flicker > 0.0F) {
            alpha *= 1.0F - p.flicker * RANDOM.nextFloat();
        }
        int color = p.endColor == 0 ? p.color : VfxMath.lerpColor(p.color, p.endColor, t);
        color = VfxMath.multiplyAlpha(color, alpha);
        if ((color >>> 24) < 2) {
            return;
        }
        float size = p.endSize < 0.0F ? p.size : VfxMath.lerp(p.size, p.endSize, t);
        float x = frame.rx(p.x), y = frame.ry(p.y), z = frame.rz(p.z);
        QuadSink sink = frame.layer(p.layer);
        if (p.flat) {
            sink.flat(x, y, z, size, p.rotation, p.shape, p.seed, color);
        } else if (p.stretch > 0.0F) {
            float tx = x - p.vx * p.stretch, ty = y - p.vy * p.stretch, tz = z - p.vz * p.stretch;
            sink.ribbon(tx, ty, tz, x, y, z, size, p.shape, 0.0F, 1.0F, color);
        } else {
            sink.billboard(frame, x, y, z, size, p.rotation, p.shape, p.seed, color);
        }
    }
}
