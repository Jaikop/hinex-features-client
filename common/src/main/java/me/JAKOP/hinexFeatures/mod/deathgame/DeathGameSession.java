package me.JAKOP.hinexFeatures.mod.deathgame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClient;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import me.JAKOP.hinexFeatures.mod.vfx.VfxParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DeathGameSession {

    public enum Phase { FADE, INTRO, PLAY, DYING, LOST, WINNING, WON }

    public record Penalty(String effect, int seconds, int level) {
    }

    private static final double FADE_TIME = 2.2;
    private static final double INTRO_TIME = 3.6;
    private static final double DYING_TIME = 1.8;
    private static final double WINNING_TIME = 1.7;
    private static final String NOIR_ID = "hinex-deathgame-noir";

    private final long seed;
    private final @Nullable TombGame game;
    private final @Nullable TombView view;
    private final HeadFace head;
    private final List<Penalty> penalties = new ArrayList<>();
    private final double created;

    private Phase phase;
    private double phaseStart;
    private double lastFrame = -1.0;
    private @Nullable Component cause;
    private boolean resultSent;
    private double resultAt;
    private boolean allowed;
    private boolean respawning;
    private String lostReason = "lose";
    private double lostAt;
    private int lostWait;
    private int countdownPlayed;
    private boolean sawDeath;
    private int actionY = -1;

    private DeathGameSession(long seed, @Nullable TombGame game, Phase phase) {
        this.seed = seed;
        this.game = game;
        this.head = HeadFace.forLocalPlayer(seed);
        this.view = game != null ? new TombView(game, head) : null;
        this.created = VfxClock.now();
        this.phase = phase;
        this.phaseStart = created;
    }

    public static DeathGameSession start(JsonObject message) {
        long seed = message.has("seed") ? message.get("seed").getAsLong() : System.nanoTime();
        String difficultyName = message.has("difficulty") ? message.get("difficulty").getAsString().toLowerCase(Locale.ROOT) : "normal";
        int length = message.has("length") ? message.get("length").getAsInt() : 56;
        float timeLimit = message.has("timeLimit") ? message.get("timeLimit").getAsFloat() : 75.0F;
        TombLevel.Difficulty difficulty = TombLevel.Difficulty.of(difficultyName);
        TombLevel level = TombLevel.generate(seed, length, difficulty);
        DeathGameSession[] holder = new DeathGameSession[1];
        TombGame game = new TombGame(level, difficulty, timeLimit, new TombGame.Listener() {
            @Override
            public void onMove(int dir) {
                holder[0].view.onMove(dir);
            }

            @Override
            public void onImpact(int dir, int x, int y, boolean bump) {
                holder[0].view.onImpact(dir, x, y, bump);
            }

            @Override
            public void onDot(int x, int y) {
                holder[0].view.onDot(x, y);
            }

            @Override
            public void onDeath(TombGame.Death death) {
                holder[0].view.onDeath(death);
            }

            @Override
            public void onWin() {
                holder[0].view.onWin();
            }
        });
        DeathGameSession session = new DeathGameSession(seed, game, Phase.FADE);
        holder[0] = session;
        session.lostWait = message.has("loseWait") ? message.get("loseWait").getAsInt() : 15;
        session.readPenalties(message);
        session.onFirstFrame();
        return session;
    }

    public static DeathGameSession lostOnly(JsonObject message) {
        DeathGameSession session = new DeathGameSession(System.nanoTime(), null, Phase.LOST);
        session.onLost(message);
        DeathGameMusic.play(0.35F);
        return session;
    }

    public static DeathGameSession wonOnly() {
        DeathGameSession session = new DeathGameSession(System.nanoTime(), null, Phase.WON);
        session.allowed = true;
        return session;
    }

    private void onFirstFrame() {
        Minecraft.getInstance().getMusicManager().stopPlaying();
        DeathGameMusic.play(0.85F);
        VfxParams params = new VfxParams(new JsonObject());
        VfxClient.start(NOIR_ID, VfxClient.SCREEN, "noir", FADE_TIME + 1.0, Vec3.ZERO, null, params);
    }

    private void readPenalties(JsonObject message) {
        if (!message.has("penalties") || !message.get("penalties").isJsonArray()) return;
        penalties.clear();
        JsonArray array = message.getAsJsonArray("penalties");
        for (JsonElement element : array) {
            try {
                JsonObject object = element.getAsJsonObject();
                penalties.add(new Penalty(object.get("effect").getAsString(), object.get("seconds").getAsInt(),
                        object.has("level") ? object.get("level").getAsInt() : 1));
            } catch (Exception ignored) {
            }
        }
    }

    public void onLost(JsonObject message) {
        lostReason = message.has("reason") ? message.get("reason").getAsString() : "lose";
        lostWait = message.has("wait") ? message.get("wait").getAsInt() : lostWait;
        lostAt = VfxClock.now();
        allowed = false;
        readPenalties(message);
        if (game != null && (phase == Phase.FADE || phase == Phase.INTRO || phase == Phase.PLAY)) {
            game.forceDeath();
            setPhase(Phase.DYING);
        }
    }

    public void onAllow() {
        if (!allowed) DeathGameSounds.allowed();
        allowed = true;
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseStart = VfxClock.now();
    }

    private double phaseTime() {
        return VfxClock.now() - phaseStart;
    }

    public void frame() {
        double now = VfxClock.now();
        float dt = lastFrame < 0 ? 0.0F : (float) Math.min(0.1, now - lastFrame);
        lastFrame = now;
        double t = phaseTime();
        switch (phase) {
            case FADE -> {
                if (t >= FADE_TIME) setPhase(Phase.INTRO);
            }
            case INTRO -> {
                if (view != null) view.setIntroPan((float) Math.min(1.0, t / 2.4));
                int count = (int) Math.floor((t - 1.2) / 0.8) + 1;
                if (t >= 1.2 && count > countdownPlayed && count <= 3) {
                    countdownPlayed = count;
                    DeathGameSounds.countdown(false);
                }
                if (t >= INTRO_TIME && game != null) {
                    if (view != null) view.setIntroPan(-1.0F);
                    DeathGameSounds.countdown(true);
                    game.start();
                    setPhase(Phase.PLAY);
                }
            }
            case PLAY -> {
                if (game != null) {
                    game.update(dt);
                    if (game.state() == TombGame.State.DEAD) {
                        DeathGameSounds.death();
                        sendResult(false);
                        lostAt = VfxClock.now();
                        setPhase(Phase.DYING);
                    } else if (game.state() == TombGame.State.WON) {
                        DeathGameSounds.win();
                        DeathGameMusic.fadeOut();
                        sendResult(true);
                        setPhase(Phase.WINNING);
                    }
                }
            }
            case DYING -> {
                if (t >= DYING_TIME) {
                    DeathGameMusic.volume(0.35F);
                    setPhase(Phase.LOST);
                }
            }
            case WINNING -> {
                if (t >= WINNING_TIME) setPhase(Phase.WON);
            }
            case LOST -> {
                if (!allowed && waitRemaining() < -8.0) allowed = true;
            }
            case WON -> {
                if (!allowed && resultSent && now - resultAt > 12.0) allowed = true;
            }
        }
        if (view != null) view.update(dt);
    }

    public void input(int dir) {
        if (phase == Phase.PLAY && game != null) game.input(dir);
    }

    private void sendResult(boolean won) {
        if (resultSent || game == null) return;
        resultSent = true;
        resultAt = VfxClock.now();
        JsonObject message = Protocol.message(Protocol.DEATH_GAME);
        message.addProperty("action", "result");
        message.addProperty("won", won);
        message.addProperty("time", game.time());
        message.addProperty("score", game.collected());
        message.addProperty("moves", game.moves());
        ClientNetwork.send(message);
    }

    public void respawn() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canRespawn() || minecraft.player == null) return;
        respawning = true;
        DeathGameMusic.fadeOut();
        minecraft.player.respawn();
    }

    public boolean canRespawn() {
        return allowed && !respawning && (phase == Phase.LOST || phase == Phase.WON);
    }

    public void end() {
        DeathGameMusic.fadeOut();
        VfxClient.stop(NOIR_ID);
    }

    public double waitRemaining() {
        return lostWait - (VfxClock.now() - lostAt);
    }

    public void draw(GuiGraphicsExtractor g, Font font, int width, int height) {
        double t = phaseTime();
        switch (phase) {
            case FADE -> drawFade(g, font, width, height, t);
            case INTRO -> {
                drawLevel(g, font, width, height, (float) smooth(0.2, 1.2, t));
                drawIntro(g, font, width, height, t);
            }
            case PLAY -> {
                drawLevel(g, font, width, height, 1.0F);
                drawHud(g, font, width, height);
            }
            case DYING -> {
                drawLevel(g, font, width, height, 1.0F);
                float red = (float) Math.max(0.0, 0.45 - t * 0.6);
                if (red > 0.0F) g.fill(0, 0, width, height, ARGB.color(red, 0xB00018));
                g.fill(0, 0, width, height, ARGB.color((float) smooth(0.6, DYING_TIME, t) * 0.88F, 0x060001));
            }
            case LOST -> {
                if (game != null) drawLevel(g, font, width, height, 1.0F);
                g.fill(0, 0, width, height, ARGB.color(game != null ? 0.88F : 1.0F, 0x060001));
                drawLost(g, font, width, height, t);
            }
            case WINNING -> {
                drawLevel(g, font, width, height, 1.0F);
                g.fill(0, 0, width, height, ARGB.white((float) smooth(0.3, WINNING_TIME, t) * 0.92F));
            }
            case WON -> drawWon(g, font, width, height, t);
        }
    }

    private void drawLevel(GuiGraphicsExtractor g, Font font, int width, int height, float alpha) {
        if (view != null) view.draw(g, font, width, height, alpha);
        else g.fill(0, 0, width, height, 0xFF030304);
    }

    private void drawFade(GuiGraphicsExtractor g, Font font, int width, int height, double t) {
        float dark = (float) smooth(0.0, 1.9, t);
        float red = (float) Math.max(0.0, 0.3 - t * 0.35);
        if (red > 0.0F) g.fill(0, 0, width, height, ARGB.color(red, 0x8A0010));
        g.fill(0, 0, width, height, ARGB.black(Math.min(1.0F, dark * 1.02F)));
        int band = height / 3;
        g.fillGradient(0, 0, width, band, ARGB.black(Math.min(1.0F, dark + 0.3F)), 0x00000000);
        g.fillGradient(0, height - band, width, height, 0x00000000, ARGB.black(Math.min(1.0F, dark + 0.3F)));

        float text = (float) (smooth(0.5, 1.1, t) * (1.0 - smooth(1.75, FADE_TIME, t)));
        centered(g, font, Component.literal("ВЫ ПОГИБЛИ"), width / 2, (int) (height * 0.40), 3.0F, 0xE2E2E8, text);
        if (cause != null) {
            centered(g, font, cause.copy(), width / 2, (int) (height * 0.40) + 34, 1.0F, 0x8C8C98, text);
        }
    }

    private void drawIntro(GuiGraphicsExtractor g, Font font, int width, int height, double t) {
        float text = (float) (smooth(0.0, 0.5, t) * (1.0 - smooth(3.1, INTRO_TIME, t)));
        g.fillGradient(0, 0, width, (int) (height * 0.34), ARGB.black(0.85F * text), 0x00000000);
        centered(g, font, Component.literal("И С П Ы Т А Н И Е"), width / 2, (int) (height * 0.08), 1.0F, 0x8C8C98, text);
        centered(g, font, Component.literal("ВЫБЕРИСЬ К СВЕТУ"), width / 2, (int) (height * 0.08) + 14, 2.2F, 0xF0F0F5, text);
        centered(g, font, Component.literal("Пока тьма поднимается снизу — доберись до выхода наверху, чтобы возродиться"),
                width / 2, (int) (height * 0.08) + 38, 1.0F, 0xA8A8B4, text);
        g.fillGradient(0, (int) (height * 0.82), width, height, 0x00000000, ARGB.black(0.85F * text));
        centered(g, font, Component.literal("WASD / стрелки — скольжение до стены  ·  шипы и тьма убивают"),
                width / 2, (int) (height * 0.9), 1.0F, 0xC8C8D2, text);

        if (t >= 1.2 && t < INTRO_TIME) {
            int number = 3 - (int) Math.floor((t - 1.2) / 0.8);
            if (number >= 1) {
                double local = (t - 1.2) % 0.8 / 0.8;
                float scale = 5.0F + (float) (1.0 - local) * 1.5F;
                float alpha = (float) (1.0 - local * 0.8);
                centered(g, font, Component.literal(String.valueOf(number)), width / 2, (int) (height * 0.5) - 20, scale, 0xFFFFFF, alpha);
            }
        }
    }

    private void drawHud(GuiGraphicsExtractor g, Font font, int width, int height) {
        if (game == null || view == null) return;
        int tile = view.tile();
        int left = view.left();
        int fieldRight = left + game.level.width * tile;
        float remaining = game.remaining();
        int seconds = (int) Math.ceil(remaining);
        String clock = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
        int clockColor = remaining < 10.0F ? ((int) (remaining * 4) % 2 == 0 ? 0xFF4050 : 0xFFA0A8) : 0xE8E8EE;
        centered(g, font, Component.literal(clock), width / 2, 8, 1.6F, clockColor, 1.0F);

        String dots = "✦ " + game.collected() + "/" + game.level.dotCount;
        g.text(font, dots, left + 6, 10, 0xFFB8BCCC, true);

        int barX = Math.min(width - 10, fieldRight + 10);
        int barTop = 30;
        int barBottom = height - 30;
        int barHeight = barBottom - barTop;
        g.fill(barX, barTop, barX + 3, barBottom, 0x40FFFFFF);
        float progress = view.progressShown();
        int fillTop = barBottom - Math.round(barHeight * progress);
        g.fill(barX, fillTop, barX + 3, barBottom, 0xFFE9EBF3);
        float span = Math.max(1, game.level.exitY - game.level.startY);
        float tide = Math.max(0.0F, Math.min(1.0F, (game.tide() - game.level.startY) / span));
        int tideY = barBottom - Math.round(barHeight * tide);
        g.fill(barX - 2, tideY - 1, barX + 5, tideY + 1, 0xFFE0182F);
        g.fill(barX, tideY, barX + 3, barBottom, 0xC0600010);
        int icon = 9;
        head.draw(g, barX + 1 - icon / 2, fillTop - icon / 2, icon, progress, 1.0F, 0.0F);
        g.fill(barX - 3, barTop - 3, barX + 6, barTop, 0xFFFFFFFF);

        if (game.time() < 6.0F) {
            float alpha = (float) (1.0 - smooth(4.5, 6.0, game.time()));
            centered(g, font, Component.literal("Выход с сервера во время испытания — поражение"), width / 2, height - 16, 1.0F,
                    0x6E6E7A, alpha);
        }
    }

    private void drawLost(GuiGraphicsExtractor g, Font font, int width, int height, double t) {
        float appear = (float) smooth(0.0, 0.8, t);
        int cx = width / 2;
        int y = Math.max(8, (int) (height * 0.1));
        int size = height < 300 ? 32 : 48;
        float shakeX = (float) (Math.sin(t * 40.0) * Math.max(0.0, 0.5 - t) * 6.0);
        g.fill(cx - size / 2 - 3, y - 3, cx + size / 2 + 3, y + size + 3, ARGB.color(0.35F * appear, 0x8A0010));
        head.draw(g, cx - size / 2 + Math.round(shakeX), y, size, 0.0F, appear, 0.0F);
        y += size + 10;

        String title = switch (lostReason) {
            case "quit" -> "ТЫ ПОКИНУЛ ИСПЫТАНИЕ";
            case "timeout" -> "ВРЕМЯ ВЫШЛО";
            default -> game != null && game.death() == TombGame.Death.SPIKES || game != null && game.death() == TombGame.Death.TRAP
                    ? "ШИПЫ ОКАЗАЛИСЬ БЫСТРЕЕ" : game != null && game.death() == TombGame.Death.TIME ? "ВРЕМЯ ВЫШЛО" : "ТЬМА ПОГЛОТИЛА ТЕБЯ";
        };
        y += centered(g, font, Component.literal(title), cx, y, 2.0F, 0xE8C8CC, appear) + 6;
        String subtitle = lostReason.equals("quit")
                ? "Выход с сервера засчитан как поражение"
                : "Возрождение будет, но тьма оставит след";
        y += centered(g, font, Component.literal(subtitle), cx, y, 1.0F, 0x9A8A8E, appear) + 12;

        actionY = y;
        if (!allowed) {
            int secs = (int) Math.ceil(Math.max(0.0, waitRemaining()));
            centered(g, font, Component.literal("Возрождение через"), cx, y, 1.0F, 0x8C8C98, appear);
            float pulse = (float) (1.0 + 0.08 * Math.max(0.0, Math.sin(t * Math.PI * 2.0)));
            centered(g, font, Component.literal(secs > 0 ? String.valueOf(secs) : "…"), cx, y + 12, 3.0F * pulse, 0xF0F0F5, appear);
        }
        y += 44;
        if (!penalties.isEmpty()) {
            centered(g, font, Component.literal("После возрождения:"), cx, y, 1.0F, 0x8C8C98, appear);
            MutableComponent list = Component.empty();
            for (int i = 0; i < penalties.size(); i++) {
                Penalty penalty = penalties.get(i);
                if (i > 0) list.append("  ·  ");
                list.append(Component.translatable("effect.minecraft." + penalty.effect()));
                if (penalty.level() > 1) list.append(" " + roman(penalty.level()));
                list.append(" " + penalty.seconds() + "с");
            }
            centered(g, font, list, cx, y + 12, 1.0F, 0xD04858, appear);
        }
    }

    private void drawWon(GuiGraphicsExtractor g, Font font, int width, int height, double t) {
        g.fill(0, 0, width, height, 0xFF050507);
        float settle = (float) smooth(0.0, 1.2, t);
        int cx = width / 2;
        int y = Math.max(8, (int) (height * 0.12));
        int size = height < 300 ? 36 : 48;
        for (int i = 6; i >= 1; i--) {
            int r = size / 2 + i * 8;
            g.fill(cx - r, y + size / 2 - r, cx + r, y + size / 2 + r, ARGB.white(0.025F * settle));
        }
        head.draw(g, cx - size / 2, y, size, 1.0F, 1.0F, 0.0F);
        y += size + 12;
        y += centered(g, font, Component.literal("ТЫ ВЫБРАЛСЯ"), cx, y, 2.0F, 0xF4F4F8, 1.0F) + 6;
        y += centered(g, font, Component.literal("Свет принял тебя — возрождение без последствий"), cx, y, 1.0F, 0xA8A8B4, 1.0F) + 2;
        if (game != null) {
            int secs = Math.round(game.time());
            String stats = String.format(Locale.ROOT, "Время %d:%02d  ·  Искры %d/%d", secs / 60, secs % 60, game.collected(), game.level.dotCount);
            y += centered(g, font, Component.literal(stats), cx, y, 1.0F, 0x70707C, 1.0F);
        }
        actionY = y + 12;
        if (!allowed) {
            String dots = ".".repeat((int) (VfxClock.now() * 2.5 % 4));
            centered(g, font, Component.literal("Сервер подтверждает" + dots), cx, actionY + 6, 1.0F, 0x8C8C98, 1.0F);
        }
        float fadeIn = (float) (1.0 - smooth(0.0, 0.6, t));
        if (fadeIn > 0.0F) g.fill(0, 0, width, height, ARGB.white(fadeIn * 0.92F));
    }

    public int actionY() {
        return actionY;
    }

    private static int centered(GuiGraphicsExtractor g, Font font, Component text, int x, int y, float scale, int rgb, float alpha) {
        int maxWidth = Math.max(80, g.guiWidth() - 24);
        if (scale <= 1.0F && font.width(text) > maxWidth) {
            int lineY = y;
            for (FormattedCharSequence line : font.split(text, maxWidth)) {
                if (alpha >= 0.04F) g.text(font, line, x - font.width(line) / 2, lineY, ARGB.color(Math.min(1.0F, alpha), rgb), true);
                lineY += 10;
            }
            return lineY - y;
        }
        float fit = Math.min(scale, maxWidth / (float) Math.max(1, font.width(text)));
        if (alpha < 0.04F) return Math.round(9 * fit);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(fit, fit);
        g.text(font, text, -font.width(text) / 2, 0, ARGB.color(Math.min(1.0F, alpha), rgb), true);
        g.pose().popMatrix();
        return Math.round(9 * fit);
    }

    private static double smooth(double from, double to, double t) {
        double x = Math.max(0.0, Math.min(1.0, (t - from) / (to - from)));
        return x * x * (3.0 - 2.0 * x);
    }

    private static String roman(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    public Phase phase() {
        return phase;
    }

    public @Nullable TombGame game() {
        return game;
    }

    public boolean allowed() {
        return allowed;
    }

    public boolean respawning() {
        return respawning;
    }

    public void setCause(@Nullable Component cause) {
        if (cause != null) this.cause = cause;
    }

    public double age() {
        return VfxClock.now() - created;
    }

    public boolean sawDeath() {
        return sawDeath;
    }

    public void markDeathSeen() {
        sawDeath = true;
    }

    public long seed() {
        return seed;
    }

    public void skipIntro() {
        if (phase == Phase.FADE) setPhase(Phase.INTRO);
        if (phase == Phase.INTRO) phaseStart = VfxClock.now() - INTRO_TIME;
    }
}
