package me.JAKOP.hinexFeatures.mod.dialog.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.dialog.DialogClient;
import me.JAKOP.hinexFeatures.mod.dialog.DialogRenderer;
import me.JAKOP.hinexFeatures.mod.dialog.DialogSpec;
import me.JAKOP.hinexFeatures.mod.dialog.DialogView;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import me.JAKOP.hinexFeatures.mod.util.Json;
import me.JAKOP.hinexFeatures.mod.vfx.VfxClock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class DialogEditorScreen extends Screen implements EditorForm.Host {

    private enum Tab {
        EDIT("Редактор"), TEMPLATES("Шаблоны"), SENT("Отправленные"), ANSWERS("Ответы");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final int[] PALETTE = {
            0xFFFFFF, 0xF2EADA, 0xFFD27A, 0xD4A25A, 0xC9A063, 0xFFAA00, 0xFF9A3C, 0xFF5555,
            0xB3001B, 0x8A0010, 0xFF8FC8, 0xB46CFF, 0x818FFF, 0x4FC3FF, 0x7FD4FF, 0x55FFFF,
            0x8CF0C0, 0x55FF55, 0x3E8E41, 0x9A6A3E, 0x5E432A, 0x2A1D13, 0x120D0A, 0x000000};
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private static @Nullable JsonObject draft;
    private static Tab tab = Tab.EDIT;
    private static String targets = "@a";
    private static String templateName = "";

    private EditorForm form;
    private @Nullable DialogView preview;
    private EditBox targetsBox;
    private EditBox nameBox;
    private @Nullable Component status;
    private double statusUntil;
    private int seenVersion = -1;
    private double listScroll;
    private int listContent;
    private final Set<String> expanded = new HashSet<>();
    private final List<Object[]> clickables = new ArrayList<>();
    private @Nullable String confirmDelete;
    private double confirmUntil;

    private @Nullable ColorSwatch paletteFor;
    private @Nullable IntConsumer paletteSet;
    private boolean paletteNone;
    private @Nullable EditBox paletteHex;
    private int paletteX;
    private int paletteY;

    private int formW;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    public DialogEditorScreen() {
        super(Component.literal("Редактор диалогов"));
    }

    private static Path draftFile() {
        return HinexFeaturesClient.platform().gameDir().resolve("hinex").resolve("dialog-draft.json");
    }

    private static JsonObject draft() {
        if (draft == null) {
            JsonObject saved = Json.read(draftFile());
            draft = saved != null ? saved : DialogSpec.example();
        }
        return draft;
    }

    @Override
    protected void init() {
        closePalette();
        clickables.clear();
        if (seenVersion < 0) {
            JsonObject refresh = new JsonObject();
            refresh.addProperty("action", "refresh");
            DialogClient.editorAction(refresh);
            seenVersion = DialogClient.editorVersion();
        }
        if (tab == Tab.EDIT) initEditor();
    }

    private void initEditor() {
        formW = Math.max(214, Math.min(340, (int) (width * 0.52F)));
        form = new EditorForm(this, font, draft());
        form.build(8, 30, formW, height - 38);

        previewX = 8 + formW + 10;
        previewW = width - previewX - 8;
        previewY = 30;
        previewH = Math.max(60, height - previewY - 100);

        int rowY = previewY + previewH + 6;
        targetsBox = new EditBox(font, previewX + 28, rowY, previewW - 28, 16, Component.literal("Кому"));
        targetsBox.setMaxLength(256);
        targetsBox.setValue(targets);
        targetsBox.setResponder(value -> targets = value);
        targetsBox.setTooltip(Tooltip.create(Component.literal("""
                Кому отправить (через запятую):
                @a — все, @a[tag=сюжет,distance=..30] — ванильный селектор
                tag:сюжет, radius:30, group:vip, perm:право, world:мир
                Ник1,Ник2 — по никам""")));
        addRenderableWidget(targetsBox);

        String[][] presets = {{"все", "@a"}, {"рядом", "radius:30"}, {"по тегу", "@a[tag=сюжет]"}, {"себе", "@s"}};
        int presetW = (previewW - 3 * 3) / 4;
        for (int i = 0; i < presets.length; i++) {
            String value = presets[i][1];
            HinexButton preset = new HinexButton(previewX + i * (presetW + 3), rowY + 19, presetW, 14,
                    Component.literal(presets[i][0]), HinexButton.Kind.SECONDARY, button -> {
                targetsBox.setValue(value);
                targets = value;
            });
            preset.setTooltip(Tooltip.create(Component.literal(value)));
            addRenderableWidget(preset);
        }

        int templateY = rowY + 37;
        boolean wide = previewW >= 150;
        int saveWidth = wide ? 58 : 18;
        nameBox = new EditBox(font, previewX, templateY, previewW - saveWidth - 3, 16, Component.literal("Шаблон"));
        nameBox.setMaxLength(48);
        nameBox.setValue(templateName);
        nameBox.setHint(Component.literal("имя шаблона").withColor(0x646C92));
        nameBox.setResponder(value -> templateName = value);
        addRenderableWidget(nameBox);
        HinexButton save = new HinexButton(previewX + previewW - saveWidth, templateY, saveWidth, 16,
                Component.literal(wide ? "Сохранить" : "★"), HinexButton.Kind.SECONDARY, button -> saveTemplate());
        save.setTooltip(Tooltip.create(Component.literal("Сохранить форму как шаблон на сервере (видят все админы)")));
        addRenderableWidget(save);
        int copyWidth = wide ? 76 : 18;
        addRenderableWidget(new HinexButton(previewX, templateY + 20, previewW - copyWidth - 3, 18, Component.literal("Отправить ▸"),
                HinexButton.Kind.PRIMARY, button -> send(targets)));
        HinexButton copy = new HinexButton(previewX + previewW - copyWidth, templateY + 20, copyWidth, 18,
                Component.literal(wide ? "Как команду" : "/"), HinexButton.Kind.SECONDARY, button -> copyCommand(draft()));
        copy.setTooltip(Tooltip.create(Component.literal("""
                Скопировать настроенный диалог как команду
                /dialog show <кому> {…} — для командных блоков,
                консоли, функций и других плагинов""")));
        addRenderableWidget(copy);

        HinexButton replay = new HinexButton(previewX + previewW - 34, previewY + 3, 31, 13, Component.literal("▶"),
                HinexButton.Kind.SECONDARY, button -> {
            if (preview != null) preview.restart();
        });
        replay.setTooltip(Tooltip.create(Component.literal("Проиграть появление заново")));
        addRenderableWidget(replay);
        HinexButton self = new HinexButton(previewX + previewW - 76, previewY + 3, 40, 13, Component.literal("себе"),
                HinexButton.Kind.SECONDARY, button -> send("@s"));
        self.setTooltip(Tooltip.create(Component.literal("Отправить себе — посмотреть в игре")));
        addRenderableWidget(self);

        rebuildPreview(false);
    }

    @Override
    public void add(AbstractWidget widget) {
        addWidget(widget);
    }

    @Override
    public void changed(boolean text) {
        rebuildPreview(text);
    }

    private void rebuildPreview(boolean text) {
        float age = preview != null ? preview.age() : 0.0F;
        try {
            preview = DialogView.of(draft().deepCopy());
            if (text) preview.setAge(preview.text.duration + 0.5F);
            else preview.setAge(age);
        } catch (RuntimeException e) {
            preview = null;
        }
    }

    private void send(String to) {
        JsonObject action = new JsonObject();
        action.addProperty("action", "send");
        action.addProperty("targets", to);
        action.add("dialog", DialogSpec.stripMeta(draft()));
        DialogClient.editorAction(action);
        setStatus(Component.literal("Отправка…").withColor(Theme.TEXT_DIM & 0xFFFFFF));
    }

    private void copyCommand(JsonObject dialog) {
        String json = COMPACT.toJson(DialogSpec.stripMeta(dialog));
        StringBuilder safe = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ' ' && i > 0 && json.charAt(i - 1) == ' ') safe.append("\\u0020");
            else safe.append(c);
        }
        String to = targets.replaceAll("\\s+", "");
        String command = "/dialog show " + (to.isEmpty() ? "@a" : to) + " " + safe;
        minecraft.keyboardHandler.setClipboard(command);
        setStatus(Component.literal(command.length() <= 256
                ? "Команда скопирована (" + command.length() + " симв.) — для чата, командного блока или консоли"
                : "Команда скопирована (" + command.length() + " симв.). В чат не влезет (лимит 256) — "
                + "вставляйте в командный блок, консоль или функцию датапака"));
    }

    private void saveTemplate() {
        String name = templateName.strip();
        if (name.isEmpty()) {
            setStatus(Component.literal("Введите имя шаблона").withColor(Theme.DANGER & 0xFFFFFF));
            nameBox.setFocused(true);
            setFocused(nameBox);
            return;
        }
        JsonObject action = new JsonObject();
        action.addProperty("action", "save");
        action.addProperty("name", name);
        action.add("dialog", DialogSpec.stripMeta(draft()));
        DialogClient.editorAction(action);
    }

    private void setStatus(Component text) {
        status = text;
        statusUntil = VfxClock.now() + 6.0;
    }

    public void showTab(int index) {
        switchTab(Tab.values()[Math.max(0, Math.min(Tab.values().length - 1, index))]);
    }

    private void switchTab(Tab next) {
        tab = next;
        listScroll = 0;
        rebuildWidgets();
    }

    private void load(JsonObject dialog, String name) {
        draft = DialogSpec.stripMeta(dialog);
        templateName = name;
        switchTab(Tab.EDIT);
    }

    @Override
    public void tick() {
        if (DialogClient.editorVersion() != seenVersion) {
            seenVersion = DialogClient.editorVersion();
            Component result = DialogClient.takeEditorResult();
            if (result != null) setStatus(result);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, Theme.SCRIM_TOP, Theme.SCRIM_BOTTOM);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        clickables.clear();
        drawHeader(g, mouseX, mouseY);
        switch (tab) {
            case EDIT -> drawEditor(g, mouseX, mouseY, a);
            case TEMPLATES -> drawTemplates(g, mouseX, mouseY);
            case SENT -> drawSent(g, mouseX, mouseY);
            case ANSWERS -> drawAnswers(g, mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        g.nextStratum();
        drawStatus(g);
        if (paletteFor != null) drawPalette(g, mouseX, mouseY, a);
    }

    private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Draw.gradientText("ДИАЛОГИ", Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE, true), 8, 9, 0xFFFFFFFF, true);
        int x = 8 + font.width("ДИАЛОГИ") + 16;
        for (Tab each : Tab.values()) {
            int w = font.width(each.title) + 12;
            boolean active = each == tab;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= 4 && mouseY < 20;
            if (active) Draw.gradientRoundRect(g, x, 4, w, 16, 3, Theme.ACCENT_BLUE, Theme.ACCENT_PURPLE);
            else if (hover) Draw.roundRect(g, x, 4, w, 16, 3, Theme.BUTTON_HOVER);
            g.text(font, each.title, x + 6, 8, active ? 0xFF0B0D1C : hover ? Theme.TEXT : Theme.TEXT_DIM, false);
            clickables.add(new Object[]{x, 4, w, 16, (Runnable) () -> switchTab(each)});
            x += w + 4;
        }
        String close = "✕";
        int closeX = width - 16;
        boolean hover = mouseX >= closeX - 3 && mouseX < closeX + 10 && mouseY >= 4 && mouseY < 20;
        g.text(font, close, closeX, 8, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
        clickables.add(new Object[]{closeX - 3, 4, 13, 16, (Runnable) this::onClose});
        g.fill(8, 23, width - 8, 24, 0x40FFFFFF);
    }

    private void drawStatus(GuiGraphicsExtractor g) {
        if (status == null || VfxClock.now() >= statusUntil) return;
        int maxWidth = tab == Tab.EDIT ? previewW : width - 40;
        List<FormattedCharSequence> lines = font.split(status, maxWidth - 10);
        int w = 0;
        for (FormattedCharSequence line : lines) w = Math.max(w, font.width(line));
        int h = lines.size() * 10 + 6;
        int centerX = tab == Tab.EDIT ? previewX + previewW / 2 : width / 2;
        int x = centerX - w / 2 - 5;
        int y = tab == Tab.EDIT ? previewY + previewH - h - 3 : height - 6 - h;
        Draw.panel(g, x, y, w + 10, h, 3, Theme.POPUP, Theme.ACCENT_MID);
        int ty = y + 4;
        for (FormattedCharSequence line : lines) {
            g.text(font, line, centerX - font.width(line) / 2, ty, 0xFFFFFFFF, true);
            ty += 10;
        }
    }

    private void drawEditor(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Draw.panel(g, 4, 27, formW + 8, height - 31, 4, Theme.PANEL, Theme.PANEL_BORDER);
        form.draw(g, mouseX, mouseY, a);

        int px = previewX;
        int py = previewY;
        g.fillGradient(px, py, px + previewW, py + previewH * 2 / 3, 0xFF3A5A86, 0xFF8FA9C8);
        g.fillGradient(px, py + previewH * 2 / 3, px + previewW, py + previewH, 0xFF3E6B32, 0xFF22401C);
        for (int i = 0; i < 6; i++) {
            int tx = px + (i * 53 + 17) % Math.max(1, previewW - 10);
            g.fill(tx, py + previewH * 2 / 3 - 9, tx + 6, py + previewH * 2 / 3, 0xFF2E4A26);
        }
        g.outline(px - 1, py - 1, previewW + 2, previewH + 2, Theme.PANEL_BORDER);
        g.text(font, "Предпросмотр", px + 4, py + 5, 0xC0FFFFFF, true);
        if (preview != null) {
            int width = preview.spec.width;
            DialogRenderer.Layout layout = DialogRenderer.layout(preview, font, width);
            float scale = Math.min(1.0F, Math.min((previewW - 12) / (float) width, (previewH - 24) / (float) layout.height));
            int w = Math.round(width * scale);
            int h = Math.round(layout.height * scale);
            int dx = px + (previewW - w) / 2;
            int dy = switch (preview.spec.position) {
                case "top" -> py + 20;
                case "center" -> py + (previewH - h) / 2 + 6;
                default -> py + previewH - h - 8;
            };
            dy = Math.max(py + 14, Math.min(py + previewH - h - 2, dy - Math.round(preview.spec.offset * scale)));
            float[] appear = DialogRenderer.appear(preview);
            g.enableScissor(px, py, px + previewW, py + previewH);
            g.pose().pushMatrix();
            g.pose().translate(dx + appear[0] * scale + w / 2.0F, dy + appear[1] * scale + h / 2.0F);
            g.pose().scale(scale * appear[2], scale * appear[2]);
            g.pose().translate(-width / 2.0F, -layout.height / 2.0F);
            DialogRenderer.draw(g, font, preview, 0, 0, width, appear[3], DialogRenderer.Mode.HUD, -1, 0.0F);
            g.pose().popMatrix();
            g.disableScissor();
            if (preview.age() > preview.text.duration + 4.0F) preview.restart();
        }
        int rowY = previewY + previewH + 6;
        g.text(font, "Кому", previewX, rowY + 4, Theme.TEXT_DIM, false);
    }

    private int listTop() {
        return 30;
    }

    private void drawTemplates(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Map<String, JsonObject> templates = DialogClient.editorData().templates();
        int top = listTop();
        Draw.panel(g, 8, top - 2, width - 16, height - top - 6, 4, Theme.PANEL, Theme.PANEL_BORDER);
        g.enableScissor(9, top - 1, width - 9, height - 9);
        int y = top + 4 - (int) listScroll;
        if (templates.isEmpty()) {
            g.centeredText(font, "Шаблонов пока нет — заполните форму в «Редакторе» и нажмите «Сохранить»", width / 2, height / 2, Theme.TEXT_MUTED);
        }
        for (Map.Entry<String, JsonObject> entry : templates.entrySet()) {
            String name = entry.getKey();
            JsonObject dialog = entry.getValue();
            boolean rowHover = mouseX >= 12 && mouseX < width - 12 && mouseY >= y && mouseY < y + 28 && mouseY >= top && mouseY < height - 8;
            if (rowHover) Draw.roundRect(g, 12, y, width - 24, 28, 3, Theme.BUTTON);
            g.text(font, name, 18, y + 4, Theme.TEXT, true);
            g.text(font, font.plainSubstrByWidth(snippet(dialog), width - 200), 18, y + 16, Theme.TEXT_MUTED, false);
            int bx = width - 18;
            bx = link(g, mouseX, mouseY, bx, y + 10, name.equals(confirmDelete) && VfxClock.now() < confirmUntil ? "точно удалить?" : "удалить",
                    Theme.DANGER, () -> deleteTemplate(name));
            bx = link(g, mouseX, mouseY, bx - 10, y + 10, "как команду", Theme.TEXT_DIM, () -> copyCommand(dialog));
            bx = link(g, mouseX, mouseY, bx - 10, y + 10, "себе", Theme.ACCENT_BLUE, () -> {
                JsonObject action = new JsonObject();
                action.addProperty("action", "send");
                action.addProperty("targets", "@s");
                action.add("dialog", dialog);
                DialogClient.editorAction(action);
            });
            link(g, mouseX, mouseY, bx - 10, y + 10, "открыть", Theme.ACCENT_PURPLE, () -> load(dialog, name));
            y += 32;
        }
        listContent = y + (int) listScroll - top;
        g.disableScissor();
    }

    private void deleteTemplate(String name) {
        if (name.equals(confirmDelete) && VfxClock.now() < confirmUntil) {
            JsonObject action = new JsonObject();
            action.addProperty("action", "delete");
            action.addProperty("name", name);
            DialogClient.editorAction(action);
            confirmDelete = null;
        } else {
            confirmDelete = name;
            confirmUntil = VfxClock.now() + 3.0;
        }
    }

    private void drawSent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<JsonObject> sent = DialogClient.editorData().sent();
        int top = listTop();
        Draw.panel(g, 8, top - 2, width - 16, height - top - 6, 4, Theme.PANEL, Theme.PANEL_BORDER);
        g.enableScissor(9, top - 1, width - 9, height - 9);
        int y = top + 4 - (int) listScroll;
        if (sent.isEmpty()) g.centeredText(font, "Ещё ничего не отправлено", width / 2, height / 2, Theme.TEXT_MUTED);
        for (JsonObject record : sent) {
            String id = Json.string(record, "id", "");
            JsonObject dialog = record.has("dialog") ? record.getAsJsonObject("dialog") : new JsonObject();
            JsonArray responses = Json.array(record, "responses");
            boolean open = expanded.contains(id);
            String head = when(record.get("time").getAsLong()) + "  ·  " + Json.string(record, "sender", "?") + " → "
                    + Json.string(record, "targets", "") + "  ·  " + (record.has("count") ? record.get("count").getAsInt() : 0) + " чел.";
            g.text(font, font.plainSubstrByWidth(head, width - 60), 16, y + 3, Theme.TEXT_MUTED, false);
            g.text(font, font.plainSubstrByWidth(snippet(dialog), width - 170), 16, y + 14, Theme.TEXT, true);
            int bx = width - 18;
            String answers = "ответов: " + responses.size() + (open ? " ▴" : " ▾");
            bx = link(g, mouseX, mouseY, bx, y + 14, answers, responses.isEmpty() ? Theme.TEXT_MUTED : Theme.ACCENT_BLUE, () -> {
                if (!expanded.remove(id)) expanded.add(id);
            });
            link(g, mouseX, mouseY, bx - 10, y + 14, "в редактор", Theme.ACCENT_PURPLE, () -> load(dialog, ""));
            y += 28;
            if (open) {
                for (JsonElement element : responses) {
                    JsonObject response = element.getAsJsonObject();
                    g.text(font, when(response.get("time").getAsLong()), 24, y, Theme.TEXT_MUTED, false);
                    g.text(font, Json.string(response, "name", "?"), 90, y, Theme.ACCENT_BLUE, false);
                    g.text(font, font.plainSubstrByWidth(Json.string(response, "answer", ""), width - 200), 180, y, Theme.TEXT, false);
                    y += 11;
                }
                if (responses.isEmpty()) {
                    g.text(font, "пока никто не ответил", 24, y, Theme.TEXT_MUTED, false);
                    y += 11;
                }
                y += 4;
            }
            g.fill(14, y - 2, width - 14, y - 1, 0x20FFFFFF);
        }
        listContent = y + (int) listScroll - top;
        g.disableScissor();
    }

    private void drawAnswers(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Object[]> all = new ArrayList<>();
        for (JsonObject record : DialogClient.editorData().sent()) {
            JsonObject dialog = record.has("dialog") ? record.getAsJsonObject("dialog") : new JsonObject();
            for (JsonElement element : Json.array(record, "responses")) all.add(new Object[]{element.getAsJsonObject(), dialog});
        }
        all.sort((a, b) -> Long.compare(((JsonObject) b[0]).get("time").getAsLong(), ((JsonObject) a[0]).get("time").getAsLong()));
        int top = listTop();
        Draw.panel(g, 8, top - 2, width - 16, height - top - 6, 4, Theme.PANEL, Theme.PANEL_BORDER);
        g.enableScissor(9, top - 1, width - 9, height - 9);
        int y = top + 4 - (int) listScroll;
        if (all.isEmpty()) g.centeredText(font, "Ответов пока нет", width / 2, height / 2, Theme.TEXT_MUTED);
        for (Object[] item : all) {
            JsonObject response = (JsonObject) item[0];
            JsonObject dialog = (JsonObject) item[1];
            g.text(font, when(response.get("time").getAsLong()), 16, y + 2, Theme.TEXT_MUTED, false);
            g.text(font, Json.string(response, "name", "?"), 82, y + 2, Theme.ACCENT_BLUE, true);
            String title = Json.string(dialog, "title", "");
            g.text(font, font.plainSubstrByWidth("«" + (title.isEmpty() ? snippet(dialog) : title) + "»", 120), 172, y + 2, Theme.TEXT_DIM, false);
            g.text(font, font.plainSubstrByWidth(Json.string(response, "answer", ""), Math.max(40, width - 320)), 300, y + 2, Theme.TEXT, false);
            y += 13;
        }
        listContent = y + (int) listScroll - top;
        g.disableScissor();
    }

    private int link(GuiGraphicsExtractor g, int mouseX, int mouseY, int right, int y, String text, int color, Runnable action) {
        int w = font.width(text);
        int x = right - w;
        boolean hover = mouseX >= x && mouseX < right && mouseY >= y - 2 && mouseY < y + 10 && mouseY >= listTop() && mouseY < height - 8;
        g.text(font, text, x, y, hover ? 0xFFFFFFFF : color, false);
        if (hover) g.fill(x, y + 9, right, y + 10, color);
        clickables.add(new Object[]{x, y - 2, w, 12, action});
        return x;
    }

    private static String snippet(JsonObject dialog) {
        String title = Json.string(dialog, "title", "");
        String text = Json.string(dialog, "text", "").replaceAll("<[^<>]{1,64}>", "").replace('\n', ' ');
        return (title.isEmpty() ? "" : title + ": ") + text;
    }

    private static String when(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(WHEN);
    }

    @Override
    public void openPalette(ColorSwatch swatch, IntSupplier get, IntConsumer set, boolean allowNone) {
        closePalette();
        paletteFor = swatch;
        paletteSet = set;
        paletteNone = allowNone;
        int w = 8 * 13 + 8;
        int h = 3 * 13 + 30;
        paletteX = Math.min(width - w - 4, swatch.getX());
        paletteY = swatch.getY() + 20 + h > height ? swatch.getY() - h - 4 : swatch.getY() + 20;
        paletteHex = new EditBox(font, paletteX + 6, paletteY + h - 20, 58, 14, Component.literal("hex"));
        paletteHex.setMaxLength(7);
        int current = get.getAsInt();
        paletteHex.setValue(current >= 0 ? DialogSpec.hex(current) : "");
        addWidget(paletteHex);
        setFocused(paletteHex);
    }

    private void closePalette() {
        if (paletteHex != null) removeWidget(paletteHex);
        paletteHex = null;
        paletteFor = null;
        paletteSet = null;
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int w = 8 * 13 + 8;
        int h = 3 * 13 + 30;
        Draw.roundRect(g, paletteX - 2, paletteY - 1, w + 4, h + 4, 5, 0x60000000);
        Draw.panel(g, paletteX, paletteY, w, h, 4, Theme.POPUP, Theme.ACCENT_MID);
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = paletteX + 4 + (i % 8) * 13;
            int cy = paletteY + 4 + (i / 8) * 13;
            boolean hover = mouseX >= cx && mouseX < cx + 12 && mouseY >= cy && mouseY < cy + 12;
            g.fill(cx, cy, cx + 12, cy + 12, hover ? 0xFFFFFFFF : 0xFF2A2E4A);
            g.fill(cx + 1, cy + 1, cx + 11, cy + 11, 0xFF000000 | PALETTE[i]);
        }
        if (paletteHex != null) {
            g.fill(paletteHex.getX() - 2, paletteHex.getY() - 2, paletteHex.getX() + paletteHex.getWidth() + 2,
                    paletteHex.getY() + paletteHex.getHeight() + 2, Theme.BUTTON);
            paletteHex.extractRenderState(g, mouseX, mouseY, a);
            int preview = DialogSpec.parseColor(paletteHex.getValue(), -1);
            int sx = paletteHex.getX() + paletteHex.getWidth() + 4;
            if (preview >= 0) g.fill(sx, paletteHex.getY(), sx + 12, paletteHex.getY() + 12, 0xFF000000 | preview);
            if (paletteNone) {
                String none = "нет";
                int nx = paletteX + w - 6 - font.width(none);
                g.text(font, none, nx, paletteHex.getY() + 3, Theme.DANGER, false);
            }
        }
    }

    private boolean clickPalette(double mx, double my) {
        int w = 8 * 13 + 8;
        int h = 3 * 13 + 30;
        if (mx < paletteX || mx >= paletteX + w || my < paletteY || my >= paletteY + h) {
            applyHex();
            closePalette();
            return true;
        }
        for (int i = 0; i < PALETTE.length; i++) {
            int cx = paletteX + 4 + (i % 8) * 13;
            int cy = paletteY + 4 + (i / 8) * 13;
            if (mx >= cx && mx < cx + 12 && my >= cy && my < cy + 12) {
                if (paletteSet != null) paletteSet.accept(PALETTE[i]);
                closePalette();
                return true;
            }
        }
        if (paletteNone && paletteHex != null && my >= paletteHex.getY() && mx >= paletteX + w - 30) {
            if (paletteSet != null) paletteSet.accept(-1);
            closePalette();
            return true;
        }
        return false;
    }

    private void applyHex() {
        if (paletteHex == null || paletteSet == null) return;
        int color = DialogSpec.parseColor(paletteHex.getValue(), -1);
        if (color >= 0) paletteSet.accept(color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (paletteFor != null) {
            if (clickPalette(mx, my)) return true;
            return paletteHex != null && paletteHex.mouseClicked(event, doubleClick);
        }
        for (Object[] clickable : clickables) {
            int x = (int) clickable[0];
            int y = (int) clickable[1];
            if (mx >= x && mx < x + (int) clickable[2] && my >= y && my < y + (int) clickable[3]) {
                ((Runnable) clickable[4]).run();
                return true;
            }
        }
        if (tab == Tab.EDIT && mx < 8 + formW + 4 && !form.contains(mx, my)) return false;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (paletteFor != null) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                applyHex();
                closePalette();
                return true;
            }
            if (event.isEscape()) {
                closePalette();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.EDIT) {
            if (form.contains(mouseX, mouseY) && !form.overTextArea(mouseX, mouseY)) {
                form.scroll(scrollY);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int visible = height - listTop() - 10;
        listScroll = Math.max(0, Math.min(Math.max(0, listContent - visible), listScroll - scrollY * 24));
        return true;
    }

    @Override
    public void removed() {
        if (draft != null) Json.write(draftFile(), draft);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
