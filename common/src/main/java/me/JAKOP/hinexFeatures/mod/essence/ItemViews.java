package me.JAKOP.hinexFeatures.mod.essence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ItemViews {

    private ItemViews() {
    }

    public static ItemStack stack(@Nullable JsonObject json) {
        if (json == null) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(Json.string(json, "id", "minecraft:air"));
        Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(id);
        if (item == Items.AIR) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, json.has("count") ? json.get("count").getAsInt() : 1));
        if (json.has("model")) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(json.get("model").getAsFloat()), List.of(), List.of(), List.of()));
        }
        String itemModel = Json.string(json, "itemModel", null);
        Identifier model = itemModel == null ? null : Identifier.tryParse(itemModel);
        if (model != null) stack.set(DataComponents.ITEM_MODEL, model);
        String name = Json.string(json, "name", null);
        if (name != null) stack.set(DataComponents.ITEM_NAME, text(name));
        if (json.has("color")) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(json.get("color").getAsInt()));
        if (json.has("glint") && json.get("glint").getAsBoolean()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    public static List<ItemStack> options(@Nullable JsonArray array) {
        List<ItemStack> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement element : array) {
            ItemStack stack = element.isJsonObject() ? stack(element.getAsJsonObject()) : ItemStack.EMPTY;
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    public static Component text(@Nullable String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElse(Component.literal(json));
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }

    public static List<Component> lines(@Nullable JsonArray array) {
        List<Component> result = new ArrayList<>();
        if (array == null) return result;
        for (JsonElement element : array) result.add(text(element.getAsString()));
        return result;
    }
}
