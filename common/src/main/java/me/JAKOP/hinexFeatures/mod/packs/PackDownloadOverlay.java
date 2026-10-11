package me.JAKOP.hinexFeatures.mod.packs;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.gate.GateScreen;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;

public final class PackDownloadOverlay {

    private PackDownloadOverlay() {
    }

    public static void extract(Screen screen, GuiGraphicsExtractor g) {
        if (!(screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof GateScreen)) return;
        PackManager packs = HinexFeaturesClient.packs();
        PackManager.Progress progress = packs != null ? packs.progress() : null;
        if (progress == null) return;

        Font font = Minecraft.getInstance().font;
        String title = "Загрузка ресурспаков Hinex";
        String detail = progress.name() + (progress.count() > 1 ? " (" + (progress.index() + 1) + "/" + progress.count() + ")" : "")
                + "  " + megabytes(progress.done()) + (progress.total() > 0 ? " / " + megabytes(progress.total()) : "") + " МБ";

        int w = Math.max(180, Math.max(font.width(title), font.width(detail)) + 20);
        int x = screen.width - w - 6;
        int y = 6;
        g.nextStratum();
        Draw.accentPanel(g, x, y, w, 34, 5, Theme.POPUP);
        g.text(font, title, x + 10, y + 6, Theme.TEXT, false);
        g.text(font, detail, x + 10, y + 16, Theme.TEXT_DIM, false);

        int barW = w - 20;
        Draw.roundRect(g, x + 10, y + 27, barW, 3, 1, Theme.PANEL_BORDER);
        if (progress.total() > 0) {
            int filled = (int) (barW * Math.min(1.0, (double) progress.done() / progress.total()));
            if (filled > 0) Draw.gradientRoundRect(g, x + 10, y + 27, filled, 3, 1, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        } else {
            int runner = barW / 4;
            int offset = (int) (System.currentTimeMillis() / 8 % (barW + runner)) - runner;
            int from = Math.max(0, offset);
            int to = Math.min(barW, offset + runner);
            if (to > from) Draw.gradientRoundRect(g, x + 10 + from, y + 27, to - from, 3, 1, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        }
    }

    private static String megabytes(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f", bytes / 1_048_576.0);
    }
}
