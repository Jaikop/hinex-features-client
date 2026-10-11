package me.JAKOP.hinexFeatures.mod.dialog.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.dialog.DialogSpec;
import me.JAKOP.hinexFeatures.mod.mixin.dialog.MultiLineEditBoxAccessor;
import me.JAKOP.hinexFeatures.mod.ui.HinexButton;
import me.JAKOP.hinexFeatures.mod.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class EditorForm {

    interface Host {
        void add(AbstractWidget widget);

        void changed(boolean text);

        void openPalette(ColorSwatch swatch, IntSupplier get, IntConsumer set, boolean allowNone);
    }

    private record Row(String label, int height, List<AbstractWidget> widgets, int[] xs, BooleanSupplier visible) {
    }

    private static final int LABEL = 50;
    private static final int GAP = 3;

    private final Host host;
    private final Font font;
    private final JsonObject draft;
    private final List<Row> rows = new ArrayList<>();
    private final Set<AbstractWidget> registered = Collections.newSetFromMap(new IdentityHashMap<>());
    private int x;
    private int y;
    private int width;
    private int height;
    private double scroll;
    private int content;
    private MultiLineEditBox textArea;

    EditorForm(Host host, Font font, JsonObject draft) {
        this.host = host;
        this.font = font;
        this.draft = draft;
    }

    private String str(String key, String fallback) {
        return draft.has(key) && draft.get(key).isJsonPrimitive() ? draft.get(key).getAsString() : fallback;
    }

    private double num(String key, double fallback) {
        try {
            return draft.has(key) ? draft.get(key).getAsDouble() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private int color(String key, int fallback) {
        return DialogSpec.parseColor(str(key, ""), fallback);
    }

    private void set(String key, String value) {
        draft.addProperty(key, value);
        host.changed(false);
    }

    private void set(String key, double value) {
        draft.addProperty(key, Math.round(value * 100.0) / 100.0);
        host.changed(false);
    }

    private JsonObject answers() {
        if (!draft.has("answers") || !draft.get("answers").isJsonObject()) draft.add("answers", new JsonObject());
        return draft.getAsJsonObject("answers");
    }

    private String answerString(String key) {
        JsonObject answers = answers();
        return answers.has(key) && answers.get(key).isJsonPrimitive() ? answers.get(key).getAsString() : "";
    }

    private void setAnswer(String key, String value) {
        answers().addProperty(key, value);
        host.changed(false);
    }

    private String answerType() {
        JsonObject answers = answers();
        return answers.has("type") ? answers.get("type").getAsString() : "none";
    }

    private String option(int index) {
        JsonObject answers = answers();
        if (!answers.has("options")) return "";
        JsonArray options = answers.getAsJsonArray("options");
        return index < options.size() ? options.get(index).getAsString() : "";
    }

    private void setOption(int index, String value) {
        JsonObject answers = answers();
        JsonArray old = answers.has("options") ? answers.getAsJsonArray("options") : new JsonArray();
        List<String> list = new ArrayList<>();
        for (JsonElement element : old) list.add(element.getAsString());
        while (list.size() <= index) list.add("");
        list.set(index, value);
        while (!list.isEmpty() && list.getLast().isEmpty()) list.removeLast();
        JsonArray array = new JsonArray();
        list.forEach(array::add);
        answers.add("options", array);
        host.changed(false);
    }

    void build(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rows.clear();
        int cw = width - LABEL - 8;
        int half = (cw - GAP) / 2;

        EditBox title = edit(cw - 20, 64, str("title", ""), value -> set("title", value));
        title.setHint(Component.literal("Имя говорящего").withColor(0x646C92));
        row("Имя", 20, always(), title, 0, swatch("Цвет имени", () -> color("titleColor", 0xFFD27A),
                rgb -> set("titleColor", DialogSpec.hex(rgb)), false), cw - 18);

        List<AbstractWidget> tools = new ArrayList<>();
        List<Integer> toolXs = new ArrayList<>();
        int tx = 0;
        String toolLabel = "Текст";
        String[][] toolbar = {
                {"B", "<b>", "</b>", "Жирный"}, {"I", "<i>", "</i>", "Курсив"}, {"U", "<u>", "</u>", "Подчёркнутый"},
                {"S", "<st>", "</st>", "Зачёркнутый"}, {"≈", "<shake>", "</shake>", "Тряска букв"},
                {"∿", "<wave>", "</wave>", "Волна"}, {"◐", "<rainbow>", "</rainbow>", "Радуга"},
                {"✦", "<pulse>", "</pulse>", "Пульсация яркости"}, {"▚", "<glitch>", "</glitch>", "Глитч"},
                {"☀", "<glow>", "</glow>", "Свечение"}, {"⏸", "<pause:500>", "", "Пауза в печати 0.5 с"},
                {"»", "<speed:0.4>", "</speed>", "Медленная печать этого куска"}};
        for (String[] tool : toolbar) {
            int w = 14;
            if (tx + w > cw) {
                rows.add(new Row(toolLabel, 17, List.copyOf(tools), toolXs.stream().mapToInt(Integer::intValue).toArray(), always()));
                tools.clear();
                toolXs.clear();
                toolLabel = "";
                tx = 0;
            }
            HinexButton button = new HinexButton(0, 0, w, 14, Component.literal(tool[0]), HinexButton.Kind.SECONDARY,
                    b -> wrap(tool[1], tool[2]));
            button.setTooltip(Tooltip.create(Component.literal(tool[3] + "\n" + tool[1] + "…" + tool[2]
                    + "\nВыделите текст и нажмите — или вставится на место курсора")));
            register(button);
            tools.add(button);
            toolXs.add(tx);
            tx += w + 2;
        }
        ColorSwatch colorTool = new ColorSwatch(0, 0, 14, "Цвет выделенного текста", () -> 0xFFAA00,
                swatch -> host.openPalette(swatch, () -> 0xFFAA00, rgb -> wrap("<" + DialogSpec.hex(rgb) + ">", "</" + DialogSpec.hex(rgb) + ">"), false));
        register(colorTool);
        if (tx + 14 > cw) {
            rows.add(new Row(toolLabel, 17, List.copyOf(tools), toolXs.stream().mapToInt(Integer::intValue).toArray(), always()));
            tools.clear();
            toolXs.clear();
            toolLabel = "";
            tx = 0;
        }
        tools.add(colorTool);
        toolXs.add(tx);
        rows.add(new Row(toolLabel, 17, List.copyOf(tools), toolXs.stream().mapToInt(Integer::intValue).toArray(), always()));

        textArea = MultiLineEditBox.builder().setShowDecorations(false).setPlaceholder(Component.literal("Текст реплики…"))
                .build(font, cw, 62, Component.literal("Текст"));
        textArea.setCharacterLimit(4000);
        textArea.setValue(str("text", ""));
        textArea.setValueListener(value -> {
            draft.addProperty("text", value);
            host.changed(true);
        });
        register(textArea);
        rows.add(new Row("", 66, List.of(textArea), new int[]{0}, always()));

        EditBox icon = edit(cw - 46, 64, str("icon", ""), value -> set("icon", value.strip()));
        icon.setHint(Component.literal("elder / head:Ник").withColor(0x646C92));
        icon.setTooltip(Tooltip.create(Component.literal("Иконка из ресурспака: assets/icons/<имя>.png (64–128 px)\n"
                + "head:Ник — лицо скина игрока\nпустое — без иконки")));
        CycleButton iconSize = new CycleButton(0, 0, 42, 16, List.of("24", "32", "36", "48", "64"),
                List.of("24px", "32px", "36px", "48px", "64px"), String.valueOf((int) num("iconSize", 36)),
                value -> set("iconSize", Double.parseDouble(value)));
        row("Иконка", 20, always(), icon, 0, iconSize, cw - 42);

        row("Место", 20, always(),
                cycle(half, "position", DialogSpec.POSITIONS, List.of("снизу", "по центру", "сверху")), 0,
                cycle(half, "window", DialogSpec.WINDOWS, List.of("выезд", "проявление", "всплытие", "тряска", "сразу")), half + GAP);
        row("Сдвиг", 20, always(), slider(cw, -80, 120, 2, num("offset", 0),
                v -> v == 0 ? "по высоте: как обычно" : v > 0 ? "выше на " + (int) v + " px" : "ниже на " + (int) -v + " px",
                v -> set("offset", v)), 0);
        row("Ширина", 20, always(), slider(cw, 120, 460, 10, num("width", 240), v -> (int) v + " px", v -> set("width", v)), 0);
        row("Размер", 20, always(), slider(cw, 0.6, 2.5, 0.05, num("scale", 1.0),
                v -> String.format(Locale.ROOT, "текст ×%.2f", v), v -> set("scale", v)), 0);
        row("Фон", 20, always(),
                swatch("Цвет фона", () -> color("background", 0x120D0A), rgb -> set("background", DialogSpec.hex(rgb)), false), 0,
                swatch("Второй цвет фона (градиент сверху вниз). Нет — сплошной", () -> str("background2", "").isBlank() ? -1
                        : color("background2", -1), rgb -> set("background2", rgb < 0 ? "" : DialogSpec.hex(rgb)), true), 20,
                slider(cw - 40, 0, 1, 0.02, num("opacity", 0.88), v -> "плотность " + Math.round(v * 100) + "%",
                        v -> set("opacity", v)), 40);
        row("Рамка", 20, always(),
                cycle(cw - 20, "borderStyle", DialogSpec.BORDERS,
                        List.of("скруглённая", "тонкая", "двойная", "толстая", "свечение", "узорная", "пиксельная", "без рамки")), 0,
                swatch("Цвет рамки", () -> color("border", 0xC9A063), rgb -> set("border", DialogSpec.hex(rgb)), false), cw - 18);
        row("Имя", 20, always(),
                cycle(cw - 20, "titleStyle", DialogSpec.TITLE_STYLES, List.of("имя на плашке", "имя в строку", "без имени")), 0,
                swatch("Цвет текста", () -> color("textColor", 0xF2EADA), rgb -> set("textColor", DialogSpec.hex(rgb)), false), cw - 18);
        row("Печать", 20, always(),
                cycle(half, "appear", DialogSpec.APPEARS, List.of("по буквам", "по словам", "проявление", "сразу")), 0,
                slider(half, 5, 150, 1, num("speed", 40), v -> (int) v + " букв/с", v -> set("speed", v)), half + GAP);
        row("Время", 20, always(), slider(cw, 0, 60, 0.5, num("duration", 0),
                v -> v <= 0 ? "по длине текста" : String.format(Locale.ROOT, "%.1f с после печати", v), v -> set("duration", v)), 0);
        EditBox sound = edit(half, 64, str("sound", ""), value -> set("sound", value.strip()));
        sound.setHint(Component.literal("звук при появлении").withColor(0x646C92));
        sound.setTooltip(Tooltip.create(Component.literal("Ванильный звук, например block.bell.use")));
        row("Голос", 20, always(),
                cycle(half, "voice", DialogSpec.VOICES, List.of("голос: нет", "голос: бип", "голос: тихий", "голос: низкий",
                        "голос: высокий", "голос: робот")), 0, sound, half + GAP);

        CycleButton answerType = new CycleButton(0, 0, half, 16, DialogSpec.ANSWERS, List.of("без ответа", "варианты 1–5", "свой ответ"),
                answerType(), value -> {
            answers().addProperty("type", value);
            host.changed(false);
            layout();
        });
        Slider timeout = slider(half, 0, 120, 1, answers().has("timeout") ? answers().get("timeout").getAsDouble() : 0,
                v -> v <= 0 ? "без лимита" : (int) v + " с на ответ", v -> {
                    answers().addProperty("timeout", (int) v);
                    host.changed(false);
                });
        row("Ответ", 20, always(), answerType, 0, timeout, half + GAP);

        BooleanSupplier answering = () -> !answerType().equals("none");
        CycleButton hint = new CycleButton(0, 0, 70, 16, List.of("true", "false"), List.of("показать", "скрыть"),
                String.valueOf(!answers().has("hint") || answers().get("hint").getAsBoolean()), value -> {
            answers().addProperty("hint", Boolean.parseBoolean(value));
            host.changed(false);
        });
        hint.setTooltip(Tooltip.create(Component.literal("Мелкая подсказка у окна ответа:\n«Enter — отправить · Esc — без ответа»")));
        EditBox hintText = edit(cw - 73, 80, answerString("hintText"), value -> setAnswer("hintText", value));
        hintText.setHint(Component.literal("свой текст (пусто — стандартный)").withColor(0x646C92));
        row("Подсказка", 20, answering, hint, 0, hintText, 73);

        for (int i = 0; i < 5; i++) {
            int index = i;
            EditBox option = edit(cw, 60, option(i), value -> setOption(index, value.strip()));
            option.setHint(Component.literal(i < 2 ? (i == 0 ? "Да, я помогу" : "Нет, мне некогда") : "(не обязательно)").withColor(0x646C92));
            row("Вариант " + (i + 1), 19, () -> answerType().equals("choice"), option, 0);
        }
        EditBox placeholder = edit(cw, 80, answers().has("placeholder") ? answers().get("placeholder").getAsString() : "",
                value -> {
                    answers().addProperty("placeholder", value);
                    host.changed(false);
                });
        placeholder.setHint(Component.literal("серый текст в пустом поле").withColor(0x646C92));
        BooleanSupplier textAnswer = () -> answerType().equals("text");
        row("В поле", 19, textAnswer, placeholder, 0);

        CycleButton inputStyle = new CycleButton(0, 0, cw - 60, 16, DialogSpec.INPUT_STYLES,
                List.of("поле: рамка", "поле: черта снизу", "поле: без рамки"), answerString("inputStyle").isEmpty() ? "box"
                : answerString("inputStyle"), value -> setAnswer("inputStyle", value));
        row("Поле", 20, textAnswer, inputStyle, 0,
                swatch("Фон поля", () -> DialogSpec.parseColor(answerString("inputBackground"), 0x000000),
                        rgb -> setAnswer("inputBackground", DialogSpec.hex(rgb)), false), cw - 58,
                swatch("Рамка поля", () -> DialogSpec.parseColor(answerString("inputBorder"), color("border", 0xC9A063)),
                        rgb -> setAnswer("inputBorder", DialogSpec.hex(rgb)), false), cw - 38,
                swatch("Цвет текста ответа", () -> DialogSpec.parseColor(answerString("inputColor"), color("textColor", 0xF2EADA)),
                        rgb -> setAnswer("inputColor", DialogSpec.hex(rgb)), false), cw - 18);
        Slider inputOpacity = slider(half, 0, 1, 0.05, answers().has("inputOpacity") ? answers().get("inputOpacity").getAsDouble() : 0.4,
                v -> "фон " + Math.round(v * 100) + "%", v -> {
                    answers().addProperty("inputOpacity", Math.round(v * 100) / 100.0);
                    host.changed(false);
                });
        Slider maxLength = slider(half, 10, 300, 10, answers().has("maxLength") ? answers().get("maxLength").getAsDouble() : 300,
                v -> "до " + (int) v + " букв", v -> {
                    answers().addProperty("maxLength", (int) v);
                    host.changed(false);
                });
        row("", 20, textAnswer, inputOpacity, 0, maxLength, half + GAP);
        Slider inputWidth = slider(half, 30, 100, 5, answers().has("inputWidth") ? answers().get("inputWidth").getAsDouble() : 100,
                v -> "ширина " + (int) v + "%", v -> {
                    answers().addProperty("inputWidth", (int) v);
                    host.changed(false);
                });
        Slider inputHeight = slider(half, 12, 28, 1, answers().has("inputHeight") ? answers().get("inputHeight").getAsDouble() : 14,
                v -> "высота " + (int) v + " px", v -> {
                    answers().addProperty("inputHeight", (int) v);
                    host.changed(false);
                });
        row("", 20, textAnswer, inputWidth, 0, inputHeight, half + GAP);
        row("Очередь", 20, always(), new CycleButton(0, 0, cw, 16, List.of("false", "true"),
                List.of("встать в очередь за текущим", "заменить текущее окно"), String.valueOf(draft.has("replace") && draft.get("replace").getAsBoolean()),
                value -> {
                    draft.addProperty("replace", Boolean.parseBoolean(value));
                    host.changed(false);
                }), 0);
        layout();
    }

    private static BooleanSupplier always() {
        return () -> true;
    }

    private void row(String label, int height, BooleanSupplier visible, Object... widgetsAndXs) {
        List<AbstractWidget> widgets = new ArrayList<>();
        int[] xs = new int[widgetsAndXs.length / 2];
        for (int i = 0; i < widgetsAndXs.length; i += 2) {
            AbstractWidget widget = (AbstractWidget) widgetsAndXs[i];
            register(widget);
            widgets.add(widget);
            xs[i / 2] = (int) widgetsAndXs[i + 1];
        }
        rows.add(new Row(label, height, widgets, xs, visible));
    }

    private void register(AbstractWidget widget) {
        if (registered.add(widget)) host.add(widget);
    }

    private EditBox edit(int width, int max, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(font, 0, 0, width, 16, Component.empty());
        box.setMaxLength(max);
        box.setValue(value);
        box.setResponder(onChange);
        register(box);
        return box;
    }

    private CycleButton cycle(int width, String key, List<String> values, List<String> labels) {
        CycleButton button = new CycleButton(0, 0, width, 16, values, labels, str(key, values.getFirst()), value -> set(key, value));
        register(button);
        return button;
    }

    private Slider slider(int width, double min, double max, double step, double value,
                          java.util.function.DoubleFunction<String> label, java.util.function.DoubleConsumer onChange) {
        Slider slider = new Slider(0, 0, width, 16, min, max, step, value, label, onChange);
        register(slider);
        return slider;
    }

    private ColorSwatch swatch(String tooltip, IntSupplier get, IntConsumer set, boolean allowNone) {
        ColorSwatch[] holder = new ColorSwatch[1];
        holder[0] = new ColorSwatch(0, 0, 16, tooltip, get, swatch -> host.openPalette(swatch, get, set, allowNone));
        register(holder[0]);
        return holder[0];
    }

    private void wrap(String open, String close) {
        MultilineTextField field = ((MultiLineEditBoxAccessor) textArea).hinex$textField();
        String selected = field.getSelectedText();
        field.insertText(open + selected + close);
        if (selected.isEmpty() && !close.isEmpty()) field.seekCursor(Whence.RELATIVE, -close.length());
        textArea.setFocused(true);
    }

    void layout() {
        int cy = y - (int) scroll;
        int total = 0;
        for (Row row : rows) {
            boolean visible = row.visible().getAsBoolean();
            for (int i = 0; i < row.widgets().size(); i++) {
                AbstractWidget widget = row.widgets().get(i);
                widget.setX(x + LABEL + row.xs()[i]);
                widget.setY(cy + (row.height() - widget.getHeight()) / 2);
                boolean inside = cy + row.height() > y && cy < y + height;
                widget.visible = visible && inside;
                widget.active = visible && inside;
            }
            if (visible) {
                cy += row.height();
                total += row.height();
            }
        }
        content = total;
        double max = Math.max(0, content - height);
        if (scroll > max) {
            scroll = max;
            layout();
        }
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.enableScissor(x, y, x + width, y + height);
        int cy = y - (int) scroll;
        for (Row row : rows) {
            if (!row.visible().getAsBoolean()) continue;
            if (cy + row.height() > y && cy < y + height && !row.label().isEmpty()) {
                g.text(font, row.label(), x, cy + (row.height() - 8) / 2, Theme.TEXT_DIM, false);
            }
            for (AbstractWidget widget : row.widgets()) {
                if (widget.visible) widget.extractRenderState(g, mouseX, mouseY, a);
            }
            cy += row.height();
        }
        g.disableScissor();
        if (content > height) {
            int barX = x + width - 3;
            g.fill(barX, y, barX + 2, y + height, 0x40FFFFFF);
            int knob = Math.max(12, height * height / content);
            int knobY = y + (int) ((height - knob) * (scroll / (content - height)));
            g.fill(barX, knobY, barX + 2, knobY + knob, Theme.ACCENT_MID);
        }
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    boolean overTextArea(double mouseX, double mouseY) {
        return textArea != null && textArea.visible && textArea.isMouseOver(mouseX, mouseY) && textArea.maxScrollAmount() > 0;
    }

    void scroll(double amount) {
        scroll = Math.max(0, Math.min(Math.max(0, content - height), scroll - amount * 22));
        layout();
    }

    List<AbstractWidget> widgets() {
        List<AbstractWidget> all = new ArrayList<>();
        for (Row row : rows) all.addAll(row.widgets());
        return all;
    }
}
