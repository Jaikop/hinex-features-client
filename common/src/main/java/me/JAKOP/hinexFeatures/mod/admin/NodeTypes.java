package me.JAKOP.hinexFeatures.mod.admin;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

final class NodeTypes {

    record Field(String key, String label, String kind, List<String> values, List<String> labels, String fallback) {
        static Field text(String key, String label, String fallback) {
            return new Field(key, label, "text", List.of(), List.of(), fallback);
        }

        static Field number(String key, String label, String fallback) {
            return new Field(key, label, "int", List.of(), List.of(), fallback);
        }

        static Field cycle(String key, String label, List<String> values, List<String> labels) {
            return new Field(key, label, "cycle", values, labels, values.getFirst());
        }
    }

    record Type(String id, String title, int color, int inputs, int outputs, List<String> inputLabels, List<String> outputLabels,
                List<Field> fields, String hint) {
    }

    static final List<String> MODES = List.of("impulse", "repeat", "chain");
    static final List<String> EVENTS = List.of("join", "quit", "death", "respawn", "chat", "trigger");

    static final Map<String, Type> TYPES = Map.ofEntries(
            entry(new Type("button", "Кнопка", 0xFF8C8C96, 0, 1, List.of(), List.of(""),
                    List.of(Field.number("length", "Сигнал, тиков", "10")), "Нажмите ▶ на блоке — короткий сигнал")),
            entry(new Type("lever", "Рычаг", 0xFF9A7442, 0, 1, List.of(), List.of(""), List.of(), "Щёлкните переключатель на блоке")),
            entry(new Type("redstone", "Блок редстоуна", 0xFFB01818, 0, 1, List.of(), List.of(""), List.of(), "Сигнал всегда включён")),
            entry(new Type("clock", "Таймер", 0xFFD9A23C, 1, 1, List.of("вкл"), List.of(""),
                    List.of(Field.number("period", "Период, тиков", "20")), "Пульс каждые N тиков (20 тиков = 1 с). Вход «вкл» — если подключён")),
            entry(new Type("delay", "Задержка", 0xFF8E8E98, 1, 1, List.of(""), List.of(""),
                    List.of(Field.number("ticks", "Задержка, тиков", "10")), "Повторяет сигнал позже")),
            entry(new Type("and", "И", 0xFF2F8F8A, 2, 1, List.of("A", "B"), List.of(""), List.of(), "Сигнал, если есть на обоих входах")),
            entry(new Type("or", "ИЛИ", 0xFF2F8F8A, 2, 1, List.of("A", "B"), List.of(""), List.of(), "Сигнал, если есть хоть на одном")),
            entry(new Type("not", "НЕ", 0xFF2F8F8A, 1, 1, List.of(""), List.of(""), List.of(), "Инвертирует: без входа — всегда включён")),
            entry(new Type("counter", "Счётчик", 0xFF3C6FD9, 2, 1, List.of("+1", "0"), List.of(""),
                    List.of(Field.number("count", "Каждые N сигналов", "3")), "Пульс на каждый N-й сигнал; вход «0» — сброс")),
            entry(new Type("random", "Случайно", 0xFF8A4FD9, 1, 1, List.of(""), List.of(""),
                    List.of(Field.number("chance", "Шанс, %", "50")), "Пропускает сигнал с шансом")),
            entry(new Type("command", "Командный блок", 0xFFD0782C, 1, 2, List.of(""), List.of("→", "✓"),
                    List.of(Field.cycle("mode", "Режим", MODES, List.of("импульс", "повтор", "цепной")),
                            Field.text("command", "Команда", "say Привет, {player}!"),
                            Field.number("interval", "Повтор: каждые N тиков", "20"),
                            Field.cycle("always", "Повтор без входа", List.of("false", "true"), List.of("нужен сигнал", "всегда"))),
                    "От консоли. {player} {x} {y} {z} {message} — от события. Выходы: → выполнено, ✓ успешно")),
            entry(new Type("event", "Событие", 0xFFD4A25A, 0, 1, List.of(), List.of(""),
                    List.of(Field.cycle("event", "Когда", EVENTS, List.of("игрок зашёл", "игрок вышел", "игрок умер", "возродился",
                                    "сообщение в чат", "/hevent имя")),
                            Field.text("filter", "Текст чата / имя", "")),
                    "Сигнал с игроком: команды дальше видят {player}")),
            entry(new Type("world_in", "Датчик в мире", 0xFF7A2424, 0, 1, List.of(), List.of(""),
                    List.of(Field.text("world", "Мир (пусто — основной)", ""), Field.number("bx", "X", "0"), Field.number("by", "Y", "64"),
                            Field.number("bz", "Z", "0")),
                    "Сигнал, пока блок в мире запитан редстоуном")),
            entry(new Type("world_out", "Сигнал в мир", 0xFF7A2424, 1, 0, List.of(""), List.of(),
                    List.of(Field.text("world", "Мир (пусто — основной)", ""), Field.number("bx", "X", "0"), Field.number("by", "Y", "64"),
                            Field.number("bz", "Z", "0"), Field.text("block", "Блок", "minecraft:redstone_block")),
                    "Пока есть сигнал — ставит блок (редстоун) в мире, потом убирает")),
            entry(new Type("note", "Заметка", 0xFFE8D98A, 0, 0, List.of(), List.of(), List.of(Field.text("text", "Текст", "")),
                    "Подпись на схеме")));

