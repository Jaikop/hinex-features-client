package me.JAKOP.hinexFeatures.mod.vfx.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Vector3f;

import java.util.Arrays;

public final class QuadSink {

    private static final int FLOATS_PER_VERTEX = 5;

    private float[] data = new float[4096];
    private int[] colors = new int[1024];
    private int vertices;

    public int quads() {
        return vertices / 4;
    }

    public void clear() {
        vertices = 0;
    }

    public void writeTo(BufferBuilder builder) {
        for (int i = 0; i < vertices; i++) {
            int o = i * FLOATS_PER_VERTEX;
            builder.addVertex(data[o], data[o + 1], data[o + 2]).setUv(data[o + 3], data[o + 4]).setColor(colors[i]);
        }
    }

    private void vertex(float x, float y, float z, float u, float v, int color) {
        if ((vertices + 1) * FLOATS_PER_VERTEX > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        if (vertices + 1 > colors.length) {
            colors = Arrays.copyOf(colors, colors.length * 2);
        }
        int o = vertices * FLOATS_PER_VERTEX;
        data[o] = x;
        data[o + 1] = y;
        data[o + 2] = z;
        data[o + 3] = u;
        data[o + 4] = v;
        colors[vertices] = color;
        vertices++;
    }

    private static float u(int shape, float local) {
        return shape * 2.0F + local;
    }

    private static float v(int seed, float local) {
        return (seed & 1023) * 2.0F + local;
    }

    public void quad(float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3, int shape, int seed, int color) {
        vertex(x0, y0, z0, u(shape, 0.0F), v(seed, 0.0F), color);
        vertex(x1, y1, z1, u(shape, 1.0F), v(seed, 0.0F), color);
        vertex(x2, y2, z2, u(shape, 1.0F), v(seed, 1.0F), color);
        vertex(x3, y3, z3, u(shape, 0.0F), v(seed, 1.0F), color);
    }

    public void billboard(VfxFrame frame, float x, float y, float z, float halfSize, float rotation, int shape, int seed, int color) {
        float cos = (float) Math.cos(rotation) * halfSize;
        float sin = (float) Math.sin(rotation) * halfSize;
        Vector3f r = frame.right;
        Vector3f u = frame.up;
        float ax = r.x * cos + u.x * sin, ay = r.y * cos + u.y * sin, az = r.z * cos + u.z * sin;
        float bx = -r.x * sin + u.x * cos, by = -r.y * sin + u.y * cos, bz = -r.z * sin + u.z * cos;
        quad(x - ax - bx, y - ay - by, z - az - bz,
                x + ax - bx, y + ay - by, z + az - bz,
                x + ax + bx, y + ay + by, z + az + bz,
                x - ax + bx, y - ay + by, z - az + bz, shape, seed, color);
    }

    public void ribbon(float x0, float y0, float z0, float x1, float y1, float z1, float halfWidth, int shape, float v0, float v1, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float cx = -(x0 + x1) * 0.5F, cy = -(y0 + y1) * 0.5F, cz = -(z0 + z1) * 0.5F;
        float sx = dy * cz - dz * cy, sy = dz * cx - dx * cz, sz = dx * cy - dy * cx;
        float len = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1.0E-5F) {
            return;
        }
        float k = halfWidth / len;
        sx *= k;
        sy *= k;
        sz *= k;
        float su0 = shape * 2.0F, su1 = shape * 2.0F + 1.0F;
        vertex(x0 - sx, y0 - sy, z0 - sz, su0, v0, color);
        vertex(x0 + sx, y0 + sy, z0 + sz, su1, v0, color);
        vertex(x1 + sx, y1 + sy, z1 + sz, su1, v1, color);
        vertex(x1 - sx, y1 - sy, z1 - sz, su0, v1, color);
    }

    public void flat(float x, float y, float z, float halfSize, float rotation, int shape, int seed, int color) {
        float cos = (float) Math.cos(rotation) * halfSize;
        float sin = (float) Math.sin(rotation) * halfSize;
        quad(x - cos + sin, y, z - sin - cos,
                x + cos + sin, y, z + sin - cos,
                x + cos - sin, y, z + sin + cos,
                x - cos - sin, y, z - sin + cos, shape, seed, color);
    }

    public void plane(float x, float y, float z, Vector3f axisA, Vector3f axisB, int shape, int seed, int color) {
        quad(x - axisA.x - axisB.x, y - axisA.y - axisB.y, z - axisA.z - axisB.z,
                x + axisA.x - axisB.x, y + axisA.y - axisB.y, z + axisA.z - axisB.z,
                x + axisA.x + axisB.x, y + axisA.y + axisB.y, z + axisA.z + axisB.z,
                x - axisA.x + axisB.x, y - axisA.y + axisB.y, z - axisA.z + axisB.z, shape, seed, color);
    }

    public void upright(VfxFrame frame, float x, float y, float z, float halfWidth, float halfHeight, int shape, int seed, int color) {
        float fx = -x, fz = -z;
        float len = (float) Math.sqrt(fx * fx + fz * fz);
        float sx = len < 1.0E-4F ? 1.0F : -fz / len;
        float sz = len < 1.0E-4F ? 0.0F : fx / len;
        quad(x - sx * halfWidth, y - halfHeight, z - sz * halfWidth,
                x + sx * halfWidth, y - halfHeight, z + sz * halfWidth,
                x + sx * halfWidth, y + halfHeight, z + sz * halfWidth,
                x - sx * halfWidth, y + halfHeight, z - sz * halfWidth, shape, seed, color);
    }

    public void box(float x0, float y0, float z0, float x1, float y1, float z1, int shape, int color) {
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, shape, 0, color);
        quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, shape, 1, color);
        quad(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, shape, 2, color);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, shape, 3, color);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, shape, 4, color);
        quad(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, shape, 5, color);
    }
}
