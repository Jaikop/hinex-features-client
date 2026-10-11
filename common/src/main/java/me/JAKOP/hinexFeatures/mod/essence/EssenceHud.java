package me.JAKOP.hinexFeatures.mod.essence;

import me.JAKOP.hinexFeatures.mod.ui.Draw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

public final class EssenceHud {

    public static final int ESSENCE = 0xFFDDA8F1;
    public static final int GLOBAL = 0xFFA8CDF1;
    private static final long GAIN_MS = 3500;
    private static final long CARD_MS = 5000;
    private static final long SLIDE_MS = 180;
    private static final long FADE_MS = 450;

    private record Card(ItemStack icon, Component name, long start) {
    }

    private static int gained;
    private static int total;
    private static long gainStart;
    private static long gainUpdated;
    private static final Deque<Card> CARDS = new ArrayDeque<>();

    private EssenceHud() {
    }

    public static void reset() {
        gained = 0;
        gainStart = 0;
        CARDS.clear();
    }

    public static void gain(int amount, int newTotal) {
        long now = Util.getMillis();
        if (now - gainUpdated > GAIN_MS) {
            gained = 0;
            gainStart = now;
        }
        gained += amount;
        total = newTotal;
        gainUpdated = now;
    }

    public static void unlocked(ItemStack icon, Component name) {
        long now = Util.getMillis();
        long start = CARDS.isEmpty() ? now : Math.max(now, CARDS.peekLast().start() + CARD_MS - FADE_MS);
        CARDS.addLast(new Card(icon, name, start));
    }

    private static float visibility(long now, long start, long end) {
        if (now < start || now > end) return 0;
        float in = Mth.clamp((now - start) / (float) SLIDE_MS, 0, 1);
        float out = Mth.clamp((end - now) / (float) FADE_MS, 0, 1);
        return Math.min(in, out);
    }

    public static void extract(GuiGraphicsExtractor g) {
        long now = Util.getMillis();
        Font font = Minecraft.getInstance().font;
        int right = g.guiWidth() - 4;
        int bottom = g.guiHeight() - 4;

        float gainAlpha = visibility(now, gainStart, gainUpdated + GAIN_MS);
        if (gainAlpha > 0.02F) {
            Component plus = Component.literal("+" + gained + " ✦");
            Component count = Component.literal(String.valueOf(total));
            int width = font.width(plus) + font.width(count) + 16;
            float slide = (1 - Mth.clamp((now - gainStart) / (float) SLIDE_MS, 0, 1)) * 30;
            int x = right - width + (int) slide;
            int y = bottom - 14;
            float pop = 1 + 0.12F * Mth.clamp(1 - (now - gainUpdated) / 160F, 0, 1);
            g.pose().pushMatrix();
            g.pose().translate(x + width / 2F, y + 7);
            g.pose().scale(pop, pop);
            g.pose().translate(-(x + width / 2F), -(y + 7));
            Draw.roundRect(g, x, y, width, 14, 4, Draw.withAlpha(0xD0120E1C, gainAlpha));
            Draw.roundRect(g, x, y + 13, width, 1, 0, Draw.withAlpha(ESSENCE, gainAlpha * 0.6F));
            if (gainAlpha > 0.05F) {
                g.text(font, plus, x + 6, y + 3, Draw.withAlpha(ESSENCE, gainAlpha), false);
                g.text(font, count, x + 10 + font.width(plus), y + 3, Draw.withAlpha(0xFF8C8FA8, gainAlpha), false);
            }
            g.pose().popMatrix();
            bottom = y - 4;
        }

        while (!CARDS.isEmpty() && now > CARDS.peekFirst().start() + CARD_MS) CARDS.removeFirst();
        Card card = CARDS.peekFirst();
        if (card == null) return;
        float alpha = visibility(now, card.start(), card.start() + CARD_MS);
        if (alpha <= 0.02F) return;
        Component title = Component.literal("Изучено");
        int width = Math.max(font.width(title), font.width(card.name())) + 30;
        float slide = (1 - Mth.clamp((now - card.start()) / (float) SLIDE_MS, 0, 1)) * 40;
        int x = right - width + (int) slide;
        int y = bottom - 24;
        Draw.roundRect(g, x, y, width, 24, 5, Draw.withAlpha(0xE0120E1C, alpha));
        Draw.roundRect(g, x, y, 2, 24, 1, Draw.withAlpha(ESSENCE, alpha));
        if (alpha > 0.3F) g.item(card.icon(), x + 6, y + 4);
        if (alpha > 0.05F) {
            g.text(font, title, x + 26, y + 3, Draw.withAlpha(0xFF8C8FA8, alpha), false);
            g.text(font, card.name(), x + 26, y + 13, Draw.withAlpha(0xFFFFFFFF, alpha), false);
        }
    }
}
