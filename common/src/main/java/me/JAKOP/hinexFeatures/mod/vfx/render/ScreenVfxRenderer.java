package me.JAKOP.hinexFeatures.mod.vfx.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenPass;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ScreenVfxRenderer {

    private static final int UBO_SIZE = 64;

    private static TextureTarget copy;
    private static final List<MappableRingBuffer> UBOS = new ArrayList<>();
    private static final Set<String> FAILED = new HashSet<>();

    private ScreenVfxRenderer() {
    }

    public static void render(RenderTarget main) {
        List<ScreenPass> passes = VfxClient.screenPasses();
        if (passes.isEmpty()) return;
        GpuTexture mainColor = main.getColorTexture();
        if (mainColor == null) return;
        ensureCopy(main.width, main.height, mainColor.getFormat());

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int index = 0;
        for (ScreenPass pass : passes) {
            if (FAILED.contains(pass.effect())) continue;
            try {
                MappableRingBuffer ubo = ubo(index++);
                try (GpuBufferSlice.MappedView view = ubo.currentBuffer().map(false, true)) {
                    Std140Builder builder = Std140Builder.intoBuffer(view.data());
                    builder.putVec4(VfxMath.clamp01(pass.intensity()), pass.time(), pass.life(), pass.seed());
                    builder.putVec4(VfxMath.red(pass.tint()), VfxMath.green(pass.tint()), VfxMath.blue(pass.tint()), pass.extra());
                    builder.putVec4(pass.pointX(), pass.pointY(), pass.radius(), pass.strength());
                    builder.putVec4(main.width, main.height, main.width / (float) Math.max(1, main.height), pass.speed());
                }
                encoder.copyTextureToTexture(mainColor, copy.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
                try (RenderPass renderPass = encoder.createRenderPass(() -> "HinexFeatures screen " + pass.effect(), main.getColorTextureView(),
                        Optional.empty())) {
                    renderPass.setPipeline(RenderSystem.getCompiledPipeline(HinexPipelines.screen(pass.effect())));
                    renderPass.setUniform("InSampler", copy.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    renderPass.setUniform("HinexScreen", ubo.currentBuffer());
                    renderPass.draw(3, 1, 0, 0);
                }
                ubo.rotate();
            } catch (RuntimeException e) {
                FAILED.add(pass.effect());
                HinexFeaturesClient.LOGGER.error("Экранный эффект {} не работает на этой видеокарте — отключён", pass.effect(), e);
            }
        }
    }

    private static MappableRingBuffer ubo(int index) {
        while (UBOS.size() <= index) {
            int number = UBOS.size();
            UBOS.add(new MappableRingBuffer(() -> "HinexFeatures screen FX " + number, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE));
        }
        return UBOS.get(index);
    }

    private static void ensureCopy(int width, int height, GpuFormat format) {
        if (copy != null && copy.width == width && copy.height == height) return;
        if (copy != null) copy.destroyBuffers();
        copy = new TextureTarget("HinexFeatures screen copy", width, height, format, null);
    }
}
