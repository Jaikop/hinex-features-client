package me.JAKOP.hinexFeatures.mod.vfx.world;

import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxFrame;
import me.JAKOP.hinexFeatures.mod.vfx.render.VfxShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class FirefliesEffect extends WorldEffect {

    private final int color;
    private final float radius;
    private final float[][] flies;

    public FirefliesEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0xD7FF7A);
        this.radius = params.number("size", 6.0F);
        int count = Math.max(1, Math.min(300, params.integer("count", 40)));
        this.flies = new float[count][6];
        for (float[] fly : flies) {
            fly[0] = rand(-1.0F, 1.0F);
            fly[1] = rand(0.2F, 2.5F);
            fly[2] = rand(-1.0F, 1.0F);
            fly[3] = rand(0.0F, 100.0F);
            fly[4] = rand(0.6F, 1.4F);
            fly[5] = rand(0.0F, VfxMath.TAU);
        }
    }

    @Override
    protected float fadeInTime() {
        return 1.5F;
    }

    @Override
    protected float fadeOutTime() {
        return 1.5F;
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        float t = age(frame.now);
        for (float[] fly : flies) {
            float speed = fly[4];
            float phase = fly[3];
            double fx = pos.x + (fly[0] + 0.35 * Math.sin(t * 0.37 * speed + phase)) * radius * 0.8;
            double fy = pos.y + fly[1] + 0.5 * Math.sin(t * 0.53 * speed + phase * 1.7);
            double fz = pos.z + (fly[2] + 0.35 * Math.cos(t * 0.31 * speed + phase * 0.6)) * radius * 0.8;
            float blink = (float) Math.max(0.0, Math.sin(t * 1.7 * speed + fly[5]));
            blink = blink * blink;
            if (blink < 0.02F) continue;
            float x = frame.rx(fx), y = frame.ry(fy), z = frame.rz(fz);
            frame.glow.billboard(frame, x, y, z, 0.22F, 0.0F, VfxShape.GLOW, 0, VfxMath.argb(0.45F * blink * fade, color));
            frame.glow.billboard(frame, x, y, z, 0.05F, 0.0F, VfxShape.DOT, 0, VfxMath.argb(blink * fade, VfxMath.shade(color, 1.3F)));
        }
    }
}
