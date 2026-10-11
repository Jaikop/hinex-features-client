package me.JAKOP.hinexFeatures.mod.vfx.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class VfxFrame {

    public final Minecraft minecraft;
    public final ClientLevel level;
    public final Vec3 camera;
    public final Vector3f right;
    public final Vector3f up;
    public final Vector3f forward;
    public final double now;
    public final float dt;
    public final float partialTick;

    public final QuadSink glow = new QuadSink();
    public final QuadSink soft = new QuadSink();
    public final QuadSink xray = new QuadSink();

    private final Matrix4f viewProjection;
    public final float aspect;

    public VfxFrame(Minecraft minecraft, ClientLevel level, Vec3 camera, Quaternionf orientation, Matrix4f projection, Matrix4f viewRotation,
                    float aspect, double now, float dt, float partialTick) {
        this.minecraft = minecraft;
        this.level = level;
        this.camera = camera;
        this.right = orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
        this.up = orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        this.forward = orientation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        this.viewProjection = new Matrix4f(projection).mul(viewRotation);
        this.aspect = aspect;
        this.now = now;
        this.dt = dt;
        this.partialTick = partialTick;
    }

    public @Nullable Vector3f project(float x, float y, float z) {
        Vector4f clip = viewProjection.transform(new Vector4f(x, y, z, 1.0F));
        if (clip.w <= 0.05F) return null;
        return new Vector3f(clip.x / clip.w * 0.5F + 0.5F, clip.y / clip.w * 0.5F + 0.5F, clip.w);
    }

    public float projectedRadius(float x, float y, float z, float radius) {
        Vector3f center = project(x, y, z);
        Vector3f edge = project(x + up.x * radius, y + up.y * radius, z + up.z * radius);
        if (center == null || edge == null) return 0.0F;
        float dx = (edge.x - center.x) * aspect;
        float dy = edge.y - center.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float rx(double worldX) {
        return (float) (worldX - camera.x);
    }

    public float ry(double worldY) {
        return (float) (worldY - camera.y);
    }

    public float rz(double worldZ) {
        return (float) (worldZ - camera.z);
    }

    public QuadSink layer(int layer) {
        return switch (layer) {
            case VfxLayer.SOFT -> soft;
            case VfxLayer.XRAY -> xray;
            default -> glow;
        };
    }

    public boolean isEmpty() {
        return glow.quads() == 0 && soft.quads() == 0 && xray.quads() == 0;
    }
}
