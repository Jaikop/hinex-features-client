package me.JAKOP.hinexFeatures.mod.vfx.render;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import com.mojang.blaze3d.ProjectionType;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

public final class WorldVfxRenderer {

    private static final int VERTEX_SIZE = DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize();

    private static ByteBufferBuilder bytes;
    private static GpuBuffer vertexBuffer;
    private static long vertexCapacity;
    private static GpuBuffer indexBuffer;
    private static int indexCapacityQuads;
    private static boolean failed;

    private WorldVfxRenderer() {
    }

    private static CameraRenderState lateCamera;

    public static void addPass(FrameGraphBuilder frame, LevelTargetBundle targets, CameraRenderState camera) {
        lateCamera = null;
        if (failed || !VfxClient.hasWorldContent()) return;
        if (ShaderCompat.shadersActive()) {
            lateCamera = camera;
            return;
        }
        FramePass pass = frame.addPass("hinexfeatures_vfx");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> main = targets.main;
        pass.executes(() -> {
            try {
                render(main.get(), camera);
            } catch (RuntimeException e) {
                failed = true;
                HinexFeaturesClient.LOGGER.error("Не удалось нарисовать эффекты в мире — они отключены до перезапуска", e);
            }
        });
    }

    public static void renderLate(RenderTarget target) {
        CameraRenderState camera = lateCamera;
        lateCamera = null;
        if (camera == null || failed) return;
        if (lateProjection == null) lateProjection = new ProjectionMatrixBuffer("hinexfeatures vfx");
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(lateProjection.getBuffer(new Matrix4f(camera.projectionMatrix)), ProjectionType.PERSPECTIVE);
            modelView.identity().mul(camera.viewRotationMatrix);
            late = true;
            render(target, camera);
        } catch (RuntimeException e) {
            failed = true;
            HinexFeaturesClient.LOGGER.error("Не удалось нарисовать эффекты в мире (шейдеры) — они отключены до перезапуска", e);
        } finally {
            late = false;
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static ProjectionMatrixBuffer lateProjection;

    private static boolean late;

    private static void render(RenderTarget target, CameraRenderState camera) {
        float aspect = target.width / (float) Math.max(1, target.height);
        VfxFrame frame = VfxClient.buildWorldFrame(camera, aspect);
        if (frame == null || frame.isEmpty()) return;

        QuadSink[] sinks = {frame.glow, frame.soft, frame.xray};
        RenderPipeline[] pipelines = late
                ? new RenderPipeline[]{HinexPipelines.VFX_GLOW_XRAY, HinexPipelines.VFX_SOFT_XRAY, HinexPipelines.VFX_GLOW_XRAY}
                : new RenderPipeline[]{HinexPipelines.VFX_GLOW, HinexPipelines.VFX_SOFT, HinexPipelines.VFX_GLOW_XRAY};
        int totalQuads = frame.glow.quads() + frame.soft.quads() + frame.xray.quads();

        long needed = (long) totalQuads * 4L * VERTEX_SIZE;
        ensureVertexBuffer(needed);
        ensureIndexBuffer(totalQuads);
        if (bytes == null) bytes = new ByteBufferBuilder(1 << 16);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        long[] offsets = new long[sinks.length];
        long offset = 0L;
        for (int i = 0; i < sinks.length; i++) {
            offsets[i] = offset;
            if (sinks[i].quads() == 0) continue;
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            sinks[i].writeTo(builder);
            try (MeshData mesh = builder.buildOrThrow()) {
                ByteBuffer data = mesh.vertexBuffer();
                int length = data.remaining();
                encoder.writeToBuffer(vertexBuffer.slice(offset, length), data);
                offset += length;
            }
        }

        Matrix4f params = new Matrix4f().m00(VfxClock.shaderTime());
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), params);
        try (RenderPass pass = encoder.createRenderPass(() -> "HinexFeatures VFX", target.getColorTextureView(), Optional.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            for (int i = 0; i < sinks.length; i++) {
                int quads = sinks[i].quads();
                if (quads == 0) continue;
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipelines[i]));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertexBuffer.slice(offsets[i], (long) quads * 4L * VERTEX_SIZE));
                pass.setIndexBuffer(indexBuffer, IndexType.INT);
                pass.drawIndexed(quads * 6, 1, 0, 0, 0);
            }
        }
    }

    private static void ensureVertexBuffer(long size) {
        if (vertexBuffer != null && vertexCapacity >= size) return;
        long capacity = Math.max(1L << 18, Long.highestOneBit(Math.max(1L, size)) << 1);
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "HinexFeatures VFX vertices",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
        vertexCapacity = capacity;
    }

    private static void ensureIndexBuffer(int quads) {
        if (indexBuffer != null && indexCapacityQuads >= quads) return;
        int capacity = Math.max(4096, Integer.highestOneBit(Math.max(1, quads)) << 1);
        ByteBuffer data = MemoryUtil.memAlloc(capacity * 6 * Integer.BYTES);
        try {
            for (int quad = 0; quad < capacity; quad++) {
                int base = quad * 4;
                data.putInt(base).putInt(base + 1).putInt(base + 2).putInt(base + 2).putInt(base + 3).putInt(base);
            }
            data.flip();
            if (indexBuffer != null) indexBuffer.close();
            indexBuffer = RenderSystem.getDevice().createBuffer(() -> "HinexFeatures VFX indices", GpuBuffer.USAGE_INDEX, data);
            indexCapacityQuads = capacity;
        } finally {
            MemoryUtil.memFree(data);
        }
    }
}
