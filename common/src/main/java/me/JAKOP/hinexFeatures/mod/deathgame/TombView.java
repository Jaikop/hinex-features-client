package me.JAKOP.hinexFeatures.mod.deathgame;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class TombView implements TombGame.Listener {

    private static final int BG = 0xFF030304;
    private static final int OPEN = 0xFF08080B;
    private static final int WALL = 0xFF111116;
    private static final int WALL_MARK = 0xFF16161C;
    private static final int EDGE = 0xFFE9EBF3;
    private static final int DOT = 0xFFD9DCEA;
    private static final int SPIKE = 0xFFA9ACB8;
    private static final int SPIKE_TIP = 0xFFD9DCE6;
    private static final int TRAP = 0xFFE0412B;
    private static final int TRAP_TIP = 0xFFFFC857;
    private static final int TIDE_TOP = 0xFFE0182F;
    private static final int TIDE_BODY = 0xFF2B040B;
    private static final int TIDE_DEEP = 0xFF080102;

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        final float maxLife;
        final int color;
        final float size;
        final int texel;
        final boolean face;
        final float drag;

        Particle(float x, float y, float vx, float vy, float life, int color, float size, int texel, boolean face, float drag) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.color = color;
            this.size = size;
            this.texel = texel;
            this.face = face;
            this.drag = drag;
        }
    }

    private final TombGame game;
    private final HeadFace head;
    private final Random random = new Random();
    private final List<Particle> particles = new ArrayList<>();
    private final float[] trailX = new float[10];
    private final float[] trailY = new float[10];
    private int trailCount;

    private float camY;
    private boolean cameraSet;
    private float shake;
    private float squash;
    private int squashDir = -1;
    private float shownProgress;
    private float flash;
    private float headAlpha = 1.0F;
    private float headLift;
    private float time;
    private float emberTimer;
    private float ashTimer;
    private boolean shattered;
    private float introPan = -1.0F;

    private int tile;
    private int left;

    public TombView(TombGame game, HeadFace head) {
        this.game = game;
        this.head = head;
    }

    @Override
    public void onMove(int dir) {
        squash = 0.0F;
        trailCount = 0;
    }

    @Override
    public void onImpact(int dir, int x, int y, boolean bump) {
        if (bump) {
            shake = Math.max(shake, 0.06F);
            return;
        }
        squash = 1.0F;
        squashDir = dir;
        shake = Math.max(shake, 0.16F);
        float wx = x + 0.5F + TombLevel.DX[dir] * 0.45F;
        float wy = y + 0.5F + TombLevel.DY[dir] * 0.45F;
        for (int i = 0; i < 9; i++) {
            float along = (random.nextFloat() - 0.5F) * 2.4F;
            float away = -(0.6F + random.nextFloat() * 1.6F);
            float vx = TombLevel.DX[dir] != 0 ? TombLevel.DX[dir] * away : along;
            float vy = TombLevel.DY[dir] != 0 ? TombLevel.DY[dir] * away : along;
            int shade = 150 + random.nextInt(90);
            particles.add(new Particle(wx, wy, vx, vy, 0.35F + random.nextFloat() * 0.35F,
                    ARGB.color(255, shade, shade, shade + 10), 0.06F + random.nextFloat() * 0.05F, -1, false, 5.0F));
        }
        DeathGameSounds.impact();
    }

    @Override
    public void onDot(int x, int y) {
        for (int i = 0; i < 5; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2.0F;
            float speed = 0.8F + random.nextFloat() * 1.4F;
            particles.add(new Particle(x + 0.5F, y + 0.5F, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                    0.3F + random.nextFloat() * 0.25F, 0xFFFFFFFF, 0.05F, -1, false, 4.0F));
        }
        DeathGameSounds.dot(game.collected());
    }

    @Override
    public void onDeath(TombGame.Death death) {
        shake = 0.5F;
        if (death == TombGame.Death.TIDE) return;
        shatter();
    }

    @Override
    public void onWin() {
        flash = 1.0F;
        for (int i = 0; i < 40; i++) {
            float angle = random.nextFloat() * (float) Math.PI * 2.0F;
            float speed = 1.0F + random.nextFloat() * 4.0F;
            particles.add(new Particle(game.x() + 0.5F, game.y() + 0.5F, (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed + 2.0F, 0.6F + random.nextFloat() * 0.8F, 0xFFFFFFFF,
                    0.05F + random.nextFloat() * 0.07F, -1, false, 1.5F));
        }
    }

    private void shatter() {
        if (shattered) return;
        shattered = true;
        headAlpha = 0.0F;
        float progress = shownProgress;
        for (int j = 0; j < 8; j++) {
            for (int i = 0; i < 8; i++) {
                float px = game.x() + 0.05F + (i + 0.5F) / 8.0F * 0.9F;
                float py = game.y() + 0.95F - (j + 0.5F) / 8.0F * 0.9F;
                float dx = px - (game.x() + 0.5F);
                float dy = py - (game.y() + 0.5F);
                float speed = 2.0F + random.nextFloat() * 3.5F;
                boolean face = head.reveal(i, j, progress) > 0.5F;
                particles.add(new Particle(px, py, dx * speed * 2.0F + (random.nextFloat() - 0.5F),
                        dy * speed * 2.0F + (random.nextFloat() - 0.2F) * 2.0F, 1.2F + random.nextFloat() * 0.8F,
                        0xFFFFFFFF, 0.9F / 8.0F, i + j * 8, face, 1.2F));
            }
        }
    }

    public void setIntroPan(float pan) {
        introPan = pan;
    }

    public float progressShown() {
        return shownProgress;
    }

    public void update(float dt) {
        time += dt;
        shake = Math.max(0.0F, shake - dt * 1.6F);
        squash = Math.max(0.0F, squash - dt * 6.0F);
        flash = Math.max(0.0F, flash - dt * 1.2F);

        float target = game.progress();
        if (game.state() == TombGame.State.DEAD) target = 0.0F;
        float speed = game.state() == TombGame.State.DEAD ? 0.7F : game.state() == TombGame.State.WON ? 0.9F : 0.35F;
        if (Math.abs(target - shownProgress) <= speed * dt) shownProgress = target;
        else shownProgress += Math.signum(target - shownProgress) * speed * dt;

        if (game.state() == TombGame.State.WON) headLift += dt * (2.0F + headLift * 4.0F);
        if (game.state() == TombGame.State.DEAD && game.death() == TombGame.Death.TIDE) {
            headAlpha = Math.max(0.0F, headAlpha - dt * 1.4F);
        }

        if (game.moving()) {
            System.arraycopy(trailX, 0, trailX, 1, trailX.length - 1);
            System.arraycopy(trailY, 0, trailY, 1, trailY.length - 1);
            trailX[0] = game.x();
            trailY[0] = game.y();
            trailCount = Math.min(trailX.length, trailCount + 1);
        } else if (trailCount > 0 && time % 0.02F < dt) {
            trailCount--;
        }

        emberTimer += dt;
        while (emberTimer > 0.05F) {
            emberTimer -= 0.05F;
            float x = random.nextFloat() * game.level.width;
            int red = 180 + random.nextInt(75);
            particles.add(new Particle(x, game.tide() + 0.1F, (random.nextFloat() - 0.5F) * 0.5F, 0.6F + random.nextFloat() * 1.3F,
                    0.8F + random.nextFloat() * 1.2F, ARGB.color(255, red, 20 + random.nextInt(40), 25), 0.05F + random.nextFloat() * 0.04F,
                    -1, false, 0.3F));
        }
        ashTimer += dt;
        while (ashTimer > 0.12F) {
            ashTimer -= 0.12F;
            float x = random.nextFloat() * game.level.width;
            float y = camY - 9.0F + random.nextFloat() * 18.0F;
            int shade = 90 + random.nextInt(60);
            particles.add(new Particle(x, y, (random.nextFloat() - 0.5F) * 0.3F, 0.15F + random.nextFloat() * 0.35F,
                    2.0F + random.nextFloat() * 2.0F, ARGB.color(255, shade, shade, shade), 0.03F + random.nextFloat() * 0.03F,
                    -1, false, 0.0F));
        }

        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.life -= dt;
            if (p.life <= 0.0F) {
                iterator.remove();
                continue;
            }
            float damping = (float) Math.exp(-p.drag * dt);
            p.vx *= damping;
            p.vy *= damping;
            if (p.texel >= 0) p.vy -= 6.0F * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
        if (particles.size() > 900) particles.subList(0, particles.size() - 900).clear();

        float lookY = game.y() + 0.5F + 1.6F;
        if (introPan >= 0.0F) {
            float from = game.level.exitY + 0.5F;
            float eased = introPan * introPan * (3.0F - 2.0F * introPan);
            lookY = from + (lookY - from) * eased;
        }
        if (!cameraSet) {
            camY = lookY;
            cameraSet = true;
        } else {
            float follow = introPan >= 0.0F ? 1.0F : 1.0F - (float) Math.exp(-dt * 7.0F);
            camY += (lookY - camY) * follow;
        }
    }

    public void draw(GuiGraphicsExtractor g, Font font, int width, int height, float levelAlpha) {
        TombLevel level = game.level;
        tile = Math.max(8, Math.min(40, Math.min((width - 60) / level.width, height / 14)));
        int t = tile;
        left = (width - level.width * t) / 2;
        float anchor = height * 0.6F;
        float baseY = anchor + camY * t;

        g.fill(0, 0, width, height, BG);

        float shakeX = shake > 0.0F ? (random.nextFloat() - 0.5F) * shake * t * 0.6F : 0.0F;
        float shakeY = shake > 0.0F ? (random.nextFloat() - 0.5F) * shake * t * 0.6F : 0.0F;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(left + shakeX, baseY + shakeY);

        int yMin = (int) Math.floor(camY - (height - anchor) / t) - 2;
        int yMax = (int) Math.ceil(camY + anchor / t) + 2;
        for (int y = Math.max(0, yMin); y <= Math.min(level.height - 1, yMax); y++) {
            for (int x = 0; x < level.width; x++) drawCell(g, level, x, y, t);
        }
        for (int y = Math.max(0, yMin); y <= Math.min(level.height - 1, yMax); y++) {
            for (int x = 0; x < level.width; x++) {
                if (level.open(x, y)) drawEdges(g, level, x, y, t);
            }
        }
        for (int y = Math.max(0, yMin); y <= Math.min(level.height - 1, yMax); y++) {
            for (int x = 0; x < level.width; x++) {
                if (level.open(x, y)) drawContents(g, level, x, y, t);
            }
        }
        drawExit(g, level, t);
        drawLighting(g, level, t, yMin, yMax);
        drawTrail(g, t);
        drawHead(g, t);
        drawParticles(g, t, false);
        drawTide(g, level, t, width, height, baseY);
        drawParticles(g, t, true);
        pose.popMatrix();

        drawSides(g, width, height);
        drawSurfaceLight(g, width, Math.round(baseY + shakeY - level.height * t), t);
        if (levelAlpha < 1.0F) g.fill(0, 0, width, height, ARGB.black(1.0F - levelAlpha));
        drawDanger(g, width, height);
        if (flash > 0.0F) g.fill(0, 0, width, height, ARGB.white(flash * 0.85F));
    }

    private static int sy(float y, int t) {
        return Math.round(-y * t);
    }

    private void drawCell(GuiGraphicsExtractor g, TombLevel level, int x, int y, int t) {
        int x0 = x * t;
        int y0 = sy(y + 1, t);
        if (level.open(x, y)) {
            g.fill(x0, y0, x0 + t, y0 + t, OPEN);
            return;
        }
        g.fill(x0, y0, x0 + t, y0 + t, WALL);
        int hash = (x * 73856093) ^ (y * 19349663);
        if ((hash & 7) == 0) {
            int mx = x0 + t / 4 + (hash >>> 4 & 3) * t / 8;
            int my = y0 + t / 3 + (hash >>> 6 & 3) * t / 10;
            g.fill(mx, my, mx + Math.max(1, t / 3), my + 1, WALL_MARK);
            g.fill(mx + t / 6, my + 1, mx + t / 6 + 1, my + Math.max(2, t / 5), WALL_MARK);
        }
    }

    private void drawEdges(GuiGraphicsExtractor g, TombLevel level, int x, int y, int t) {
        int e = Math.max(2, t / 6);
        int glow = Math.max(2, t / 4);
        int x0 = x * t;
        int y0 = sy(y + 1, t);
        int x1 = x0 + t;
        int y1 = y0 + t;
        int glowColor = ARGB.color(0.10F, EDGE & 0xFFFFFF);
        if (!level.open(x, y + 1)) {
            g.fill(x0, y0 - e, x1, y0, EDGE);
            g.fill(x0, y0, x1, y0 + glow, glowColor);
        }
        if (!level.open(x, y - 1)) {
            g.fill(x0, y1, x1, y1 + e, EDGE);
            g.fill(x0, y1 - glow, x1, y1, glowColor);
        }
        if (!level.open(x - 1, y)) {
            g.fill(x0 - e, y0, x0, y1, EDGE);
            g.fill(x0, y0, x0 + glow, y1, glowColor);
        }
        if (!level.open(x + 1, y)) {
            g.fill(x1, y0, x1 + e, y1, EDGE);
            g.fill(x1 - glow, y0, x1, y1, glowColor);
        }
        if (!level.open(x, y + 1) && !level.open(x - 1, y)) g.fill(x0 - e, y0 - e, x0, y0, EDGE);
        if (!level.open(x, y + 1) && !level.open(x + 1, y)) g.fill(x1, y0 - e, x1 + e, y0, EDGE);
        if (!level.open(x, y - 1) && !level.open(x - 1, y)) g.fill(x0 - e, y1, x0, y1 + e, EDGE);
        if (!level.open(x, y - 1) && !level.open(x + 1, y)) g.fill(x1, y1, x1 + e, y1 + e, EDGE);
    }

    private void drawContents(GuiGraphicsExtractor g, TombLevel level, int x, int y, int t) {
        int x0 = x * t;
        int y0 = sy(y + 1, t);
        if (level.dot(x, y) && !game.taken(x, y)) {
            int s = Math.max(2, t / 7);
            float twinkle = 0.65F + 0.35F * (float) Math.sin(time * 3.0F + x * 1.7F + y * 0.9F);
            int cx = x0 + t / 2 - s / 2;
            int cy = y0 + t / 2 - s / 2;
            g.fill(cx - 1, cy - 1, cx + s + 1, cy + s + 1, ARGB.color(0.12F * twinkle, 0xFFFFFF));
            g.fill(cx, cy, cx + s, cy + s, ARGB.color(twinkle, DOT & 0xFFFFFF));
        }
        float extension = game.trapExtension(x, y, game.time());
        for (int dir = 0; dir < 4; dir++) {
            if (level.spike(x, y, dir)) drawSpikes(g, x0, y0, t, dir, 1.0F, false);
            if (level.trap(x, y, dir)) drawSpikes(g, x0, y0, t, dir, extension, true);
        }
    }

    private void drawSpikes(GuiGraphicsExtractor g, int x0, int y0, int t, int dir, float extension, boolean trap) {
        int e = Math.max(2, t / 6);
        float length = t * (trap ? 0.44F : 0.2F) * extension;
        int base = t / 3;
        if (trap) {
            float pulse = 0.55F + 0.45F * (float) Math.sin(time * 6.0);
            int slit = ARGB.color(0.5F + 0.5F * pulse, 0xFF3A1F);
            int w = Math.max(2, t / 10);
            for (int k = 0; k < 3; k++) {
                int c = k * base + base / 2;
                switch (dir) {
                    case TombLevel.UP -> g.fill(x0 + c - w, y0, x0 + c + w, y0 + 2, slit);
                    case TombLevel.DOWN -> g.fill(x0 + c - w, y0 + t - 2, x0 + c + w, y0 + t, slit);
                    case TombLevel.LEFT -> g.fill(x0, y0 + c - w, x0 + 2, y0 + c + w, slit);
                    default -> g.fill(x0 + t - 2, y0 + c - w, x0 + t, y0 + c + w, slit);
                }
            }
        }
        if (length < 1.0F) return;
        int rows = Math.max(2, Math.round(length / 1.5F));
        for (int k = 0; k < 3; k++) {
            float center = k * base + base / 2.0F;
            for (int r = 0; r < rows; r++) {
                float frac = r / (float) rows;
                float half = base * (trap ? 0.45F : 0.32F) * (1.0F - frac);
                if (half < 0.5F) half = 0.5F;
                float d0 = frac * length;
                float d1 = (r + 1) / (float) rows * length;
                int color = trap ? (frac > 0.6F ? TRAP_TIP : TRAP) : (frac > 0.6F ? SPIKE_TIP : SPIKE);
                switch (dir) {
                    case TombLevel.UP -> fill(g, x0 + center - half, y0 + d0, x0 + center + half, y0 + d1, color);
                    case TombLevel.DOWN -> fill(g, x0 + center - half, y0 + t - d1, x0 + center + half, y0 + t - d0, color);
                    case TombLevel.LEFT -> fill(g, x0 + d0, y0 + center - half, x0 + d1, y0 + center + half, color);
                    default -> fill(g, x0 + t - d1, y0 + center - half, x0 + t - d0, y0 + center + half, color);
                }
            }
        }
        if (e > 0 && extension > 0.9F && trap) {
            float pulse = (float) Math.max(0.0, Math.sin(time * 12.0));
            int glow = ARGB.color(0.12F * pulse, 0xFF2030);
            g.fill(x0, y0, x0 + t, y0 + t, glow);
        }
    }

    private static void fill(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        int ix0 = Math.round(x0);
        int iy0 = Math.round(y0);
        int ix1 = Math.max(ix0 + 1, Math.round(x1));
        int iy1 = Math.max(iy0 + 1, Math.round(y1));
        g.fill(ix0, iy0, ix1, iy1, color);
    }

    private void drawSurfaceLight(GuiGraphicsExtractor g, int width, int ceiling, int t) {
        if (ceiling < -t) return;
        float pulse = 0.9F + 0.1F * (float) Math.sin(time * 1.3F);
        int haze = ARGB.color(0.07F * pulse, 0xE6ECFF);
        int strong = ARGB.color(0.34F * pulse, 0xE6ECFF);
        int band = t * 6;
        if (ceiling - band > 0) g.fill(0, 0, width, ceiling - band, haze);
        g.fillGradient(0, ceiling - band, width, ceiling, haze, strong);
        g.fillGradient(0, ceiling, width, ceiling + t * 2, strong, 0x00000000);
    }

    private void drawExit(GuiGraphicsExtractor g, TombLevel level, int t) {
        int x0 = level.exitX * t;
        int y0 = sy(level.exitY + 1, t);
        float pulse = 0.75F + 0.25F * (float) Math.sin(time * 2.4F);
        g.fill(x0, sy(level.height, t), x0 + t, y0, 0xFFF4F6FF);
        g.fillGradient(x0 - t / 2, sy(level.height + 4, t), x0 + t + t / 2, y0 + t, ARGB.color(0.0F, 0xFFFFFF),
                ARGB.color(0.55F * pulse, 0xFFFFFF));
        g.fillGradient(x0, y0, x0 + t, y0 + t, ARGB.color(0.85F * pulse, 0xFFFFFF), ARGB.color(0.2F, 0xFFFFFF));
        for (int k = 0; k < 5; k++) {
            float phase = time * 0.7F + k * 1.3F;
            int rx = x0 + Math.round((float) Math.sin(phase) * t * 1.2F) + t / 2;
            int w = Math.max(1, t / 6);
            g.fillGradient(rx - w, sy(level.height + 3, t), rx + w, y0 + t * 2, ARGB.color(0.10F * pulse, 0xFFFFFF), 0x00FFFFFF);
        }
    }

    private void drawLighting(GuiGraphicsExtractor g, TombLevel level, int t, int yMin, int yMax) {
        float hx = game.x() + 0.5F;
        float hy = game.y() + 0.5F + headLift;
        float flicker = 0.96F + 0.04F * (float) Math.sin(time * 9.0F) * (float) Math.sin(time * 5.3F);
        float radius = 6.5F * flicker;
        int half = Math.max(1, t / 2);
        for (int y = Math.max(0, yMin); y <= Math.min(level.height - 1, yMax); y++) {
            for (int x = -1; x <= level.width; x++) {
                for (int sub = 0; sub < 4; sub++) {
                    float wx = x + 0.25F + (sub & 1) * 0.5F;
                    float wy = y + 0.25F + (sub >> 1) * 0.5F;
                    float dx = wx - hx;
                    float dy = wy - hy;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float light = Math.max(0.0F, 1.0F - Math.max(0.0F, dist - 1.8F) / radius);
                    float ex = wx - (level.exitX + 0.5F);
                    float ey = wy - (level.exitY + 0.5F);
                    float exit = Math.max(0.0F, 1.0F - (float) Math.sqrt(ex * ex + ey * ey) / 7.0F);
                    float lit = Math.max(Math.max(light * light, exit), 0.10F);
                    float dark = (1.0F - lit) * 0.9F;
                    if (dark < 0.02F) continue;
                    int px = x * t + (sub & 1) * half;
                    int py = sy(y + 1, t) + (1 - (sub >> 1)) * half;
                    int w = (sub & 1) == 0 ? half : t - half;
                    int h = (sub >> 1) == 1 ? half : t - half;
                    g.fill(px, py, px + w, py + h, ARGB.black(dark));
                }
            }
        }
    }

    private void drawTrail(GuiGraphicsExtractor g, int t) {
        int size = Math.round(t * 0.7F);
        for (int i = trailCount - 1; i >= 1; i--) {
            float alpha = 0.22F * (1.0F - i / (float) trailX.length);
            int x = Math.round((trailX[i] + 0.5F) * t) - size / 2;
            int y = sy(trailY[i] + 0.5F, t) - size / 2;
            g.fill(x, y, x + size, y + size, ARGB.color(alpha, 0xE9EBF3));
        }
    }

    private void drawHead(GuiGraphicsExtractor g, int t) {
        if (headAlpha <= 0.01F) return;
        float size = t * 0.86F;
        float cx = (game.x() + 0.5F) * t;
        float cy = -(game.y() + 0.5F + headLift) * t;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        float sx = 1.0F;
        float syScale = 1.0F;
        float offX = 0.0F;
        float offY = 0.0F;
        if (squash > 0.0F && squashDir >= 0) {
            float s = squash * squash;
            boolean vertical = squashDir == TombLevel.UP || squashDir == TombLevel.DOWN;
            float along = 1.0F - 0.3F * s;
            float across = 1.0F + 0.18F * s;
            sx = vertical ? across : along;
            syScale = vertical ? along : across;
            offX = TombLevel.DX[squashDir] * (1.0F - along) * size * 0.5F;
            offY = -TombLevel.DY[squashDir] * (1.0F - along) * size * 0.5F;
        }
        pose.translate(cx + offX, cy + offY);
        pose.scale(sx, syScale);
        int glowSize = Math.round(size * 1.5F);
        float glowAlpha = 0.06F + 0.1F * shownProgress;
        g.fill(-glowSize / 2, -glowSize / 2, glowSize / 2, glowSize / 2, ARGB.color(glowAlpha * headAlpha, 0xFFFFFF));
        int s = Math.round(size);
        g.fill(-s / 2 - 1, -s / 2 - 1, s / 2 + 1, s / 2 + 1, ARGB.black(0.8F * headAlpha));
        head.draw(g, -s / 2, -s / 2, s, shownProgress, headAlpha, 1.0F);
        pose.popMatrix();
    }

    private void drawParticles(GuiGraphicsExtractor g, int t, boolean front) {
        for (Particle p : particles) {
            boolean ember = p.drag == 0.3F;
            if (ember != front) continue;
            float alpha = Math.min(1.0F, p.life / p.maxLife * 1.6F);
            int size = Math.max(1, Math.round(p.size * t));
            int x = Math.round(p.x * t) - size / 2;
            int y = sy(p.y, t) - size / 2;
            if (p.texel >= 0) {
                head.drawTexel(g, p.texel % 8, p.texel / 8, p.face, x, y, size, alpha);
            } else {
                g.fill(x, y, x + size, y + size, ARGB.color(alpha * ARGB.alphaFloat(p.color), p.color & 0xFFFFFF));
            }
        }
    }

    private void drawTide(GuiGraphicsExtractor g, TombLevel level, int t, int width, int height, float baseY) {
        float surface = game.tide();
        int fromX = -left - 4;
        int toX = width - left + 4;
        int bottom = Math.round(height - baseY) + 8;
        int column = Math.max(2, t / 8);
        for (int x = fromX; x < toX; x += column) {
            float wx = x / (float) t;
            float wave = (float) (Math.sin(wx * 1.9 + time * 3.1) * 0.12 + Math.sin(wx * 0.8 - time * 1.7) * 0.10
                    + Math.sin(wx * 4.3 + time * 5.0) * 0.04);
            int top = sy(surface + wave, t);
            if (top >= bottom) continue;
            g.fillGradient(x, top - t, x + column, top, 0x00000000, ARGB.color(0.35F, TIDE_TOP & 0xFFFFFF));
            g.fill(x, top, x + column, top + Math.max(1, t / 12), TIDE_TOP);
            g.fillGradient(x, top + Math.max(1, t / 12), x + column, Math.min(bottom, top + t * 3), TIDE_BODY, TIDE_DEEP);
            if (top + t * 3 < bottom) g.fill(x, top + t * 3, x + column, bottom, TIDE_DEEP);
        }
    }

    private void drawSides(GuiGraphicsExtractor g, int width, int height) {
        int fieldRight = left + game.level.width * tile;
        g.fill(0, 0, Math.max(0, left), height, BG);
        g.fill(fieldRight, 0, width, height, BG);
        int band = Math.max(8, tile);
        for (int i = 0; i < band; i += 2) {
            float alpha = 0.6F * (1.0F - i / (float) band);
            g.fill(left + i, 0, left + i + 2, height, ARGB.black(alpha));
            g.fill(fieldRight - i - 2, 0, fieldRight - i, height, ARGB.black(alpha));
        }
        g.fillGradient(0, 0, width, height / 6, ARGB.black(0.7F), 0x00000000);
        g.fillGradient(0, height - height / 6, width, height, 0x00000000, ARGB.black(0.6F));
    }

    private void drawDanger(GuiGraphicsExtractor g, int width, int height) {
        if (game.state() != TombGame.State.RUNNING) return;
        float gap = game.y() - game.tide();
        float danger = Math.max(0.0F, Math.min(1.0F, (4.5F - gap) / 3.5F));
        if (danger <= 0.0F) return;
        float beat = (float) Math.pow(Math.max(0.0, Math.sin(time * (5.0F + danger * 4.0F))), 6.0);
        float alpha = danger * (0.25F + 0.35F * beat);
        int band = height / 4;
        int color = ARGB.color(alpha, 0xC0001A);
        g.fillGradient(0, height - band, width, height, 0x00000000, color);
        g.fillGradient(0, 0, width, band / 2, color, 0x00000000);
        for (int i = 0; i < band / 2; i += 2) {
            int c = ARGB.color(alpha * (1.0F - i / (float) (band / 2)), 0xC0001A);
            g.fill(i, 0, i + 2, height, c);
            g.fill(width - i - 2, 0, width - i, height, c);
        }
    }

    public int tile() {
        return tile;
    }

    public int left() {
        return left;
    }
}
