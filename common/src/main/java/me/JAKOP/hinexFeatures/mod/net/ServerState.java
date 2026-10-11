package me.JAKOP.hinexFeatures.mod.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ServerState(boolean plus, Component lockedHint, List<Emoji> emojis, Set<String> formatTags, boolean typingIndicator,
                          boolean admin) {

    public record Emoji(String name, String symbol) {
    }

    public boolean allows(String tag) {
        return formatTags.contains(tag);
    }

    public static ServerState parse(JsonObject json) {
        List<Emoji> emojis = new ArrayList<>();
        for (JsonElement element : Json.array(json, "emojis")) {
            JsonObject emoji = element.getAsJsonObject();
            String symbol = Json.string(emoji, "symbol", null);
            if (symbol != null && !symbol.isEmpty()) emojis.add(new Emoji(Json.string(emoji, "name", symbol), symbol));
        }

        Set<String> tags = new LinkedHashSet<>();
        for (JsonElement element : Json.array(json, "formatTags")) tags.add(element.getAsString());

        return new ServerState(Json.bool(json, "plus", false), parseComponent(Json.string(json, "lockedHint", null)),
                List.copyOf(emojis), Set.copyOf(tags), Json.bool(json, "typingIndicator", false), Json.bool(json, "admin", false));
    }

    private static Component parseComponent(String json) {
        if (json == null) return Component.literal("Нужна подписка Plus");
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result().orElseGet(() -> Component.literal("Нужна подписка Plus"));
        } catch (Exception e) {
            return Component.literal("Нужна подписка Plus");
        }
    }
}
