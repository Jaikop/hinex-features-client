package me.JAKOP.hinexFeatures.mod.admin;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.dialog.editor.CycleButton;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WorkspaceScreen extends Screen {

    private static final int GRID = 10;
    private static final int PALETTE_W = 96;
    private static final int INSPECTOR_W = 150;
    private static final int LEFT_MOUSE = 1;
    private static final int RIGHT_MOUSE = 3;
    private static final int MULTILINE_H = 44;

    private static float camX = -40;
    private static float camY = -40;
    private static float zoom = 1.0F;
    private static boolean showLog;

    private enum Drag { NONE, MOVE, PAN, WIRE }

    private final List<Object[]> clickables = new ArrayList<>();
    private final List<AbstractWidget> inspector = new ArrayList<>();
    private @Nullable String selected;
    private @Nullable JsonObject editingProps;
    private boolean propsDirty;
    private int propsTimer;
    private int seenVersion = -1;
    private Drag drag = Drag.NONE;
    private @Nullable String dragNode;
    private int dragPort;
    private float dragOffsetX;
    private float dragOffsetY;
    private double panStartX;
    private double panStartY;
    private float panCamX;
    private float panCamY;
    private boolean dragMoved;
    private double mouseX;
    private double mouseY;
    private int paletteScroll;

    private int canvasX;
    private int canvasY;
    private int canvasW;
    private int canvasH;

    public WorkspaceScreen() {
        super(Component.literal("Общая схема"));
    }

    @Override
    protected void init() {
        WorkspaceClient.send("open", new JsonObject());
        canvasX = 8 + PALETTE_W + 6;
        canvasY = 44;
        layoutCanvas();

        addRenderableWidget(new HinexButton(8, 26, 110, 14,
                Component.literal(WorkspaceClient.running ? "⏸ Остановить схему" : "▶ Запустить схему"),
                WorkspaceClient.running ? HinexButton.Kind.SECONDARY : HinexButton.Kind.PRIMARY, b -> {
            JsonObject extra = new JsonObject();
            extra.addProperty("running", !WorkspaceClient.running);
            WorkspaceClient.send("run", extra);
            WorkspaceClient.running = !WorkspaceClient.running;
            rebuildWidgets();
        }));
        addRenderableWidget(new HinexButton(122, 26, 60, 14, Component.literal(showLog ? "Журнал ▴" : "Журнал ▾"), HinexButton.Kind.SECONDARY, b -> {
            showLog = !showLog;
            rebuildWidgets();
        }));
        addRenderableWidget(new HinexButton(186, 26, 50, 14, Component.literal("Центр"), HinexButton.Kind.SECONDARY, b -> center()));
        buildInspector();
    }

    private void layoutCanvas() {
        int right = selected != null && WorkspaceClient.NODES.containsKey(selected) ? width - 8 - INSPECTOR_W - 6 : width - 8;
        canvasW = Math.max(60, right - canvasX);
        canvasH = Math.max(40, height - 8 - canvasY - (showLog ? 74 : 0));
    }

    private void center() {
        if (WorkspaceClient.NODES.isEmpty()) {
            camX = -40;
            camY = -40;
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (WorkspaceClient.Node node : WorkspaceClient.NODES.values()) {
            minX = Math.min(minX, node.x);
            minY = Math.min(minY, node.y);
            maxX = Math.max(maxX, node.x + NodeTypes.width(node.type));
            maxY = Math.max(maxY, node.y + NodeTypes.height(node.type));
        }
        zoom = Math.max(0.4F, Math.min(1.5F, Math.min(canvasW / (float) (maxX - minX + 60), canvasH / (float) (maxY - minY + 60))));
        camX = (minX + maxX) / 2.0F - canvasW / 2.0F / zoom;
        camY = (minY + maxY) / 2.0F - canvasH / 2.0F / zoom;
    }

    private void buildInspector() {
        inspector.clear();
        WorkspaceClient.Node node = selected != null ? WorkspaceClient.NODES.get(selected) : null;
        if (node == null) {
            selected = null;
            editingProps = null;
            return;
        }
        editingProps = node.props.deepCopy();
        int x = width - 8 - INSPECTOR_W;
        int y = canvasY + 16;
        EditBox label = new EditBox(font, x, y + 10, INSPECTOR_W, 14, Component.literal("Подпись"));
        label.setMaxLength(60);
        label.setValue(node.str("label", ""));
        label.moveCursorToStart(false);
        label.setHint(Component.literal("подпись на блоке").withColor(0x646C92));
        label.setResponder(value -> prop("label", value));
        inspector.add(addRenderableWidget(label));
        y += 28;
        for (NodeTypes.Field field : NodeTypes.of(node.type).fields()) {
            if (field.key().equals("interval") || field.key().equals("always")) {
                if (!node.str("mode", "impulse").equals("repeat")) continue;
            }
            if (field.kind().equals("cycle")) {
                CycleButton cycle = new CycleButton(x, y + 10, INSPECTOR_W, 14, field.values(), field.labels(),
                        node.str(field.key(), field.fallback()), value -> {
                    prop(field.key(), value);
                    if (field.key().equals("mode")) {
                        sendProps();
                        rebuildWidgets();
                    }
                });
                inspector.add(addRenderableWidget(cycle));
            } else if (multiline(field)) {
                MultiLineEditBox area = MultiLineEditBox.builder().setShowDecorations(false)
                        .setPlaceholder(Component.literal(field.key().equals("command") ? "команда без /" : "текст").withColor(0x646C92))
                        .build(font, INSPECTOR_W, MULTILINE_H, Component.literal(field.label()));
                area.setPosition(x, y + 10);
                area.setCharacterLimit(field.key().equals("command") ? 1000 : 300);
                area.setValue(node.str(field.key(), field.fallback()));
                area.setValueListener(value -> {
                    if (value.indexOf('\n') >= 0) {
                        area.setValue(value.replace("\n", ""));
                        return;
                    }
                    prop(field.key(), value);
                });
                inspector.add(addRenderableWidget(area));
            } else {
                EditBox box = new EditBox(font, x, y + 10, INSPECTOR_W, 14, Component.literal(field.label()));
                box.setMaxLength(field.key().equals("command") ? 1000 : 120);
                box.setValue(node.str(field.key(), field.fallback()));
                box.moveCursorToStart(false);
                boolean numeric = field.kind().equals("int");
                box.setResponder(value -> {
                    if (numeric && !value.matches("-?\\d{0,9}")) return;
                    prop(field.key(), value);
                });
                inspector.add(addRenderableWidget(box));
            }
            y += fieldHeight(field);
        }
        if (node.type.equals("button")) {
            inspector.add(addRenderableWidget(new HinexButton(x, y + 6, INSPECTOR_W, 16, Component.literal("▶ Нажать"), HinexButton.Kind.PRIMARY,
                    b -> press(node))));
            y += 24;
        }
        if (node.type.equals("lever")) {
            inspector.add(addRenderableWidget(new HinexButton(x, y + 6, INSPECTOR_W, 16,
                    Component.literal(node.bool("on") ? "Выключить" : "Включить"), HinexButton.Kind.PRIMARY, b -> toggle(node))));
            y += 24;
        }
        inspector.add(addRenderableWidget(new HinexButton(x, y + 8, INSPECTOR_W, 14, Component.literal("Удалить блок"), HinexButton.Kind.LINK,
                b -> removeSelected())));
    }

    private static boolean multiline(NodeTypes.Field field) {
        return field.key().equals("command") || field.key().equals("text");
    }

    private static int fieldHeight(NodeTypes.Field field) {
        return multiline(field) ? MULTILINE_H + 14 : 28;
    }

    private void prop(String key, String value) {
        if (editingProps == null) return;
        editingProps.addProperty(key, value);
        propsDirty = true;
        propsTimer = 8;
        WorkspaceClient.Node node = selected != null ? WorkspaceClient.NODES.get(selected) : null;
        if (node != null) node.props = editingProps.deepCopy();
    }

    private void sendProps() {
        if (!propsDirty || selected == null || editingProps == null) return;
        propsDirty = false;
        JsonObject op = new JsonObject();
        op.addProperty("op", "update");
        op.addProperty("id", selected);
        op.add("props", editingProps.deepCopy());
        WorkspaceClient.edit(op);
    }

    private void select(@Nullable String id) {
        sendProps();
        if (id != null && id.equals(selected)) return;
        selected = id;
        rebuildWidgets();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private void addNode(String type, @Nullable String mode) {
        float cx = camX + canvasW / 2.0F / zoom - NodeTypes.width(type) / 2.0F;
        float cy = camY + canvasH / 2.0F / zoom - NodeTypes.height(type) / 2.0F;
        int x = snap(cx);
        int y = snap(cy);
        for (int guard = 0; guard < 20 && occupied(x, y); guard++) y += 50;
        WorkspaceClient.Node node = new WorkspaceClient.Node(newId(), type, x, y, NodeTypes.defaults(type, mode));
        WorkspaceClient.NODES.put(node.id, node);
        JsonObject op = new JsonObject();
        op.addProperty("op", "add");
        op.add("node", node.toJson());
        WorkspaceClient.edit(op);
        select(node.id);
    }

    private boolean occupied(int x, int y) {
        for (WorkspaceClient.Node node : WorkspaceClient.NODES.values()) {
            if (Math.abs(node.x - x) < 40 && Math.abs(node.y - y) < 30) return true;
        }
        return false;
    }

    private static int snap(float value) {
        return Math.round(value / GRID) * GRID;
    }

    private void removeSelected() {
        if (selected == null) return;
        JsonObject op = new JsonObject();
        op.addProperty("op", "remove");
        op.addProperty("id", selected);
        WorkspaceClient.NODES.remove(selected);
        String id = selected;
        WorkspaceClient.WIRES.values().removeIf(wire -> wire.from().equals(id) || wire.to().equals(id));
        WorkspaceClient.edit(op);
        propsDirty = false;
        selected = null;
        rebuildWidgets();
    }

    private void press(WorkspaceClient.Node node) {
        JsonObject extra = new JsonObject();
        extra.addProperty("id", node.id);
        WorkspaceClient.send("press", extra);
    }

    private void toggle(WorkspaceClient.Node node) {
        JsonObject extra = new JsonObject();
        extra.addProperty("id", node.id);
        WorkspaceClient.send("toggle", extra);
        node.props.addProperty("on", !node.bool("on"));
        if (node.id.equals(selected)) rebuildWidgets();
    }

    private float worldX(double screenX) {
        return camX + (float) (screenX - canvasX) / zoom;
    }

    private float worldY(double screenY) {
        return camY + (float) (screenY - canvasY) / zoom;
    }

    private boolean inCanvas(double x, double y) {
        return x >= canvasX && x < canvasX + canvasW && y >= canvasY && y < canvasY + canvasH;
    }

    private static float[] port(WorkspaceClient.Node node, boolean output, int index) {
        NodeTypes.Type type = NodeTypes.of(node.type);
        int count = output ? type.outputs() : type.inputs();
        int h = NodeTypes.height(node.type);
        float y = node.y + h * (index + 1) / (float) (count + 1);
        float x = output ? node.x + NodeTypes.width(node.type) : node.x;
        return new float[]{x, y};
    }

    private WorkspaceClient.@Nullable Node nodeAt(float wx, float wy) {
        WorkspaceClient.Node found = null;
        for (WorkspaceClient.Node node : WorkspaceClient.NODES.values()) {
            if (wx >= node.x && wx < node.x + NodeTypes.width(node.type) && wy >= node.y && wy < node.y + NodeTypes.height(node.type)) {
                found = node;
            }
        }
        return found;
    }

    private Object @Nullable [] portAt(float wx, float wy) {
        float reach = Math.max(4.0F, Math.min(9.0F, 6.0F / zoom));
        for (WorkspaceClient.Node node : WorkspaceClient.NODES.values()) {
            NodeTypes.Type type = NodeTypes.of(node.type);
            for (int i = 0; i < type.outputs(); i++) {
                float[] p = port(node, true, i);
                if (Math.abs(wx - p[0]) <= reach && Math.abs(wy - p[1]) <= reach) return new Object[]{node.id, true, i};
            }
            for (int i = 0; i < type.inputs(); i++) {
                float[] p = port(node, false, i);
                if (Math.abs(wx - p[0]) <= reach && Math.abs(wy - p[1]) <= reach) return new Object[]{node.id, false, i};
            }
        }
        return null;
    }

    private WorkspaceClient.@Nullable Wire wireAt(float wx, float wy) {
        float reach = Math.max(3.0F, 4.0F / zoom);
        for (WorkspaceClient.Wire wire : WorkspaceClient.WIRES.values()) {
            WorkspaceClient.Node from = WorkspaceClient.NODES.get(wire.from());
            WorkspaceClient.Node to = WorkspaceClient.NODES.get(wire.to());
            if (from == null || to == null) continue;
            float[] a = port(from, true, wire.fromPort());
            float[] b = port(to, false, wire.toPort());
            float mid = (a[0] + b[0]) / 2.0F;
            if (near(wx, wy, a[0], a[1], mid, a[1], reach) || near(wx, wy, mid, a[1], mid, b[1], reach)
                    || near(wx, wy, mid, b[1], b[0], b[1], reach)) {
                return wire;
            }
        }
        return null;
    }

    private static boolean near(float px, float py, float x0, float y0, float x1, float y1, float reach) {
        float minX = Math.min(x0, x1) - reach;
        float maxX = Math.max(x0, x1) + reach;
        float minY = Math.min(y0, y1) - reach;
        float maxY = Math.max(y0, y1) + reach;
        return px >= minX && px <= maxX && py >= minY && py <= maxY;
    }

    @Override
    public void tick() {
        if (propsTimer > 0 && --propsTimer == 0) sendProps();
        if (WorkspaceClient.version != seenVersion) {
            seenVersion = WorkspaceClient.version;
            if (selected != null && !WorkspaceClient.NODES.containsKey(selected)) {
                selected = null;
                rebuildWidgets();
            } else if (selected != null && editingProps != null && !propsDirty) {
                WorkspaceClient.Node node = WorkspaceClient.NODES.get(selected);
                if (node != null && !node.props.equals(editingProps)) rebuildWidgets();
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        AdminHeader.background(this, g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        clickables.clear();
        layoutCanvas();
        AdminHeader.draw(g, font, width, false, mouseX, mouseY, clickables, this::onClose);
        drawToolbar(g);
        drawPalette(g, mouseX, mouseY);
        drawCanvas(g, mouseX, mouseY);
        if (showLog) drawLog(g);
        drawInspectorLabels(g);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawToolbar(GuiGraphicsExtractor g) {
        String editors = WorkspaceClient.EDITORS.isEmpty() ? "" : "Сейчас в схеме: " + String.join(", ", WorkspaceClient.EDITORS);
        g.text(font, font.plainSubstrByWidth(editors, width - 250), 244, 29, Theme.TEXT_MUTED, false);
        String zoomText = Math.round(zoom * 100) + "%";
        g.text(font, zoomText, width - 8 - font.width(zoomText), 29, Theme.TEXT_MUTED, false);
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int top = canvasY;
        int bottom = height - 8;
        Draw.panel(g, 6, top - 2, PALETTE_W + 2, bottom - top + 4, 4, Theme.PANEL, Theme.PANEL_BORDER);
        g.enableScissor(7, top - 1, 7 + PALETTE_W, bottom + 1);
        int y = top + 2 - paletteScroll;
        for (String[] item : NodeTypes.PALETTE) {
            if (item[1] == null) {
                g.text(font, item[0], 10, y + 2, Theme.TEXT_MUTED, false);
                y += 12;
                continue;
            }
            WorkspaceClient.Node sample = new WorkspaceClient.Node("", item[1], 0, 0, NodeTypes.defaults(item[1], item[2]));
            boolean hover = mouseX >= 8 && mouseX < 6 + PALETTE_W && mouseY >= y && mouseY < y + 12 && mouseY >= top && mouseY < bottom;
            if (hover) Draw.roundRect(g, 8, y, PALETTE_W - 4, 12, 2, Theme.BUTTON_HOVER);
            g.fill(11, y + 3, 17, y + 9, NodeTypes.color(sample));
            g.text(font, font.plainSubstrByWidth(item[0], PALETTE_W - 20), 20, y + 2, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (y + 12 > top && y < bottom) {
                String type = item[1];
                String mode = item[2];
                clickables.add(new Object[]{8, Math.max(y, top), PALETTE_W - 4, Math.min(y + 12, bottom) - Math.max(y, top),
                        (Runnable) () -> addNode(type, mode)});
            }
            y += 13;
        }
        g.disableScissor();
    }

    private void drawCanvas(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, 0xFF0D1020);
        g.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        float step = GRID * zoom;
        if (step >= 5) {
            int firstX = (int) Math.floor(camX / GRID);
            int firstY = (int) Math.floor(camY / GRID);
            for (int i = firstX; ; i++) {
                int sx = Math.round(canvasX + (i * GRID - camX) * zoom);
                if (sx > canvasX + canvasW) break;
                g.fill(sx, canvasY, sx + 1, canvasY + canvasH, i % 10 == 0 ? 0x30FFFFFF : 0x12FFFFFF);
            }
            for (int j = firstY; ; j++) {
                int sy = Math.round(canvasY + (j * GRID - camY) * zoom);
                if (sy > canvasY + canvasH) break;
                g.fill(canvasX, sy, canvasX + canvasW, sy + 1, j % 10 == 0 ? 0x30FFFFFF : 0x12FFFFFF);
            }
        }

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(canvasX - camX * zoom, canvasY - camY * zoom);
        pose.scale(zoom, zoom);
        float wx = worldX(mouseX);
        float wy = worldY(mouseY);
        WorkspaceClient.Wire hoverWire = inCanvas(mouseX, mouseY) && drag == Drag.NONE ? wireAt(wx, wy) : null;
        for (WorkspaceClient.Wire wire : WorkspaceClient.WIRES.values()) {
            WorkspaceClient.Node from = WorkspaceClient.NODES.get(wire.from());
            WorkspaceClient.Node to = WorkspaceClient.NODES.get(wire.to());
            if (from == null || to == null) continue;
            boolean powered = WorkspaceClient.level(from.id, wire.fromPort());
            int color = wire == hoverWire ? 0xFFFFD27A : powered ? 0xFFFF3030 : 0xFF5A1C1C;
            float[] p0 = port(from, true, wire.fromPort());
            float[] p1 = port(to, false, wire.toPort());
            drawWire(g, p0[0], p0[1], p1[0], p1[1], color, powered);
        }
        for (WorkspaceClient.Node node : WorkspaceClient.NODES.values()) drawNode(g, node, wx, wy);
        if (drag == Drag.WIRE && dragNode != null) {
            WorkspaceClient.Node from = WorkspaceClient.NODES.get(dragNode);
            if (from != null) {
                float[] p0 = port(from, true, dragPort);
                drawWire(g, p0[0], p0[1], wx, wy, 0xFFFFD27A, false);
            }
        }
        pose.popMatrix();

        if (ClientNetwork.state() == null) {
            String[] help = {"Общая схема хранится на сервере.", "Здесь нет плагина HinexFeatures — схема недоступна.",
                    "Макросы на вкладке «Локально» работают везде."};
            int y = canvasY + canvasH / 2 - help.length * 6;
            for (String line : help) {
                g.centeredText(font, line, canvasX + canvasW / 2, y, Theme.TEXT_MUTED);
                y += 11;
            }
        } else if (WorkspaceClient.NODES.isEmpty()) {
            String[] help = {"Схема пуста — добавьте блоки из списка слева.",
                    "Тяните от выхода блока (справа) ко входу другого (слева), чтобы соединить.",
                    "ПКМ по проводу — удалить. Колесо — масштаб. Пустое место — двигать холст.",
                    "Командные блоки выполняют команды от консоли, как в мире."};
            int y = canvasY + canvasH / 2 - help.length * 6;
            for (String line : help) {
                for (FormattedCharSequence part : font.split(Component.literal(line), canvasW - 20)) {
                    g.centeredText(font, part, canvasX + canvasW / 2, y, Theme.TEXT_MUTED);
                    y += 11;
                }
            }
        }
        g.disableScissor();
        g.outline(canvasX - 1, canvasY - 1, canvasW + 2, canvasH + 2, Theme.PANEL_BORDER);
        if (!WorkspaceClient.running) {
            String paused = "Схема остановлена";
            g.text(font, paused, canvasX + canvasW - font.width(paused) - 6, canvasY + 4, Theme.DANGER, false);
        }
        if (hoverWire != null) g.setTooltipForNextFrame(font, Component.literal("ПКМ — удалить провод"), mouseX, mouseY);
    }

    private static void drawWire(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color, boolean powered) {
        float mid = (x0 + x1) / 2.0F;
        float t = powered ? 2.0F : 1.5F;
        rect(g, Math.min(x0, mid), y0 - t / 2, Math.max(x0, mid), y0 + t / 2, color);
        rect(g, mid - t / 2, Math.min(y0, y1), mid + t / 2, Math.max(y0, y1), color);
        rect(g, Math.min(mid, x1), y1 - t / 2, Math.max(mid, x1), y1 + t / 2, color);
    }

    private static void rect(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x0, y0);
        g.pose().scale(Math.max(0.01F, x1 - x0), Math.max(0.01F, y1 - y0));
        g.fill(0, 0, 1, 1, color);
        g.pose().popMatrix();
    }

    private void drawNode(GuiGraphicsExtractor g, WorkspaceClient.Node node, float wx, float wy) {
        int w = NodeTypes.width(node.type);
        int h = NodeTypes.height(node.type);
        int x = node.x;
        int y = node.y;
        int color = NodeTypes.color(node);
        boolean isSelected = node.id.equals(selected);
        boolean active = false;
        boolean[] levels = WorkspaceClient.LEVELS.get(node.id);
        if (levels != null) for (boolean level : levels) active |= level;

        if (node.type.equals("note")) {
            g.fill(x, y, x + w, y + h, 0xE0E8D98A);
            g.fill(x, y, x + w, y + 3, 0xFFD4C060);
            int ty = y + 6;
            for (FormattedCharSequence line : font.split(Component.literal(node.str("text", node.str("label", "Заметка"))), w - 8)) {
                if (ty > y + h - 9) break;
                g.text(font, line, x + 4, ty, 0xFF3A3020, false);
                ty += 10;
            }
            if (isSelected) g.outline(x - 1, y - 1, w + 2, h + 2, 0xFFFFFFFF);
            return;
        }

        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000);
        g.fill(x, y, x + w, y + h, 0xFF1A1D2E);
        g.fill(x, y, x + w, y + 11, color);
        g.fill(x, y + 11, x + w, y + 12, ARGB.scaleRGB(color, 0.6F));
        if (active) g.outline(x - 1, y - 1, w + 2, h + 2, 0xFFFF3030);
        if (isSelected) g.outline(x - 2, y - 2, w + 4, h + 4, 0xFFFFFFFF);
        g.text(font, font.plainSubstrByWidth(NodeTypes.title(node), w - 6), x + 3, y + 2, 0xFFFFFFFF, true);

        String label = node.str("label", "");
        String body = switch (node.type) {
            case "command" -> node.str("command", "");
            case "clock" -> "каждые " + node.str("period", "20") + " т";
            case "delay" -> node.str("ticks", "10") + " т";
            case "counter" -> "каждый " + node.str("count", "3") + "-й";
            case "random" -> node.str("chance", "50") + "%";
            case "event" -> eventTitle(node.str("event", "join")) + (node.str("filter", "").isEmpty() ? "" : ": " + node.str("filter", ""));
            case "world_in", "world_out" -> node.str("bx", "0") + " " + node.str("by", "64") + " " + node.str("bz", "0");
            case "lever" -> node.bool("on") ? "ВКЛ" : "выкл";
            default -> "";
        };
        int textColor = 0xFFD8DCEE;
        if (!label.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(label, w - 8), x + 4, y + 15, 0xFFFFE9B0, false);
            if (!body.isEmpty() && h > 36) g.text(font, font.plainSubstrByWidth(body, w - 8), x + 4, y + 26, 0xFFA8ACC0, false);
        } else if (!body.isEmpty()) {
            if (node.type.equals("command")) {
                g.fill(x + 3, y + 14, x + w - 3, y + h - 4, 0xFF0A0C14);
                int ty = y + 16;
                for (FormattedCharSequence line : font.split(Component.literal("/" + body), w - 10)) {
                    if (ty > y + h - 13) break;
                    g.text(font, line, x + 5, ty, 0xFFB8F0B8, false);
                    ty += 10;
                }
            } else {
                g.text(font, font.plainSubstrByWidth(body, w - 8), x + 4, y + 17, textColor, false);
            }
        }

        if (node.type.equals("button") || node.type.equals("lever")) {
            int bx = x + w - 14;
            int by = y + h - 14;
            boolean on = node.type.equals("lever") ? node.bool("on") : active;
            boolean hover = wx >= bx && wx < bx + 11 && wy >= by && wy < by + 11;
            g.fill(bx, by, bx + 11, by + 11, hover ? 0xFFFFD27A : on ? 0xFFFF3030 : 0xFF404656);
            g.text(font, node.type.equals("button") ? "▶" : on ? "I" : "O", bx + 3, by + 2, 0xFF0B0D1C, false);
        }

        NodeTypes.Type type = NodeTypes.of(node.type);
        for (int i = 0; i < type.inputs(); i++) {
            float[] p = port(node, false, i);
            int px = Math.round(p[0]);
            int py = Math.round(p[1]);
            g.fill(px - 3, py - 3, px + 3, py + 3, 0xFF8890B0);
            g.fill(px - 2, py - 2, px + 2, py + 2, 0xFF1A1D2E);
            String name = i < type.inputLabels().size() ? type.inputLabels().get(i) : "";
            if (!name.isEmpty()) g.text(font, name, px + 4, py - 4, 0xFF8890B0, false);
        }
        for (int i = 0; i < type.outputs(); i++) {
            float[] p = port(node, true, i);
            int px = Math.round(p[0]);
            int py = Math.round(p[1]);
            boolean on = WorkspaceClient.level(node.id, i);
            g.fill(px - 3, py - 3, px + 3, py + 3, on ? 0xFFFF3030 : 0xFF8890B0);
            String name = i < type.outputLabels().size() ? type.outputLabels().get(i) : "";
            if (!name.isEmpty()) g.text(font, name, px - 4 - font.width(name), py - 4, on ? 0xFFFF6060 : 0xFF8890B0, false);
        }
    }

    private static String eventTitle(String event) {
        return switch (event) {
            case "quit" -> "вышел";
            case "death" -> "умер";
            case "respawn" -> "возродился";
            case "chat" -> "чат";
            case "trigger" -> "/hevent";
            default -> "зашёл";
        };
    }

    private void drawLog(GuiGraphicsExtractor g) {
        int top = canvasY + canvasH + 6;
        int bottom = height - 8;
        Draw.panel(g, canvasX - 1, top - 2, canvasW + 2, bottom - top + 4, 3, Theme.PANEL, Theme.PANEL_BORDER);
        List<JsonObject> log = WorkspaceClient.LOG;
        int lines = (bottom - top) / 10;
        int y = top + 1;
        if (log.isEmpty()) g.text(font, "Журнал пуст: здесь появятся выполненные команды", canvasX + 4, y, Theme.TEXT_MUTED, false);
        for (int i = Math.max(0, log.size() - lines); i < log.size(); i++) {
            JsonObject entry = log.get(i);
            boolean ok = entry.has("ok") && entry.get("ok").getAsBoolean();
            String note = entry.has("note") ? "  (" + entry.get("note").getAsString() + ")" : "";
            String label = entry.get("label").getAsString();
            WorkspaceClient.Node source = WorkspaceClient.NODES.get(entry.has("node") ? entry.get("node").getAsString() : "");
            boolean named = source != null && !source.str("label", "").isBlank();
            String text = entry.get("time").getAsString() + "  " + (ok ? "✓" : "✗") + "  " + (named ? "[" + label + "]  " : "") + "/"
                    + entry.get("command").getAsString() + note;
            g.text(font, font.plainSubstrByWidth(text, canvasW - 8), canvasX + 4, y, ok ? Theme.TEXT_DIM : Theme.DANGER, false);
            y += 10;
        }
    }

    private void drawInspectorLabels(GuiGraphicsExtractor g) {
        WorkspaceClient.Node node = selected != null ? WorkspaceClient.NODES.get(selected) : null;
        if (node == null) return;
        int x = width - 8 - INSPECTOR_W;
        Draw.panel(g, x - 4, canvasY - 2, INSPECTOR_W + 8, height - 8 - canvasY + 4, 4, Theme.PANEL, Theme.PANEL_BORDER);
        g.text(font, NodeTypes.title(node), x, canvasY + 3, NodeTypes.color(node) | 0xFF000000, true);
        int y = canvasY + 16;
        g.text(font, "Подпись", x, y, Theme.TEXT_MUTED, false);
        y += 28;
        for (NodeTypes.Field field : NodeTypes.of(node.type).fields()) {
            if ((field.key().equals("interval") || field.key().equals("always")) && !node.str("mode", "impulse").equals("repeat")) continue;
            g.text(font, field.label(), x, y, Theme.TEXT_MUTED, false);
            y += fieldHeight(field);
        }
        int hintY = Math.max(y + 34, height - 60);
        for (FormattedCharSequence line : font.split(Component.literal(NodeTypes.of(node.type).hint()), INSPECTOR_W)) {
            if (hintY > height - 12) break;
            g.text(font, line, x, hintY, Theme.TEXT_MUTED, false);
            hintY += 10;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (AdminHeader.click(clickables, mx, my)) return true;
        if (super.mouseClicked(event, doubleClick)) return true;
        if (!inCanvas(mx, my)) return false;
        float wx = worldX(mx);
        float wy = worldY(my);
        int button = event.button();
        if (button == RIGHT_MOUSE) {
            WorkspaceClient.Wire wire = wireAt(wx, wy);
            if (wire != null) {
                JsonObject op = new JsonObject();
                op.addProperty("op", "disconnect");
                op.addProperty("id", wire.id());
                WorkspaceClient.WIRES.remove(wire.id());
                WorkspaceClient.edit(op);
                return true;
            }
            startPan(mx, my);
            return true;
        }
        if (button != LEFT_MOUSE) return false;
        WorkspaceClient.Node node = nodeAt(wx, wy);
        if (node != null && (node.type.equals("button") || node.type.equals("lever"))) {
            int bx = node.x + NodeTypes.width(node.type) - 14;
            int by = node.y + NodeTypes.height(node.type) - 14;
            if (wx >= bx && wx < bx + 11 && wy >= by && wy < by + 11) {
                if (node.type.equals("button")) press(node);
                else toggle(node);
                return true;
            }
        }
        Object[] port = portAt(wx, wy);
        if (port != null && (boolean) port[1]) {
            drag = Drag.WIRE;
            dragNode = (String) port[0];
            dragPort = (int) port[2];
            return true;
        }
        if (node != null) {
            select(node.id);
            drag = Drag.MOVE;
            dragNode = node.id;
            dragOffsetX = wx - node.x;
            dragOffsetY = wy - node.y;
            dragMoved = false;
            return true;
        }
        select(null);
        startPan(mx, my);
        return true;
    }

    private void startPan(double mx, double my) {
        drag = Drag.PAN;
        panStartX = mx;
        panStartY = my;
        panCamX = camX;
        panCamY = camY;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        switch (drag) {
            case PAN -> {
                camX = panCamX - (float) (event.x() - panStartX) / zoom;
                camY = panCamY - (float) (event.y() - panStartY) / zoom;
                return true;
            }
            case MOVE -> {
                WorkspaceClient.Node node = dragNode != null ? WorkspaceClient.NODES.get(dragNode) : null;
                if (node != null) {
                    node.x = snap(worldX(event.x()) - dragOffsetX);
                    node.y = snap(worldY(event.y()) - dragOffsetY);
                    dragMoved = true;
                }
                return true;
            }
            case WIRE -> {
                return true;
            }
            default -> {
                return super.mouseDragged(event, dx, dy);
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag == Drag.MOVE && dragMoved && dragNode != null) {
            WorkspaceClient.Node node = WorkspaceClient.NODES.get(dragNode);
            if (node != null) {
                JsonObject op = new JsonObject();
                op.addProperty("op", "update");
                op.addProperty("id", node.id);
                op.addProperty("x", node.x);
                op.addProperty("y", node.y);
                WorkspaceClient.edit(op);
            }
        } else if (drag == Drag.WIRE && dragNode != null) {
            Object[] port = portAt(worldX(event.x()), worldY(event.y()));
            if (port != null && !(boolean) port[1] && !port[0].equals(dragNode)) {
                String id = newId();
                WorkspaceClient.WIRES.put(id, new WorkspaceClient.Wire(id, dragNode, dragPort, (String) port[0], (int) port[2]));
                JsonObject op = new JsonObject();
                op.addProperty("op", "connect");
                op.addProperty("id", id);
                op.addProperty("from", dragNode);
                op.addProperty("fromPort", dragPort);
                op.addProperty("to", (String) port[0]);
                op.addProperty("toPort", (int) port[2]);
                WorkspaceClient.edit(op);
            }
        }
        drag = Drag.NONE;
        dragNode = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx < canvasX - 2) {
            int max = Math.max(0, NodeTypes.PALETTE.size() * 13 - (height - canvasY - 12));
            paletteScroll = (int) Math.max(0, Math.min(max, paletteScroll - scrollY * 14));
            return true;
        }
        if (!inCanvas(mx, my)) return super.mouseScrolled(mx, my, scrollX, scrollY);
        float beforeX = worldX(mx);
        float beforeY = worldY(my);
        zoom = Math.max(0.35F, Math.min(2.5F, zoom * (scrollY > 0 ? 1.12F : 1.0F / 1.12F)));
        camX = beforeX - (float) (mx - canvasX) / zoom;
        camY = beforeY - (float) (my - canvasY) / zoom;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean typing = getFocused() instanceof EditBox || getFocused() instanceof MultiLineEditBox;
        if (!typing && selected != null && event.key() == InputConstants.KEY_DELETE) {
            removeSelected();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        sendProps();
        WorkspaceClient.send("close", new JsonObject());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public int[] toScreen(float wx, float wy) {
        return new int[]{Math.round(canvasX + (wx - camX) * zoom), Math.round(canvasY + (wy - camY) * zoom)};
    }

    public int @Nullable [] portOnScreen(String id, boolean output, int index) {
        WorkspaceClient.Node node = WorkspaceClient.NODES.get(id);
        if (node == null) return null;
        float[] p = port(node, output, index);
        return toScreen(p[0], p[1]);
    }

    public void fit() {
        center();
    }

    public void selectNode(@Nullable String id) {
        select(id);
    }

    public void setLogVisible(boolean visible) {
        showLog = visible;
        rebuildWidgets();
    }
}
