package me.JAKOP.hinexFeatures.mod.admin;

import me.JAKOP.hinexFeatures.mod.dialog.editor.CycleButton;
import me.JAKOP.hinexFeatures.mod.ui.Draw;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MacroScreen extends Screen {

    private static final int ROW = 20;
    private static @Nullable String selectedId;

    private final List<Object[]> clickables = new ArrayList<>();
    private final List<List<AbstractWidget>> stepRows = new ArrayList<>();
    private @Nullable String capture;
    private int listScroll;
    private int stepScroll;
    private boolean dirty;
    private int listW;
    private int editX;
    private int editW;
    private int stepsTop;
    private int stepsBottom;

    public MacroScreen() {
        super(Component.literal("Панель управления"));
    }

    public static void preselect(String id) {
        selectedId = id;
    }

    private @Nullable Macro selected() {
        for (Macro macro : MacroStore.macros()) if (macro.id.equals(selectedId)) return macro;
        return MacroStore.macros().isEmpty() ? null : MacroStore.macros().getFirst();
    }

    @Override
    protected void init() {
        stepRows.clear();
        listW = Math.max(120, Math.min(170, (int) (width * 0.3F)));
        editX = 8 + listW + 10;
        editW = width - editX - 8;
        Macro macro = selected();
        if (macro != null) selectedId = macro.id;

        addRenderableWidget(new HinexButton(8, height - 66, listW, 16, Component.literal("+ Новый макрос"), HinexButton.Kind.SECONDARY, b -> {
            Macro created = Macro.create("Макрос " + (MacroStore.macros().size() + 1));
            MacroStore.macros().add(created);
            selectedId = created.id;
            changed();
            rebuildWidgets();
        }));
        if (macro == null) return;

        EditBox name = new EditBox(font, editX + 56, 28, editW - 56, 16, Component.literal("Название"));
        name.setMaxLength(48);
        name.setValue(macro.name);
        name.setResponder(value -> {
            macro.name = value;
            changed();
        });
        addRenderableWidget(name);

        int third = (editW - 56 - 8) / 3;
        boolean narrow = third < 96;
        HinexButton key = new HinexButton(editX + 56, 48, third, 16,
                Component.literal("macro".equals(capture) ? "нажмите…" : (narrow ? "клав.: " : "клавиша: ")
                        + (macro.key == null ? "нет" : macro.key.display())),
                HinexButton.Kind.SECONDARY, b -> {
            capture = "macro".equals(capture) ? null : "macro";
            rebuildWidgets();
        });
        key.setTooltip(Tooltip.create(Component.literal("Нажмите и затем клавишу (можно с Ctrl/Shift/Alt).\nEsc — убрать клавишу.\n"
                + "Повторное нажатие клавиши в игре останавливает макрос.")));
        addRenderableWidget(key);
        CycleButton quick = new CycleButton(editX + 56 + third + 4, 48, third, 16, List.of("true", "false"),
                narrow ? List.of("★ в меню", "☆ не в меню") : List.of("★ в быстром меню", "☆ не в быстром меню"),
                String.valueOf(macro.quick), value -> {
            macro.quick = Boolean.parseBoolean(value);
            changed();
        });
        HotKey quickKey = MacroStore.quickKey();
        quick.setTooltip(Tooltip.create(Component.literal("Показывать в быстром меню"
                + (quickKey != null ? " (держите " + quickKey.display() + ")" : ""))));
        addRenderableWidget(quick);
        CycleButton repeat = new CycleButton(editX + 56 + third * 2 + 8, 48, third, 16,
                List.of("1", "2", "3", "5", "10", "0"), List.of("один раз", "2 раза", "3 раза", "5 раз", "10 раз", "по кругу"),
                String.valueOf(macro.repeat), value -> {
            macro.repeat = Integer.parseInt(value);
            changed();
        });
        repeat.setTooltip(Tooltip.create(Component.literal("Сколько раз повторить всю цепочку.\n«По кругу» — пока не остановите.")));
        addRenderableWidget(repeat);

        int addW = (editW - 56 - 8) / 3;
        String[][] kinds = {{"command", "+ команда"}, {"chat", "+ сообщение"}, {"wait", "+ пауза"}};
        for (int i = 0; i < kinds.length; i++) {
            String type = kinds[i][0];
            addRenderableWidget(new HinexButton(editX + 56 + i * (addW + 4), 70, addW, 14, Component.literal(kinds[i][1]),
                    HinexButton.Kind.SECONDARY, b -> {
                macro.steps.add(new Macro.Step(type, type.equals("wait") ? "" : "", type.equals("wait") ? 1.0 : 0.0));
                changed();
                stepScroll = Integer.MAX_VALUE;
                rebuildWidgets();
            }));
        }

        stepsTop = 90;
        stepsBottom = height - 40;
        for (int i = 0; i < macro.steps.size(); i++) stepRows.add(stepRow(macro, i));
        int visibleRows = Math.max(1, (stepsBottom - stepsTop) / ROW);
        stepScroll = Math.max(0, Math.min(stepScroll, macro.steps.size() - visibleRows));
        layoutSteps();

        boolean running = MacroRunner.running(macro);
        addRenderableWidget(new HinexButton(editX, height - 22, 110, 16, Component.literal(running ? "■ Остановить" : "▶ Запустить"),
                running ? HinexButton.Kind.SECONDARY : HinexButton.Kind.PRIMARY, b -> {
            if (MacroRunner.running(macro)) MacroRunner.stop(macro);
            else MacroRunner.start(macro);
            rebuildWidgets();
        }));
        addRenderableWidget(new HinexButton(editX + editW - 90, height - 22, 90, 16, Component.literal("Удалить макрос"),
                HinexButton.Kind.LINK, b -> {
            MacroRunner.stop(macro);
            MacroStore.macros().remove(macro);
            selectedId = null;
            changed();
            rebuildWidgets();
        }));
    }

    private List<AbstractWidget> stepRow(Macro macro, int index) {
        Macro.Step step = macro.steps.get(index);
        List<AbstractWidget> row = new ArrayList<>();
        CycleButton type = new CycleButton(0, 0, 54, 16, List.of("command", "chat", "wait"), List.of("команда", "сообщение", "пауза"),
                step.type, value -> {
            step.type = value;
            changed();
        });
        row.add(addRenderableWidget(type));
        EditBox text = new EditBox(font, 0, 0, stepTextWidth(), 16, Component.literal("Шаг"));
        text.setMaxLength(256);
        text.setValue(step.text);
        text.moveCursorToStart(false);
        text.setHint(Component.literal(step.type.equals("chat") ? "текст в чат" : step.type.equals("wait") ? "(ничего — просто пауза)" : "time set night")
                .withColor(0x646C92));
        text.setResponder(value -> {
            step.text = value;
            changed();
        });
        row.add(addRenderableWidget(text));
        EditBox delay = new EditBox(font, 0, 0, 34, 16, Component.literal("Пауза"));
        delay.setMaxLength(6);
        delay.setValue(format(step.delay));
        delay.setTooltip(Tooltip.create(Component.literal("Пауза после шага, секунды (можно 0.5)")));
        delay.setResponder(value -> {
            try {
                step.delay = Math.max(0.0, Math.min(3600.0, Double.parseDouble(value.replace(',', '.'))));
                changed();
            } catch (NumberFormatException ignored) {
            }
        });
        row.add(addRenderableWidget(delay));
        row.add(addRenderableWidget(new HinexButton(0, 0, 12, 16, Component.literal("↑"), HinexButton.Kind.SECONDARY, b -> move(macro, index, -1))));
        row.add(addRenderableWidget(new HinexButton(0, 0, 12, 16, Component.literal("↓"), HinexButton.Kind.SECONDARY, b -> move(macro, index, 1))));
        row.add(addRenderableWidget(new HinexButton(0, 0, 12, 16, Component.literal("✕"), HinexButton.Kind.SECONDARY, b -> {
            macro.steps.remove(index);
            changed();
            rebuildWidgets();
        })));
        return row;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }

    private void move(Macro macro, int index, int direction) {
        int target = index + direction;
        if (target < 0 || target >= macro.steps.size()) return;
        Macro.Step step = macro.steps.remove(index);
        macro.steps.add(target, step);
        changed();
        rebuildWidgets();
    }

    private int stepTextWidth() {
        return Math.max(40, editW - 22 - 58 - 4 - 34 - 4 - 3 * 14);
    }

    private void layoutSteps() {
        int textW = stepTextWidth();
        for (int i = 0; i < stepRows.size(); i++) {
            int y = stepsTop + (i - stepScroll) * ROW;
            boolean visible = y >= stepsTop && y + 16 <= stepsBottom;
            List<AbstractWidget> row = stepRows.get(i);
            int[] xs = {editX + 22, editX + 22 + 58, editX + 22 + 58 + textW + 4, 0, 0, 0};
            xs[3] = xs[2] + 38;
            xs[4] = xs[3] + 14;
            xs[5] = xs[4] + 14;
            row.get(1).setWidth(textW);
            for (int k = 0; k < row.size(); k++) {
                AbstractWidget widget = row.get(k);
                widget.setX(xs[k]);
                widget.setY(y);
                widget.visible = visible;
            }
        }
    }

    private void changed() {
        dirty = true;
    }

    @Override
    public void tick() {
        if (dirty) {
            dirty = false;
            MacroStore.save();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        AdminHeader.background(this, g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        clickables.clear();
        AdminHeader.draw(g, font, width, true, mouseX, mouseY, clickables, this::onClose);
        drawList(g, mouseX, mouseY);
        drawKeys(g, mouseX, mouseY);
        Macro macro = selected();
        if (macro == null) {
            g.centeredText(font, "Макросов нет — создайте первый слева", editX + editW / 2, height / 2, Theme.TEXT_MUTED);
        } else {
            g.text(font, "Название", editX, 32, Theme.TEXT_DIM, false);
            g.text(font, "Запуск", editX, 52, Theme.TEXT_DIM, false);
            g.text(font, "Шаги", editX, 73, Theme.TEXT_DIM, false);
            Draw.panel(g, editX - 2, stepsTop - 3, editW + 4, stepsBottom - stepsTop + 4, 3, 0x80101224, Theme.PANEL_BORDER);
            for (int i = 0; i < stepRows.size(); i++) {
                int y = stepsTop + (i - stepScroll) * ROW;
                if (y < stepsTop || y + 16 > stepsBottom) continue;
                g.text(font, String.valueOf(i + 1), editX + 4, y + 4, Theme.TEXT_MUTED, false);
            }
            if (macro.steps.isEmpty()) g.centeredText(font, "Добавьте шаги кнопками выше", editX + editW / 2, stepsTop + 20, Theme.TEXT_MUTED);
            if (stepRows.size() * ROW > stepsBottom - stepsTop) {
                g.text(font, "колесо — прокрутка", editX + editW - font.width("колесо — прокрутка"), 73, Theme.TEXT_MUTED, false);
            }
            g.text(font, "{me} {x} {y} {z} {target} {look} · пауза — после шага, сек", editX, height - 36, Theme.TEXT_MUTED, false);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int top = 28;
        int bottom = height - 70;
        Draw.panel(g, 6, top - 2, listW + 4, bottom - top + 4, 4, Theme.PANEL, Theme.PANEL_BORDER);
        g.enableScissor(7, top - 1, 8 + listW + 1, bottom + 1);
        int y = top - listScroll;
        for (Macro macro : MacroStore.macros()) {
            boolean active = macro.id.equals(selectedId);
            boolean hover = mouseX >= 8 && mouseX < 8 + listW && mouseY >= y && mouseY < y + 18 && mouseY >= top && mouseY < bottom;
            if (active) Draw.roundRect(g, 8, y, listW, 18, 3, Theme.BUTTON_HOVER);
            else if (hover) Draw.roundRect(g, 8, y, listW, 18, 3, Theme.BUTTON);
            String keyText = macro.key != null ? macro.key.display() : "";
            int keyW = keyText.isEmpty() ? 0 : font.width(keyText) + 6;
            String name = (MacroRunner.running(macro) ? "▶ " : macro.quick ? "★ " : "") + macro.name;
            g.text(font, font.plainSubstrByWidth(name, listW - 10 - keyW), 12, y + 5,
                    MacroRunner.running(macro) ? Theme.ACCENT_BLUE : active ? Theme.TEXT : Theme.TEXT_DIM, false);
            if (keyW > 0) {
                Draw.roundRect(g, 8 + listW - keyW - 3, y + 3, keyW, 12, 2, 0xFF2A2E4A);
                g.text(font, keyText, 8 + listW - keyW, y + 5, Theme.TEXT_DIM, false);
            }
            if (y + 18 > top && y < bottom) {
                String id = macro.id;
                clickables.add(new Object[]{8, Math.max(y, top), listW, Math.min(y + 18, bottom) - Math.max(y, top), (Runnable) () -> {
                    selectedId = id;
                    capture = null;
                    stepScroll = 0;
                    rebuildWidgets();
                }});
            }
            y += 20;
        }
        g.disableScissor();
    }

    private void drawKeys(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int y = height - 44;
        keyRow(g, mouseX, mouseY, y, "Панель", "panel", MacroStore.panelKey());
        keyRow(g, mouseX, mouseY, y + 18, "Быстрое меню", "quick", MacroStore.quickKey());
    }

    private void keyRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int y, String label, String id, @Nullable HotKey key) {
        g.text(font, label, 8, y + 4, Theme.TEXT_DIM, false);
        String value = id.equals(capture) ? "нажмите…" : key == null ? "нет" : key.display();
        int w = Math.max(40, font.width(value) + 12);
        int x = 8 + listW - w;
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 16;
        Draw.panel(g, x, y, w, 16, 3, hover ? Theme.BUTTON_HOVER : Theme.BUTTON, id.equals(capture) ? Theme.ACCENT_BLUE : Theme.PANEL_BORDER);
        g.centeredText(font, value, x + w / 2, y + 4, id.equals(capture) ? Theme.ACCENT_BLUE : Theme.TEXT);
        clickables.add(new Object[]{x, y, w, 16, (Runnable) () -> {
            capture = id.equals(capture) ? null : id;
            rebuildWidgets();
        }});
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capture != null) {
            if (HotKey.isModifier(event)) return true;
            HotKey key = event.isEscape() ? null : HotKey.of(event);
            switch (capture) {
                case "panel" -> MacroStore.setPanelKey(key);
                case "quick" -> MacroStore.setQuickKey(key);
                default -> {
                    Macro macro = selected();
                    if (macro != null) {
                        macro.key = key;
                        changed();
                    }
                }
            }
            capture = null;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (AdminHeader.click(clickables, event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < editX - 4) {
            int max = Math.max(0, MacroStore.macros().size() * 20 - (height - 98));
            listScroll = (int) Math.max(0, Math.min(max, listScroll - scrollY * 20));
            return true;
        }
        Macro macro = selected();
        if (macro != null) {
            int visibleRows = Math.max(1, (stepsBottom - stepsTop) / ROW);
            stepScroll = (int) Math.max(0, Math.min(Math.max(0, macro.steps.size() - visibleRows), stepScroll - Math.signum(scrollY)));
            layoutSteps();
        }
        return true;
    }

    @Override
    public void removed() {
        if (dirty) MacroStore.save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
