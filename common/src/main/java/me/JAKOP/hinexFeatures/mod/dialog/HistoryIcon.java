package me.JAKOP.hinexFeatures.mod.dialog;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HistoryIcon {

    private static final int OUTLINE = 0xFF2A1A0E;
    private static final int COVER = 0xFF8B5A2B;
    private static final int SPINE = 0xFF6A4220;
    private static final int PAGES = 0xFFF1E3C4;
    private static final int LINES = 0xFFB9A283;
    private static final int GOLD = 0xFFD4A25A;
    private static final int RING = 0xFFD8DCE4;
    private static final int GLASS = 0x80BFE3FF;
    private static final int HANDLE = 0xFF4A2E18;

    private HistoryIcon() {
    }

    public static void draw(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y + 1, x + 12, y + 15, OUTLINE);
        g.fill(x + 2, y + 2, x + 11, y + 14, COVER);
        g.fill(x + 2, y + 2, x + 4, y + 14, SPINE);
        g.fill(x + 5, y + 3, x + 10, y + 13, PAGES);
        g.fill(x + 6, y + 5, x + 9, y + 6, LINES);
        g.fill(x + 6, y + 7, x + 9, y + 8, LINES);
        g.fill(x + 6, y + 9, x + 8, y + 10, LINES);
        g.fill(x + 2, y + 2, x + 11, y + 3, GOLD);
        float cx = 10.5F;
        float cy = 9.5F;
        for (int py = 5; py <= 14; py++) {
            for (int px = 6; px <= 15; px++) {
                float dx = px + 0.5F - cx;
                float dy = py + 0.5F - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d <= 2.4F) g.fill(x + px, y + py, x + px + 1, y + py + 1, GLASS);
                else if (d <= 3.6F) g.fill(x + px, y + py, x + px + 1, y + py + 1, RING);
                else if (d <= 4.3F && dx < 0) g.fill(x + px, y + py, x + px + 1, y + py + 1, OUTLINE);
            }
        }
        for (int i = 0; i < 2; i++) {
            g.fill(x + 13 + i, y + 13 + i, x + 15 + i, y + 15 + i, HANDLE);
        }
    }
}
