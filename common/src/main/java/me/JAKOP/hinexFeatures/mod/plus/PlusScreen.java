package me.JAKOP.hinexFeatures.mod.plus;

import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.chat.ChatTools;
import me.JAKOP.hinexFeatures.mod.chat.EmojiPicker;
import me.JAKOP.hinexFeatures.mod.chat.FormatPanel;
import me.JAKOP.hinexFeatures.mod.chat.MiniPreview;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import me.JAKOP.hinexFeatures.mod.ui.HinexCheckbox;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlusScreen extends Screen {

    private static final int PAD = 10;
    private static final int BUTTON = 14;
    private static final List<Integer> NAMED = new ArrayList<>(MiniPreview.NAMED.values());
    private static final String CODES = "0123456789abcdef";

    private enum Popup { NONE, EMOJI, FORMAT }

    private final Screen parent;
    private final ColorPicker picker = new ColorPicker(this::previewColor, this::applyColor);
    private final EmojiPicker emojiPicker = new EmojiPicker();
    private final FormatPanel formatPanel = new FormatPanel("Суффикс слишком длинный");
    private Popup open = Popup.NONE;

    private EditBox suffixBox;
    private HinexButton applyButton;
    private HinexCheckbox joinBox;
    private int seenVersion;
    private int pendingColor = -1;
    private @Nullable Component status;
    private double statusUntil;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int innerX;
    private int innerW;
    private int previewY;
    private int pickerY;
    private int suffixY;
    private int emojiX;
    private int formatX;

    public PlusScreen(Screen parent) {
        super(Component.literal("Plus"));
        this.parent = parent;
    }

    private static PlusClient.Info info() {
        PlusClient.Info info = PlusClient.info();
        return info != null ? info : PlusClient.Info.EMPTY;
    }

    @Override
    protected void init() {
        PlusClient.Info info = info();
        seenVersion = PlusClient.version();
        panelW = Math.min(250, width - 12);
        innerW = panelW - 2 * PAD;
        panelX = (width - panelW) / 2;
        innerX = panelX + PAD;

        picker.setBounds(innerX, 0, innerW);
        panelH = 28 + 22 + 6 + 12 + 19 + picker.height() + 19 + BUTTON + 10;
        panelY = Math.max(4, (height - panelH) / 2);
        previewY = panelY + 28;
        int joinY = previewY + 22 + 6;
        pickerY = joinY + 12 + 19;
        picker.setBounds(innerX, pickerY, innerW);
        picker.setColor(info.color() >= 0 ? info.color() : 0xFFFFFF);
        pendingColor = -1;

        HinexButton reset = new HinexButton(picker.columnX(), pickerY + 40, ColorPicker.COLUMN_W, 16,
                Component.literal("Сбросить"), HinexButton.Kind.SECONDARY, button -> run("plus setcolor reset"));
        addRenderableWidget(reset);

        suffixY = pickerY + picker.height() + 19;
        emojiX = innerX;
        formatX = innerX + BUTTON + 3;
        int boxX = formatX + BUTTON + 4;
        int small = 20;
        suffixBox = new EditBox(font, boxX, suffixY, innerX + innerW - 2 * (small + 3) - boxX, BUTTON, Component.literal("Суффикс"));
        suffixBox.setMaxLength(Math.clamp(info.maxRaw(), 1, 256 - "plus setsuffix ".length()));
        suffixBox.setValue(info.suffix() != null ? info.suffix() : "");
        addRenderableWidget(suffixBox);
        applyButton = new HinexButton(innerX + innerW - 2 * small - 3, suffixY, small, BUTTON, Component.literal("✔"), HinexButton.Kind.PRIMARY,
                button -> applySuffix());
        applyButton.setTooltip(Tooltip.create(Component.literal("Сохранить")));
        addRenderableWidget(applyButton);
        HinexButton clear = new HinexButton(innerX + innerW - small, suffixY, small, BUTTON, Component.literal("✕"), HinexButton.Kind.SECONDARY,
                button -> {
                    suffixBox.setValue("");
                    run("plus setsuffix reset");
                });
        clear.setTooltip(Tooltip.create(Component.literal("Убрать суффикс")));
        addRenderableWidget(clear);

        joinBox = new HinexCheckbox(innerX, joinY, innerW, Component.literal("Все видят, когда я захожу и выхожу"),
                checked -> run("plus joinmessage " + (checked ? "on" : "off")));
        joinBox.setChecked(!info.hideJoin());
        addRenderableWidget(joinBox);
    }

    private void run(String command) {
        if (minecraft.player == null) return;
        minecraft.player.connection.sendCommand(command);
        setStatus(Component.literal("Сохраняю…").withColor(Theme.TEXT_DIM & 0xFFFFFF));
    }

    private void previewColor(int rgb) {
        pendingColor = rgb;
    }

    private void applyColor(int rgb) {
        pendingColor = rgb;
        int index = NAMED.indexOf(rgb);
        run(index >= 0 ? "plus setcolor " + CODES.charAt(index) : String.format(Locale.ROOT, "plus setcolor hex #%06X", rgb));
    }

    private void applySuffix() {
        String value = suffixBox.getValue().strip();
        if (visibleLength(value) > info().maxSuffix()) {
            setStatus(Component.literal("Не длиннее " + info().maxSuffix() + " символов").withColor(Theme.DANGER & 0xFFFFFF));
            return;
        }
        if (value.isEmpty()) run("plus setsuffix reset");
        else run("plus setsuffix " + value);
    }

    private void setStatus(Component text) {
        status = text;
        statusUntil = VfxClock.now() + 3.0;
    }

    private static Component suffix(String value, PlusClient.Info info) {
        return value.isEmpty() ? Component.empty() : MiniPreview.render(Legacy.toTags(value), info.tags());
    }

    private int visibleLength(String value) {
        String visible = suffix(value, info()).getString();
        return visible.codePointCount(0, visible.length());
    }

    private boolean suffixChanged() {
        String saved = info().suffix();
        return !suffixBox.getValue().strip().equals(saved == null ? "" : saved);
    }

    @Override
    public void tick() {
        if (!PlusClient.available()) {
            onClose();
            return;
        }
        if (PlusClient.version() != seenVersion) {
            seenVersion = PlusClient.version();
            PlusClient.Info info = info();
            if (!suffixBox.isFocused()) suffixBox.setValue(info.suffix() != null ? info.suffix() : "");
            joinBox.setChecked(!info.hideJoin());
            if (!picker.dragging()) {
                picker.setColor(info.color() >= 0 ? info.color() : 0xFFFFFF);
                pendingColor = -1;
            }
            setStatus(Component.literal("Сохранено ✔").withColor(0x7CFF8C));
        }
        applyButton.active = suffixChanged();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        PlusClient.Info info = info();
        boolean popup = open != Popup.NONE;
        int mx = popup ? -1 : mouseX;
        int my = popup ? -1 : mouseY;

        Draw.panel(g, panelX, panelY, panelW, panelH, 5, Theme.PANEL, 0xFF6B4A1E);
        Draw.gradientLine(g, panelX + 5, panelY, panelW - 10, 1, PlusButton.GOLD, PlusButton.PINK);

        Draw.icon(g, PlusButton.ICON, innerX, panelY + 8, PlusButton.GOLD, PlusButton.SPARK);
        g.text(font, Draw.gradientText("PLUS", PlusButton.GOLD, PlusButton.PINK, true), innerX + 20, panelY + 12, 0xFFFFFFFF, true);
        int closeX = panelX + panelW - 14;
        boolean closeHover = mx >= closeX - 3 && mx < closeX + 10 && my >= panelY + 6 && my < panelY + 20;
        g.text(font, "✕", closeX, panelY + 10, closeHover ? Theme.TEXT : Theme.TEXT_DIM, false);

        Draw.panel(g, innerX, previewY, innerW, 22, 3, Theme.PANEL_LIGHT, Theme.PANEL_BORDER);
        int rowX = innerX + 12;
        int rowW = innerW - 24;
        g.fill(rowX, previewY + 6, rowX + rowW, previewY + 16, 0x80000000);
        String value = suffixBox.getValue();
        MutableComponent preview = Component.empty().append(name(info));
        if (!value.isEmpty()) {
            if (info.space() && !Character.isWhitespace(value.charAt(0))) preview.append(" ");
            preview.append(suffix(value, info));
        }
        g.enableScissor(rowX, previewY + 6, rowX + rowW - 14, previewY + 16);
        g.text(font, preview, rowX + 2, previewY + 7, 0xFFFFFFFF, true);
        g.disableScissor();
        for (int bar = 0; bar < 5; bar++) {
            int barX = rowX + rowW - 12 + bar * 2;
            g.fill(barX, previewY + 13 - bar, barX + 1, previewY + 15, 0xFF55FF55);
        }

        g.text(font, "Цвет ника", innerX, pickerY - 11, Theme.TEXT_DIM, false);
        picker.extract(g, font, mx, my, pendingColor >= 0 ? pendingColor : info.color());

        g.text(font, "Суффикс", innerX, suffixY - 11, Theme.TEXT_DIM, false);
        int length = visibleLength(value);
        String counter = length + "/" + info.maxSuffix();
        g.text(font, counter, innerX + innerW - font.width(counter), suffixY - 11,
                length > info.maxSuffix() ? Theme.DANGER : Theme.TEXT_MUTED, false);
        toolButton(g, emojiX, ChatTools.SMILE, open == Popup.EMOJI, mx, my, Theme.ACCENT_BLUE);
        toolButton(g, formatX, ChatTools.BRUSH, open == Popup.FORMAT, mx, my, 0xFF000000 | formatPanel.primaryColor());

        super.extractRenderState(g, popup ? -1 : mouseX, popup ? -1 : mouseY, a);

        if (status != null && VfxClock.now() < statusUntil) {
            g.nextStratum();
            int w = font.width(status) + 12;
            int x = closeX - 8 - w;
            Draw.panel(g, x, panelY + 7, w, 14, 3, Theme.POPUP, 0xFF6B4A1E);
            g.text(font, status, x + 6, panelY + 10, 0xFFFFFFFF, false);
        }

        if (popup) {
            g.nextStratum();
            ServerState state = ClientNetwork.state();
            if (open == Popup.EMOJI) {
                emojiPicker.layout(state == null ? List.of() : state.emojis(), emojiX, suffixY - 3, suffixY + BUTTON + 3);
                emojiPicker.extract(g, mouseX, mouseY);
            } else {
                formatPanel.layout(info.tags(), suffixBox, emojiX, suffixY - 3, suffixY + BUTTON + 3);
                formatPanel.extract(g, mouseX, mouseY);
            }
        }
    }

    private void toolButton(GuiGraphicsExtractor g, int bx, String[] icon, boolean active, int mouseX, int mouseY, int accent) {
        boolean hovered = over(mouseX, mouseY, bx);
        if (active) Draw.gradientRoundRect(g, bx, suffixY, BUTTON, BUTTON, 3, PlusButton.GOLD, PlusButton.PINK);
        else Draw.panel(g, bx, suffixY, BUTTON, BUTTON, 3, hovered ? Theme.BUTTON_HOVER : Theme.PANEL_LIGHT,
                hovered ? PlusButton.GOLD : Theme.PANEL_BORDER);
        Draw.icon(g, icon, bx + 3, suffixY + 3, active ? 0xFF1C140A : hovered ? Theme.TEXT : Theme.TEXT_DIM, active ? 0xFF1C140A : accent);
    }

    private boolean over(double mx, double my, int bx) {
        return mx >= bx && mx < bx + BUTTON && my >= suffixY && my < suffixY + BUTTON;
    }

    private Component name(PlusClient.Info info) {
        String player = minecraft.getUser().getName();
        if (pendingColor >= 0) return Component.literal(player).withColor(pendingColor);
        if (info.colorCode() != null) return Legacy.parse(info.colorCode() + player);
        return Component.literal(player);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (over(mx, my, emojiX) || over(mx, my, formatX)) {
            if (event.button() != 1) return true;
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
            Popup target = over(mx, my, emojiX) ? Popup.EMOJI : Popup.FORMAT;
            open = open == target ? Popup.NONE : target;
            picker.stopEditing();
            focusSuffix();
            return true;
        }

        switch (open) {
            case EMOJI -> {
                if (emojiPicker.contains(mx, my)) {
                    if (event.button() == 1 && emojiPicker.mouseClicked(mx, my, event.hasShiftDown(), suffixBox)) open = Popup.NONE;
                    focusSuffix();
                    return true;
                }
                open = Popup.NONE;
            }
            case FORMAT -> {
                if (formatPanel.contains(mx, my)) {
                    if (event.button() == 1) formatPanel.mouseClicked(mx, my);
                    focusSuffix();
                    return true;
                }
                formatPanel.mouseClicked(mx, my);
                open = Popup.NONE;
            }
            default -> {
            }
        }

        int closeX = panelX + panelW - 14;
        if (mx >= closeX - 3 && mx < closeX + 10 && my >= panelY + 6 && my < panelY + 20) {
            onClose();
            return true;
        }
        if (event.button() == 1 && picker.mouseClicked(mx, my)) {
            setFocused(null);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void focusSuffix() {
        setFocused(suffixBox);
        suffixBox.setFocused(true);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (open == Popup.FORMAT && formatPanel.mouseDragged(event.x(), event.y())) return true;
        if (picker.mouseDragged(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (open == Popup.FORMAT && formatPanel.mouseReleased()) return true;
        if (picker.mouseReleased()) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (open == Popup.EMOJI && emojiPicker.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (open == Popup.FORMAT && formatPanel.contains(mouseX, mouseY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (open == Popup.FORMAT && formatPanel.keyPressed(event.input())) return true;
        if (picker.keyPressed(event.input())) return true;
        if (event.isEscape() && open != Popup.NONE) {
            open = Popup.NONE;
            return true;
        }
        if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && suffixBox.isFocused()) {
            applySuffix();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (open == Popup.FORMAT && formatPanel.charTyped(event.codepoint())) return true;
        if (picker.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
