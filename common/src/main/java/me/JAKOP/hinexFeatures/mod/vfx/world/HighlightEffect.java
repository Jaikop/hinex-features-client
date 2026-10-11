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

public final class HighlightEffect extends WorldEffect {

    private final int color;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final boolean xray;
    private final float speed;
    private final float[] motes = new float[1];

    public HighlightEffect(VfxParams params, Vec3 origin, @Nullable UUID follow, double start, double duration) {
        super(params, origin, follow, start, duration);
        this.color = params.color("color", 0xFFD24A);
        int size = Math.max(1, params.integer("size", 1));
        this.sizeX = Math.max(1, params.integer("sx", size));
        this.sizeY = Math.max(1, params.integer("sy", size));
        this.sizeZ = Math.max(1, params.integer("sz", size));
        this.xray = params.bool("xray", false);
        this.speed = params.number("speed", 1.0F);
    }

    @Override
    public void render(VfxFrame frame, float fade) {
        Vec3 pos = position(frame);
        double bx = Math.floor(pos.x), by = Math.floor(pos.y), bz = Math.floor(pos.z);
        float age = age(frame.now);
        float pulse = 0.55F + 0.45F * (float) Math.sin(age * 4.0 * speed);
        float grow = 0.004F + 0.03F * pulse;
        float x0 = frame.rx(bx) - grow, y0 = frame.ry(by) - grow, z0 = frame.rz(bz) - grow;
        float x1 = frame.rx(bx + sizeX) + grow, y1 = frame.ry(by + sizeY) + grow, z1 = frame.rz(bz + sizeZ) + grow;

        QuadSink sink = xray ? frame.xray : frame.glow;
        sink.box(x0, y0, z0, x1, y1, z1, VfxShape.FRAME, VfxMath.argb((0.45F + 0.5F * pulse) * fade, color));
        float star = 0.12F + 0.08F * pulse;
        int starColor = VfxMath.argb(0.9F * fade * pulse, VfxMath.shade(color, 1.3F));
        for (int corner = 0; corner < 8; corner++) {
            float cx = (corner & 1) == 0 ? x0 : x1;
            float cy = (corner & 2) == 0 ? y0 : y1;
            float cz = (corner & 4) == 0 ? z0 : z1;
            sink.billboard(frame, cx, cy, cz, star, age * 1.5F, VfxShape.STAR, corner, starColor);
        }
        int count = emit(6.0F * sizeX * sizeZ * fade, frame.dt, motes);
        for (int i = 0; i < count; i++) {
            VfxParticles.spawn().at(bx + rand(0.0F, sizeX), by + sizeY, bz + rand(0.0F, sizeZ))
                    .velocity(0.0, rand(0.3F, 0.9F), 0.0).life(rand(0.8F, 1.6F)).size(rand(0.025F, 0.05F), 0.0F)
                    .color(VfxMath.argb(1.0F, color), VfxMath.argb(0.0F, 0xFFFFFF))
                    .look(VfxShape.GLOW, xray ? VfxLayer.XRAY : VfxLayer.GLOW).flicker(0.35F);
        }
    }
}
