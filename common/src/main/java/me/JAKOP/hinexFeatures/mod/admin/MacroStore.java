package me.JAKOP.hinexFeatures.mod.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.util.Json;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MacroStore {

    private static final HotKey DEFAULT_PANEL = new HotKey(InputConstants.KEY_F7, 0x40000040, 0);
    private static final HotKey DEFAULT_QUICK = new HotKey(InputConstants.KEY_G, 'g', 0);

    private static final List<Macro> MACROS = new ArrayList<>();
    private static @Nullable HotKey panelKey = DEFAULT_PANEL;
    private static @Nullable HotKey quickKey = DEFAULT_QUICK;
    private static boolean loaded;

    private MacroStore() {
    }

    private static Path file() {
        return HinexFeaturesClient.platform().gameDir().resolve("hinex").resolve("admin-macros.json");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        JsonObject json = Json.read(file());
        if (json == null) {
            Macro example = new Macro("example", "Пример: утро и объявление");
            example.steps.add(new Macro.Step("command", "time set day", 0.5));
            example.steps.add(new Macro.Step("command", "weather clear", 0.5));
            example.steps.add(new Macro.Step("chat", "Доброе утро, {me} на связи!", 0.0));
            example.quick = true;
            MACROS.add(example);
            return;
        }
        JsonObject keys = Json.object(json, "keys");
        if (keys != null) {
            panelKey = keys.has("panel") && keys.get("panel").isJsonObject() ? HotKey.fromJson(keys.getAsJsonObject("panel")) : null;
            quickKey = keys.has("quick") && keys.get("quick").isJsonObject() ? HotKey.fromJson(keys.getAsJsonObject("quick")) : null;
        }
        for (JsonElement element : Json.array(json, "macros")) {
            try {
                MACROS.add(Macro.fromJson(element.getAsJsonObject()));
            } catch (RuntimeException e) {
                HinexFeaturesClient.LOGGER.warn("Битый макрос пропущен: {}", e.toString());
            }
        }
    }

    public static void save() {
        load();
        JsonObject json = new JsonObject();
        JsonObject keys = new JsonObject();
        if (panelKey != null) keys.add("panel", panelKey.toJson());
        if (quickKey != null) keys.add("quick", quickKey.toJson());
        json.add("keys", keys);
        JsonArray array = new JsonArray();
        for (Macro macro : MACROS) array.add(macro.toJson());
        json.add("macros", array);
        Json.write(file(), json);
    }

    public static List<Macro> macros() {
        load();
        return MACROS;
    }

    public static List<Macro> quickMacros() {
        return macros().stream().filter(macro -> macro.quick).limit(10).toList();
    }

    public static @Nullable HotKey panelKey() {
        load();
        return panelKey;
    }

    public static @Nullable HotKey quickKey() {
        load();
        return quickKey;
    }

    public static void setPanelKey(@Nullable HotKey key) {
        load();
        panelKey = key;
        save();
    }

    public static void setQuickKey(@Nullable HotKey key) {
        load();
        quickKey = key;
        save();
    }
}
