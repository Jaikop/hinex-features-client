package me.JAKOP.hinexFeatures.mod.deathgame;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.Random;

public final class HeadFace {

    public static final Identifier SKULL = Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

    private final Identifier skin;
    private final float[] thresholds = new float[64];

    public HeadFace(@Nullable Identifier skin, long seed) {
        this.skin = skin;
        Random random = new Random(seed ^ 0x5EEDL);
        for (int j = 0; j < 8; j++) {
            for (int i = 0; i < 8; i++) {
                float row = (7 - j) / 7.0F;
                thresholds[i + j * 8] = 0.04F + (row * 0.72F + random.nextFloat() * 0.2F);
            }
        }
    }

    public static HeadFace forLocalPlayer(long seed) {
        Identifier texture = null;
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerSkin skin = player.getSkin();
            texture = skin.body().texturePath();
        }
        return new HeadFace(texture, seed);
    }

    public @Nullable Identifier skin() {
        return skin;
    }

    public float reveal(int i, int j, float progress) {
        return Math.max(0.0F, Math.min(1.0F, (progress - thresholds[i + j * 8]) / 0.1F));
    }

    public void draw(GuiGraphicsExtractor g, int x, int y, int size, float progress, float alpha, float glow) {
        if (alpha <= 0.01F || size <= 0) return;
        for (int j = 0; j < 8; j++) {
            int y0 = y + Math.round(j * size / 8.0F);
            int y1 = y + Math.round((j + 1) * size / 8.0F);
            for (int i = 0; i < 8; i++) {
                int x0 = x + Math.round(i * size / 8.0F);
                int x1 = x + Math.round((i + 1) * size / 8.0F);
                int w = x1 - x0;
                int h = y1 - y0;
                if (w <= 0 || h <= 0) continue;
                float a = skin == null ? 0.0F : reveal(i, j, progress);
                if (a < 1.0F) {
                    g.blit(RenderPipelines.GUI_TEXTURED, SKULL, x0, y0, 8 + i, 8 + j, w, h, 1, 1, 64, 32,
                            ARGB.white(alpha));
                }
                if (a > 0.0F) {
                    int color = ARGB.white(alpha * a);
                    g.blit(RenderPipelines.GUI_TEXTURED, skin, x0, y0, 8 + i, 8 + j, w, h, 1, 1, 64, 64, color);
                    g.blit(RenderPipelines.GUI_TEXTURED, skin, x0, y0, 40 + i, 8 + j, w, h, 1, 1, 64, 64, color);
                }
                float edge = a * (1.0F - a) * 4.0F * glow;
                if (edge > 0.02F) g.fill(x0, y0, x1, y1, ARGB.white(Math.min(1.0F, edge * 0.8F) * alpha));
            }
        }
    }

    public void drawTexel(GuiGraphicsExtractor g, int i, int j, boolean face, int x, int y, int size, float alpha) {
        if (face && skin != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8 + i, 8 + j, size, size, 1, 1, 64, 64, ARGB.white(alpha));
            g.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40 + i, 8 + j, size, size, 1, 1, 64, 64, ARGB.white(alpha));
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, SKULL, x, y, 8 + i, 8 + j, size, size, 1, 1, 64, 32, ARGB.white(alpha));
        }
    }
}
