package me.JAKOP.hinexFeatures.mod.vfx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

public final class VfxParams {

    public static final VfxParams EMPTY = new VfxParams(new JsonObject());

    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("white", 0xFFFFFF), Map.entry("black", 0x000000), Map.entry("red", 0xFF4A4A),
            Map.entry("orange", 0xFF9A3C), Map.entry("gold", 0xFFD24A), Map.entry("yellow", 0xFFF06A),
            Map.entry("lime", 0xA6FF4D), Map.entry("green", 0x4DFF88), Map.entry("aqua", 0x4FFFE8),
            Map.entry("cyan", 0x4FD8FF), Map.entry("blue", 0x4F8BFF), Map.entry("purple", 0xB46CFF),
            Map.entry("violet", 0x8A5CFF), Map.entry("pink", 0xFF6CC4), Map.entry("magenta", 0xFF4FE0),
            Map.entry("hinex", 0x6C8BFF));

    private final JsonObject json;

    public VfxParams(JsonObject json) {
        this.json = json;
    }

    public boolean has(String key) {
        return json.has(key);
    }

    public float number(String key, float fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        try {
            return element.getAsFloat();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int integer(String key, int fallback) {
        return Math.round(number(key, fallback));
    }

    public boolean bool(String key, boolean fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        String value = element.getAsString().toLowerCase(Locale.ROOT);
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on") || value.equals("да");
    }

    public String string(String key, String fallback) {
        JsonElement element = json.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsString();
    }

    public int color(String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) return fallback;
        return parseColor(element.getAsString(), fallback);
    }

    public static int parseColor(String value, int fallback) {
        String text = value.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(text);
        if (named != null) return named;
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() == 3) {
            text = "" + text.charAt(0) + text.charAt(0) + text.charAt(1) + text.charAt(1) + text.charAt(2) + text.charAt(2);
        }
        if (text.length() != 6) return fallback;
        try {
            return Integer.parseInt(text, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