    static final List<String[]> PALETTE = List.of(
            new String[]{"Сигналы", null, null},
            new String[]{"Кнопка", "button", null}, new String[]{"Рычаг", "lever", null},
            new String[]{"Блок редстоуна", "redstone", null}, new String[]{"Таймер", "clock", null},
            new String[]{"Логика", null, null},
            new String[]{"И", "and", null}, new String[]{"ИЛИ", "or", null}, new String[]{"НЕ", "not", null},
            new String[]{"Задержка", "delay", null}, new String[]{"Счётчик", "counter", null}, new String[]{"Случайно", "random", null},
            new String[]{"Команды", null, null},
            new String[]{"Импульсный", "command", "impulse"}, new String[]{"Повторяющийся", "command", "repeat"},
            new String[]{"Цепной", "command", "chain"},
            new String[]{"События", null, null},
            new String[]{"Событие", "event", null},
            new String[]{"Мир", null, null},
            new String[]{"Датчик в мире", "world_in", null}, new String[]{"Сигнал в мир", "world_out", null},
            new String[]{"Прочее", null, null},
            new String[]{"Заметка", "note", null});

    private NodeTypes() {
    }

    private static Map.Entry<String, Type> entry(Type type) {
        return Map.entry(type.id(), type);
    }

    static Type of(String id) {
        return TYPES.getOrDefault(id, TYPES.get("note"));
    }

    static int color(WorkspaceClient.Node node) {
        if (node.type.equals("command")) {
            return switch (node.str("mode", "impulse")) {
                case "repeat" -> 0xFF7A52B0;
                case "chain" -> 0xFF4F9A5A;
                default -> 0xFFD0782C;
            };
        }
        return of(node.type).color();
    }

    static String title(WorkspaceClient.Node node) {
        if (node.type.equals("command")) {
            return switch (node.str("mode", "impulse")) {
                case "repeat" -> "Повторяющийся";
                case "chain" -> "Цепной";
                default -> "Командный блок";
            };
        }
        return of(node.type).title();
    }

    static int width(String type) {
        return switch (type) {
            case "command", "note" -> 130;
            case "and", "or", "not" -> 70;
            default -> 100;
        };
    }

    static int height(String type) {
        return type.equals("command") ? 50 : type.equals("note") ? 40 : 36;
    }

    static JsonObject defaults(String type, String mode) {
        JsonObject props = new JsonObject();
        for (Field field : of(type).fields()) {
            if (!field.fallback().isEmpty()) props.addProperty(field.key(), field.fallback());
        }
        if (type.equals("command") && mode != null) props.addProperty("mode", mode);
        if (type.equals("command") && "repeat".equals(mode)) props.addProperty("command", "effect give @a[tag=сюжет] glowing 2 0 true");
        return props;
    }
}
