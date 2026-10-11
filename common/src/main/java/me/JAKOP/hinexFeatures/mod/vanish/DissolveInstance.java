package me.JAKOP.hinexFeatures.mod.vanish;

import me.JAKOP.hinexFeatures.mod.mixin.vfx.RenderTypeAccessor;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vfx.VfxMath;
import me.JAKOP.hinexFeatures.mod.vfx.render.HinexPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DissolveInstance {

    public final UUID uuid;
    public final VanishStyle style;
    public final boolean appear;
    public final double start;
    public final double duration;
    public final float seed = (float) (Math.random() * 40.0);
    @Nullable EntityRenderState snapshot;
    double x;
    double y;
    double z;
    float height = 1.8F;
    float width = 0.6F;
    final float[] emitters = new float[6];
    boolean burstDone;
    boolean finalDone;

    private final Map<Identifier, RenderType> renderTypes = new HashMap<>();

    DissolveInstance(UUID uuid, VanishStyle style, boolean appear, double start, double duration) {
        this.uuid = uuid;
        this.style = style;
        this.appear = appear;
        this.start = start;
        this.duration = duration;
    }

    public void track(double x, double y, double z, float height, float width) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = Math.max(0.2F, height);
        this.width = Math.max(0.2F, width);
    }

    Vec3 position() {
        return new Vec3(x, y, z);
    }

    public float time(double now) {
        if (duration <= 0.0) return 0.0F;
        return VfxMath.clamp01((float) ((now - start) / duration));
    }

    public float progress(double now) {
        if (style == VanishStyle.VANISHED) return 0.0F;
        float t = time(now);
        float eased = style == VanishStyle.ASH || style == VanishStyle.VOID ? t : VfxMath.easeInOut(t);
        return appear ? 1.0F - eased : eased;
    }

    public boolean finished(double now) {
        return style != VanishStyle.VANISHED && now > start + duration + 0.05;
    }

    public int fallbackColor(double now) {
        if (style == VanishStyle.VANISHED) return ARGB.color(0.35F, ARGB.srgbLerp(0.45F, 0xFFFFFFFF, 0xFF000000 | style.color) & 0xFFFFFF);
        float gone = progress(now);
        float alpha = 1.0F - gone * gone * (3.0F - 2.0F * gone);
        int rgb = ARGB.srgbLerp(Math.min(1.0F, gone * 1.4F) * 0.75F, 0xFFFFFFFF, 0xFF000000 | style.color) & 0xFFFFFF;
        return ARGB.color(alpha, rgb);
    }

    public RenderType renderType(Identifier texture) {
        return renderTypes.computeIfAbsent(texture, location -> RenderTypeAccessor.hinex$create("hinex_dissolve",
                RenderSetup.builder(HinexPipelines.DISSOLVE)
                        .withTexture("Sampler0", location)
                        .useLightmap()
                        .setTextureTransform(new TextureTransform("hinex_dissolve", this::shaderParams))
                        .createRenderSetup()));
    }

    private Matrix4f shaderParams() {
        double now = VfxClock.now();
        Vec3 camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState.pos;
        Matrix4f m = new Matrix4f();
        m.m00((float) (x - camera.x)).m01((float) (y - camera.y)).m02((float) (z - camera.z)).m03(height);
        m.m10(progress(now)).m11(VfxClock.shaderTime()).m12(seed).m13(width);
        m.m20(VfxMath.red(style.color)).m21(VfxMath.green(style.color)).m22(VfxMath.blue(style.color)).m23(1.0F);
        m.m30(VfxMath.red(style.color2)).m31(VfxMath.green(style.color2)).m32(VfxMath.blue(style.color2)).m33(style.id);
        return m;
    }
}
