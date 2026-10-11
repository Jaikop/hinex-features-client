package me.JAKOP.hinexFeatures.mod.chat;

import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class ChatTools {

    public static final int INPUT_X = 31;
    private static final int BUTTON = 12;
    private static final int EMOJI_X = 3;
    private static final int FORMAT_X = 16;
    private static final int POPUP_BOTTOM_OFFSET = 30;

    public static final String[] SMILE = {
            "  ####  ",
            " #    # ",
            "# #  # #",
            "#      #",
            "# #  # #",
            "#  ##  #",
            " #    # ",
            "  ####  ",
    };
    public static final String[] BRUSH = {
            "   ##   ",
            "  #  #  ",
            "  #  #  ",
            " ###### ",
            " #    # ",
            "##    ##",
            "        ",
            "++++++++",
    };
    private static final String[] LOCK = {
            " ### ",
            "#   #",
            "#####",
            "## ##",
            "#####",
    };

    private enum Popup { NONE, EMOJI, FORMAT }

    private final EditBox input;
    private final EmojiPicker emojiPicker = new EmojiPicker();
    private final FormatPanel formatPanel = new FormatPanel();
    private Popup open = Popup.NONE;
    private int screenWidth;
    private int screenHeight;
    private long shakeUntil;
    private int shakeButton;

    private String previewSource;
    private Component preview;

    private ChatTools(EditBox input, int width, int height) {
        this.input = input;
        this.screenWidth = width;
        this.screenHeight = height;
    }

    public static @Nullable ChatTools attach(@Nullable ChatTools previous, EditBox input, int width, int height) {
        if (ClientNetwork.state() == null) return null;
        input.setX(INPUT_X);
        input.setWidth(width - INPUT_X);
        ChatTools tools = new ChatTools(input, width, height);
        if (previous != null) tools.open = previous.open;
        return tools;
    }

    private static @Nullable ServerState state() {
        return ClientNetwork.state();
    }

    private int buttonY() {
        return screenHeight - 14;
    }

    private boolean overButton(double mx, double my, int bx) {
        return mx >= bx && mx < bx + BUTTON && my >= buttonY() && my < buttonY() + BUTTON;
    }

    private boolean commandMode() {
        return input.getValue().startsWith("/");
    }

    public void extract(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ServerState state = state();
        if (state == null) return;
        if (!state.plus() || commandMode()) open = Popup.NONE;

        Font font = Minecraft.getInstance().font;
        g.nextStratum();

        button(g, EMOJI_X, SMILE, open == Popup.EMOJI, state.plus(), mouseX, mouseY, 0, Theme.ACCENT_BLUE);
        button(g, FORMAT_X, BRUSH, open == Popup.FORMAT, state.plus(), mouseX, mouseY, 1, 0xFF000000 | formatPanel.primaryColor());

        if (state.plus() && !commandMode()) extractPreview(g, font, state);

        int bottom = screenHeight - POPUP_BOTTOM_OFFSET;
        switch (open) {
            case EMOJI -> {
                emojiPicker.layout(state, bottom);
                emojiPicker.extract(g, mouseX, mouseY);
            }
            case FORMAT -> {
                formatPanel.layout(state, input, bottom);
                formatPanel.extract(g, mouseX, mouseY);
            }
            default -> {
            }
        }

        if (overButton(mouseX, mouseY, EMOJI_X)) {
            g.setTooltipForNextFrame(font, state.plus() ? Component.literal("Эмодзи") : state.lockedHint(), mouseX, mouseY);
        } else if (overButton(mouseX, mouseY, FORMAT_X)) {
            g.setTooltipForNextFrame(font, state.plus() ? Component.literal("Форматирование текста") : state.lockedHint(), mouseX, mouseY);
        }
    }

    private void button(GuiGraphicsExtractor g, int bx, String[] icon, boolean active, boolean unlocked,
                        int mouseX, int mouseY, int index, int accent) {
        int by = buttonY();
        int offset = 0;
        if (shakeButton == index && Util.getMillis() < shakeUntil) {
            offset = (int) Math.round(Math.sin(Util.getMillis() / 25.0) * 1.5);
        }
        bx += offset;
        boolean hovered = overButton(mouseX, mouseY, bx - offset);

        if (active) {
            Draw.gradientRoundRect(g, bx, by, BUTTON, BUTTON, 3, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        } else {
            Draw.panel(g, bx, by, BUTTON, BUTTON, 3, hovered ? Theme.BUTTON_HOVER : 0xC0141626,
                    hovered && unlocked ? Theme.ACCENT_MID : Theme.PANEL_BORDER);
        }
        int color = active ? 0xFF0B0D1C : unlocked ? (hovered ? Theme.TEXT : Theme.TEXT_DIM) : Theme.TEXT_MUTED;
        Draw.icon(g, icon, bx + 2, by + 2, color, unlocked ? accent : Theme.TEXT_MUTED);

        if (!unlocked) {
            Draw.roundRect(g, bx + BUTTON - 6, by + BUTTON - 7, 7, 7, 1, 0xFF0B0D1C);
            Draw.icon(g, LOCK, bx + BUTTON - 5, by + BUTTON - 6, 0xFFFFC857, 0);
        }
    }

    private void extractPreview(GuiGraphicsExtractor g, Font font, ServerState state) {
        String value = input.getValue();
        if (!MiniPreview.hasTags(value)) return;
        if (!value.equals(previewSource)) {
            previewSource = value;
            preview = MiniPreview.render(value, state.formatTags());
        }

        int maxWidth = screenWidth - 12 - 16;
        List<FormattedCharSequence> lines = font.split(preview, maxWidth);
        if (lines.isEmpty()) return;
        FormattedCharSequence line = lines.getLast();
        int y = screenHeight - 27;
        int width = font.width(line) + 20;
        Draw.panel(g, 2, y, Math.min(width, screenWidth - 4), 12, 3, 0xE0101222, Theme.PANEL_BORDER);
        g.fillGradient(3, y + 2, 4, y + 10, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
        g.text(font, "»", 7, y + 2, Theme.TEXT_MUTED, false);
        g.text(font, line, 15, y + 2, 0xFFFFFFFF, true);
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        ServerState state = state();
        if (state == null) return false;
        double mx = event.x();
        double my = event.y();

        if (overButton(mx, my, EMOJI_X) || overButton(mx, my, FORMAT_X)) {
            if (event.button() != 1) return true;
            boolean emoji = overButton(mx, my, EMOJI_X);
            AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
            if (!state.plus()) {
                shakeButton = emoji ? 0 : 1;
                shakeUntil = Util.getMillis() + 350;
                return true;
            }
            if (commandMode()) return true;
            Popup target = emoji ? Popup.EMOJI : Popup.FORMAT;
            open = open == target ? Popup.NONE : target;
            return true;
        }

        if (my >= buttonY()) return false;

        switch (open) {
            case EMOJI -> {
                if (emojiPicker.contains(mx, my)) {
                    if (event.button() == 1 && emojiPicker.mouseClicked(mx, my, event.hasShiftDown(), input)) open = Popup.NONE;
                    return true;
                }
                open = Popup.NONE;
            }
            case FORMAT -> {
                if (formatPanel.contains(mx, my)) {
                    if (event.button() == 1) formatPanel.mouseClicked(mx, my);
                    return true;
                }
                formatPanel.mouseClicked(mx, my);
                open = Popup.NONE;
            }
            default -> {
            }
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event) {
        return open == Popup.FORMAT && formatPanel.mouseDragged(event.x(), event.y());
    }

    public boolean mouseReleased() {
        return open == Popup.FORMAT && formatPanel.mouseReleased();
    }

    public boolean mouseScrolled(double mx, double my, double scrollY) {
        if (open == Popup.EMOJI) return emojiPicker.mouseScrolled(mx, my, scrollY);
        return open == Popup.FORMAT && formatPanel.contains(mx, my);
    }

    public boolean keyPressed(KeyEvent event) {
        if (open == Popup.FORMAT && formatPanel.keyPressed(event.input())) return true;
        if (event.isEscape() && open != Popup.NONE) {
            open = Popup.NONE;
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return open == Popup.FORMAT && formatPanel.charTyped(event.codepoint());
    }
}
