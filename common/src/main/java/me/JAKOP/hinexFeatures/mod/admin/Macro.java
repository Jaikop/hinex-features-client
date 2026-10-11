package me.JAKOP.hinexFeatures.mod.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.util.Json;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Macro {

    public static final class Step {
        public String type;
        public String text;
        public double delay;

        public Step(String type, String text, double delay) {
            this.type = type;
            this.text = text;
            this.delay = delay;
        }
    }

    public String id;
    public String name;
    public @Nullable HotKey key;
    public boolean quick;
    public int repeat = 1;
    public final List<Step> steps = new ArrayList<>();

    public Macro(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public static Macro create(String name) {
        Macro macro = new Macro(UUID.randomUUID().toString().substring(0, 8), name);
        macro.steps.add(new Step("command", "time set day", 0.0));
        return macro;
    }

    static Macro fromJson(JsonObject json) {
        Macro macro = new Macro(Json.string(json, "id", UUID.randomUUID().toString().substring(0, 8)), Json.string(json, "name", "Макрос"));
        macro.key = HotKey.fromJson(Json.object(json, "key"));
        macro.quick = Json.bool(json, "quick", false);
        macro.repeat = json.has("repeat") ? Math.max(0, json.get("repeat").getAsInt()) : 1;
        for (JsonElement element : Json.array(json, "steps")) {
            JsonObject step = element.getAsJsonObject();
            macro.steps.add(new Step(Json.string(step, "type", "command"), Json.string(step, "text", ""),
                    step.has("delay") ? Math.max(0.0, step.get("delay").getAsDouble()) : 0.0));
        }
        return macro;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        if (key != null) json.add("key", key.toJson());
        json.addProperty("quick", quick);
        json.addProperty("repeat", repeat);
        JsonArray array = new JsonArray();
        for (Step step : steps) {
            JsonObject object = new JsonObject();
            object.addProperty("type", step.type);
            object.addProperty("text", step.text);
            object.addProperty("delay", step.delay);
            array.add(object);
        }
        json.add("steps", array);
        return json;
    }
}
