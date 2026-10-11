package me.JAKOP.hinexFeatures.mod.admin;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public record HotKey(int key, int keycode, int modifiers) {

    private static final int SHIFT = 1;
    private static final int CTRL = 2;
    private static final int ALT = 4;

    static int normalize(int sdl) {
        int result = 0;
        if ((sdl & InputConstants.MOD_SHIFT) != 0) result |= SHIFT;
        if ((sdl & InputConstants.MOD_CONTROL) != 0) result |= CTRL;
        if ((sdl & InputConstants.MOD_ALT) != 0) result |= ALT;
        return result;
    }

    public static HotKey of(KeyEvent event) {
        return new HotKey(event.key(), event.keycode(), normalize(event.modifiers()));
    }

    public boolean matches(KeyEvent event) {
        return event.key() == key && normalize(event.modifiers()) == modifiers;
    }

    public static boolean isModifier(KeyEvent event) {
        int key = event.key();
        return key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL || key == InputConstants.KEY_LSHIFT
                || key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT;
    }

    public String display() {
        StringBuilder text = new StringBuilder();
        if ((modifiers & CTRL) != 0) text.append("Ctrl+");
        if ((modifiers & SHIFT) != 0) text.append("Shift+");
        if ((modifiers & ALT) != 0) text.append("Alt+");
        text.append(keyName());
        return text.toString();
    }

    private String keyName() {
        InputConstants.Key info = InputConstants.getKey(new KeyEvent(key, keycode, 0));
        String name = info.getName();
        if (name.startsWith("key.keyboard.")) {
            String rest = name.substring("key.keyboard.".length());
            if (rest.length() == 1) return rest.toUpperCase(Locale.ROOT);
        }
        return info.getDisplayName().getString();
    }

    static @Nullable HotKey fromJson(@Nullable JsonObject json) {
        if (json == null || !json.has("key")) return null;
        return new HotKey(json.get("key").getAsInt(), json.has("keycode") ? json.get("keycode").getAsInt() : 0,
                json.has("mods") ? json.get("mods").getAsInt() : 0);
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("keycode", keycode);
        json.addProperty("mods", modifiers);
        return json;
    }
}
