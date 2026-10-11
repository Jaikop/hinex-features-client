package me.JAKOP.hinexFeatures.mod.admin;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MacroRunner {

    private static final class Run {
        final Macro macro;
        int step;
        int loop;
        long nextAt;

        Run(Macro macro) {
            this.macro = macro;
        }
    }

    private static final List<Run> RUNS = new ArrayList<>();

    private MacroRunner() {
    }

    public static void start(Macro macro) {
        if (macro.steps.isEmpty()) return;
        stop(macro);
        RUNS.add(new Run(macro));
    }

    public static void stop(Macro macro) {
        RUNS.removeIf(run -> run.macro == macro);
    }

    public static void stopAll() {
        RUNS.clear();
    }

    public static boolean running(Macro macro) {
        for (Run run : RUNS) if (run.macro == macro) return true;
        return false;
    }

    public static void tick(Minecraft minecraft) {
        if (RUNS.isEmpty()) return;
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null) {
            RUNS.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Run> iterator = RUNS.iterator();
        while (iterator.hasNext()) {
            Run run = iterator.next();
            if (now < run.nextAt) continue;
            if (run.step >= run.macro.steps.size()) {
                run.loop++;
                if (run.macro.repeat > 0 && run.loop >= run.macro.repeat) {
                    iterator.remove();
                    continue;
                }
                run.step = 0;
            }
            Macro.Step step = run.macro.steps.get(run.step++);
            perform(minecraft, player, step);
            run.nextAt = now + Math.round(Math.max(0.0, step.delay) * 1000.0);
        }
    }

    private static void perform(Minecraft minecraft, LocalPlayer player, Macro.Step step) {
        String text = placeholders(minecraft, player, step.text.strip());
        if (text.isEmpty() || step.type.equals("wait")) return;
        if (text.length() > 256) {
            HinexFeaturesClient.LOGGER.warn("Шаг макроса длиннее 256 символов — пропущен");
            player.sendSystemMessage(Component.literal("Макрос: шаг длиннее 256 символов пропущен").withColor(0xFF5C7A));
            return;
        }
        if (step.type.equals("chat") && !text.startsWith("/")) {
            player.connection.sendChat(text);
        } else {
            player.connection.sendCommand(text.startsWith("/") ? text.substring(1) : text);
        }
    }

    static String placeholders(Minecraft minecraft, LocalPlayer player, String text) {
        if (!text.contains("{")) return text;
        BlockPos pos = player.blockPosition();
        String result = text.replace("{me}", player.getGameProfile().name())
                .replace("{x}", String.valueOf(pos.getX()))
                .replace("{y}", String.valueOf(pos.getY()))
                .replace("{z}", String.valueOf(pos.getZ()));
        if (result.contains("{target}")) {
            String target = "";
            if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player other) {
                target = other.getGameProfile().name();
            } else {
                Entity crosshair = minecraft.crosshairPickEntity;
                if (crosshair instanceof Player other) target = other.getGameProfile().name();
            }
            result = result.replace("{target}", target);
        }
        if (result.contains("{look}")) {
            String look = "~ ~ ~";
            if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos block = hit.getBlockPos();
                look = block.getX() + " " + block.getY() + " " + block.getZ();
            }
            result = result.replace("{look}", look);
        }
        return result;
    }

    public static void extractHud(GuiGraphicsExtractor g) {
        if (RUNS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        int y = 4;
        for (Run run : RUNS) {
            String text = "▶ " + run.macro.name + "  " + Math.min(run.step, run.macro.steps.size()) + "/" + run.macro.steps.size()
                    + (run.macro.repeat != 1 ? "  ×" + (run.loop + 1) + (run.macro.repeat > 0 ? "/" + run.macro.repeat : "") : "");
            int width = minecraft.font.width(text) + 8;
            Draw.roundRect(g, 4, y, width, 12, 3, 0xB0101224);
            g.text(minecraft.font, text, 8, y + 2, Theme.ACCENT_BLUE, false);
            y += 14;
        }
    }
}
