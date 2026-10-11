package me.JAKOP.hinexFeatures.mod.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogSpec {

    public static final List<String> POSITIONS = List.of("bottom", "center", "top");
    public static final List<String> BORDERS = List.of("rounded", "line", "double", "thick", "glow", "ornate", "pixel", "none");
    public static final List<String> TITLE_STYLES = List.of("tab", "inline", "none");
    public static final List<String> APPEARS = List.of("typewriter", "words", "fade", "instant");
    public static final List<String> WINDOWS = List.of("slide", "fade", "pop", "shake", "none");
    public static final List<String> VOICES = List.of("none", "blip", "soft", "low", "high", "robot");
    public static final List<String> ANSWERS = List.of("none", "choice", "text");
    public static final List<String> INPUT_STYLES = List.of("box", "line", "none");

    public final JsonObject json;
    public final String id;
    public final String title;
    public final String text;
    public final String icon;
    public final String sender;
    public final long time;
    public final int iconSize;
    public final int width;
    public final String position;
    public final int offset;
    public final float scale;
    public final int background;
    public final int background2;
    public final float opacity;
    public final int border;
    public final String borderStyle;
    public final int titleColor;
    public final int textColor;
    public final String titleStyle;
    public final String appear;
    public final float speed;
    public final String window;
    public final float duration;
    public final String voice;
    public final String sound;
    public final String answerType;
    public final List<String> options;
    public final String placeholder;
    public final float timeout;
    public final boolean hint;
    public final String hintText;
    public final String inputStyle;
    public final int inputBackground;
    public final float inputOpacity;
    public final int inputBorder;
    public final int inputColor;
    public final int maxLength;
    public final int inputWidth;
    public final int inputHeight;
    public final boolean replace;

    private DialogSpec(JsonObject json) {
        this.json = json;
        id = string(json, "id", "");
        title = string(json, "title", "");
        text = string(json, "text", "");
        icon = string(json, "icon", "").strip();
        sender = string(json, "sender", "");
        time = json.has("time") ? json.get("time").getAsLong() : System.currentTimeMillis();
        iconSize = clamp(integer(json, "iconSize", 36), 16, 72);
        width = clamp(integer(json, "width", 240), 120, 460);
        position = choice(json, "position", POSITIONS);
        offset = clamp(integer(json, "offset", 0), -200, 300);
        scale = clamp(number(json, "scale", 1.0F), 0.6F, 2.5F);
        background = color(json, "background", 0x120D0A);
        background2 = json.has("background2") && !string(json, "background2", "").isBlank() ? color(json, "background2", -1) : -1;
        opacity = clamp(number(json, "opacity", 0.88F), 0.0F, 1.0F);
        border = color(json, "border", 0xC9A063);
        borderStyle = choice(json, "borderStyle", BORDERS);
        titleColor = color(json, "titleColor", 0xFFD27A);
        textColor = color(json, "textColor", 0xF2EADA);
        titleStyle = choice(json, "titleStyle", TITLE_STYLES);
        appear = choice(json, "appear", APPEARS);
        speed = clamp(number(json, "speed", 40.0F), 4.0F, 400.0F);
        window = choice(json, "window", WINDOWS);
        duration = Math.max(0.0F, number(json, "duration", 0.0F));
        voice = choice(json, "voice", VOICES);
        sound = string(json, "sound", "").strip();
        JsonObject answers = json.has("answers") && json.get("answers").isJsonObject() ? json.getAsJsonObject("answers") : new JsonObject();
        List<String> list = new ArrayList<>();
        if (answers.has("options") && answers.get("options").isJsonArray()) {
            for (JsonElement element : answers.getAsJsonArray("options")) {
                String option = element.getAsString().strip();
                if (!option.isEmpty() && list.size() < 5) list.add(option);
            }
        }
        options = List.copyOf(list);
        String type = string(answers, "type", "none").toLowerCase(Locale.ROOT);
        answerType = type.equals("choice") && options.isEmpty() ? "none" : ANSWERS.contains(type) ? type : "none";
        placeholder = string(answers, "placeholder", "");
        timeout = Math.max(0.0F, number(answers, "timeout", 0.0F));
        hint = !answers.has("hint") || !answers.get("hint").isJsonPrimitive() || answers.get("hint").getAsBoolean();
        hintText = string(answers, "hintText", "").strip();
        inputStyle = choice(answers, "inputStyle", INPUT_STYLES);
        inputBackground = color(answers, "inputBackground", 0x000000);
        inputOpacity = clamp(number(answers, "inputOpacity", 0.4F), 0.0F, 1.0F);
        inputBorder = color(answers, "inputBorder", border);
        inputColor = color(answers, "inputColor", textColor);
        maxLength = clamp(integer(answers, "maxLength", 300), 10, 300);
        inputWidth = clamp(integer(answers, "inputWidth", 100), 30, 100);
        inputHeight = clamp(integer(answers, "inputHeight", 14), 12, 28);
        replace = json.has("replace") && json.get("replace").getAsBoolean();
    }

    public static DialogSpec parse(JsonObject json) {
        return new DialogSpec(json);
    }

    public static JsonObject example() {
        JsonObject json = new JsonObject();
        json.addProperty("title", "Старейшина");
        json.addProperty("text", "Путник, ты пришёл вовремя. <shake>Тьма</shake> уже у ворот — "
                + "<pause:400>и только <#ffd27a><wave>древний свет</wave></#ffd27a> может нас спасти.");
        json.addProperty("icon", "");
        return json;
    }

    public boolean hasAnswers() {
        return !answerType.equals("none");
    }

    public float readTime(int visibleChars) {
        if (duration > 0.0F) return duration;
        return Math.max(3.0F, Math.min(30.0F, 2.5F + visibleChars * 0.06F));
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) ? Math.round(json.get(key).getAsFloat()) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static float number(JsonObject json, String key, float fallback) {
        try {
            return json.has(key) ? json.get(key).getAsFloat() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String choice(JsonObject json, String key, List<String> allowed) {
        String value = string(json, key, allowed.getFirst()).toLowerCase(Locale.ROOT);
        return allowed.contains(value) ? value : allowed.getFirst();
    }

    public static int color(JsonObject json, String key, int fallback) {
        return parseColor(string(json, key, ""), fallback);
    }

    public static int parseColor(String raw, int fallback) {
        String value = raw.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return fallback;
        Integer named = RichText.NAMED.get(value);
        if (named != null) return named;
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() == 3) {
            value = "" + value.charAt(0) + value.charAt(0) + value.charAt(1) + value.charAt(1) + value.charAt(2) + value.charAt(2);
        }
        if (value.length() != 6) return fallback;
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static JsonObject stripMeta(JsonObject json) {
        JsonObject copy = json.deepCopy();
        copy.remove("id");
        copy.remove("sender");
        copy.remove("time");
        return copy;
    }

    static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
