package me.JAKOP.hinexFeatures.mod.vfx.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import me.JAKOP.hinexFeatures.mod.HinexConstants;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

import me.JAKOP.hinexFeatures.mod.vfx.screen.ScreenEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class HinexPipelines {

    private static final RenderPipeline.Snippet MATRICES_FOG = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.FOG)
            .buildSnippet();

    public static final RenderPipeline DISSOLVE = RenderPipeline.builder(MATRICES_FOG)
            .withLocation(id("pipeline/dissolve"))
            .withVertexShader(id("core/dissolve"))
            .withFragmentShader(id("core/dissolve"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withBindGroupLayout(BindGroupLayouts.LIGHTING)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    private static final RenderPipeline.Snippet VFX_SNIPPET = RenderPipeline.builder(MATRICES_FOG)
            .withVertexShader(id("core/vfx"))
            .withFragmentShader(id("core/vfx"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .buildSnippet();

    private static final BlendFunction GLOW_BLEND = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA);

    public static final RenderPipeline VFX_GLOW = RenderPipeline.builder(VFX_SNIPPET)
            .withLocation(id("pipeline/vfx_glow"))
            .withShaderDefine("HINEX_GLOW")
            .withColorTargetState(new ColorTargetState(GLOW_BLEND))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    public static final RenderPipeline VFX_SOFT = RenderPipeline.builder(VFX_SNIPPET)
            .withLocation(id("pipeline/vfx_soft"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    public static final RenderPipeline VFX_GLOW_XRAY = RenderPipeline.builder(VFX_SNIPPET)
            .withLocation(id("pipeline/vfx_glow_xray"))
            .withShaderDefine("HINEX_GLOW")
            .withColorTargetState(new ColorTargetState(GLOW_BLEND))
            .withDepthStencilState(Optional.empty())
            .build();

    public static final RenderPipeline VFX_SOFT_XRAY = RenderPipeline.builder(VFX_SNIPPET)
            .withLocation(id("pipeline/vfx_soft_xray"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(Optional.empty())
            .build();

    private static final BindGroupLayout SCREEN_LAYOUT = BindGroupLayout.builder()
            .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform("HinexScreen", UniformType.UNIFORM_BUFFER)
            .build();

    private static final Map<String, RenderPipeline> SCREEN = new HashMap<>();

    private HinexPipelines() {
    }

    public static List<RenderPipeline> all() {
        List<RenderPipeline> all = new ArrayList<>(List.of(DISSOLVE, VFX_GLOW, VFX_SOFT, VFX_GLOW_XRAY, VFX_SOFT_XRAY));
        for (String effect : ScreenEffect.KNOWN) all.add(screen(effect));
        all.add(screen("lensing"));
        all.add(screen("ripple"));
        return all;
    }

    public static synchronized RenderPipeline screen(String effect) {
        return SCREEN.computeIfAbsent(effect, name -> RenderPipeline.builder()
                .withLocation(id("pipeline/screen_" + name))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(id("post/screen"))
                .withShaderDefine("EFFECT_" + name.toUpperCase(Locale.ROOT))
                .withBindGroupLayout(SCREEN_LAYOUT)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build());
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(HinexConstants.MOD_ID, path);
    }
}
