package me.JAKOP.hinexFeatures.mod.essence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class KnowledgeData {

    public record Recipe(String key, boolean shaped, int width, int height, List<List<ItemStack>> slots, ItemStack result) {
    }

    public static final class Node {
        public final String id;
        public final Component name;
        public final List<Component> description;
        public final ItemStack icon;
        public final int x;
        public final int y;
        public final int required;
        public final List<String> requires;
        public final List<Recipe> recipes;
        public boolean unlocked;

        Node(JsonObject json) {
            id = Json.string(json, "id", "?");
            name = ItemViews.text(Json.string(json, "name", id));
            description = ItemViews.lines(Json.array(json, "description"));
            icon = ItemViews.stack(json.has("icon") ? json.getAsJsonObject("icon") : null);
            x = json.has("x") ? json.get("x").getAsInt() : 0;
            y = json.has("y") ? json.get("y").getAsInt() : 0;
            required = json.has("required") ? json.get("required").getAsInt() : 0;
            List<String> list = new ArrayList<>();
            for (JsonElement element : Json.array(json, "requires")) list.add(element.getAsString());
            requires = List.copyOf(list);
            unlocked = Json.bool(json, "unlocked", false);
            List<Recipe> parsed = new ArrayList<>();
            for (JsonElement element : Json.array(json, "recipes")) {
                JsonObject recipe = element.getAsJsonObject();
                List<List<ItemStack>> slots = new ArrayList<>();
                for (JsonElement slot : Json.array(recipe, "slots")) slots.add(ItemViews.options(slot.getAsJsonArray()));
                parsed.add(new Recipe(Json.string(recipe, "key", ""), Json.bool(recipe, "shaped", true),
                        Math.max(1, recipe.get("width").getAsInt()), Math.max(1, recipe.get("height").getAsInt()),
                        slots, ItemViews.stack(recipe.getAsJsonObject("result"))));
            }
            recipes = List.copyOf(parsed);
        }
    }

    public record GlobalLevel(String id, Component name, List<Component> description, ItemStack icon, int required) {
    }

    public final Component title;
    public int essence;
    public int global;
    public final List<Node> nodes;
    public final List<GlobalLevel> globals;

    private KnowledgeData(JsonObject json) {
        title = Component.literal(Json.string(json, "title", "Древо знаний"));
        essence = json.has("essence") ? json.get("essence").getAsInt() : 0;
        global = json.has("global") ? json.get("global").getAsInt() : 0;
        List<Node> list = new ArrayList<>();
        for (JsonElement element : Json.array(json, "nodes")) list.add(new Node(element.getAsJsonObject()));
        nodes = List.copyOf(list);
        List<GlobalLevel> levels = new ArrayList<>();
        for (JsonElement element : Json.array(json, "globals")) {
            JsonObject level = element.getAsJsonObject();
            levels.add(new GlobalLevel(Json.string(level, "id", "?"), ItemViews.text(Json.string(level, "name", "")),
                    ItemViews.lines(Json.array(level, "description")),
                    ItemViews.stack(level.has("icon") ? level.getAsJsonObject("icon") : null),
                    level.has("required") ? level.get("required").getAsInt() : 0));
        }
        levels.sort((a, b) -> Integer.compare(a.required(), b.required()));
        globals = List.copyOf(levels);
    }

    static KnowledgeData parse(JsonObject json) {
        return new KnowledgeData(json);
    }

    public @Nullable Node node(String id) {
        for (Node node : nodes) {
            if (node.id.equals(id)) return node;
        }
        return null;
    }

    public boolean reachable(Node node) {
        if (node.requires.isEmpty()) return true;
        for (String id : node.requires) {
            Node required = node(id);
            if (required != null && required.unlocked) return true;
        }
        return false;
    }
}
