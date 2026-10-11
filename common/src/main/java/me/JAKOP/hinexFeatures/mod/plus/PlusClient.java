package me.JAKOP.hinexFeatures.mod.plus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import me.JAKOP.hinexFeatures.mod.util.Json;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class PlusClient {

    public record Info(boolean enabled, @Nullable String suffix, @Nullable String colorCode, int color, boolean hideJoin,
                       int maxSuffix, int maxRaw, Set<String> tags, boolean space) {

        static final Info EMPTY = new Info(false, null, null, -1, false, 35, 200, Set.of("color", "decorations", "reset"), true);
    }

    private static volatile @Nullable Info info;
    private static volatile int version;

    private PlusClient() {
    }

    public static void reset() {
        info = null;
        version++;
    }

    public static void handle(JsonObject message) {
        boolean enabled = message.has("enabled") && message.get("enabled").getAsBoolean();
        int color = -1;
        String hex = Json.string(message, "color", null);
        if (hex != null && hex.startsWith("#") && hex.length() == 7) {
            try {
                color = Integer.parseInt(hex.substring(1), 16);
            } catch (NumberFormatException ignored) {
            }
        }
        Set<String> tags = new HashSet<>(Info.EMPTY.tags());
        for (JsonElement tag : Json.array(message, "tags")) tags.add(tag.getAsString());
        int maxSuffix = message.has("maxSuffix") ? message.get("maxSuffix").getAsInt() : 35;
        info = new Info(enabled, Json.string(message, "suffix", null), Json.string(message, "colorCode", null), color,
                message.has("hideJoin") && message.get("hideJoin").getAsBoolean(),
                maxSuffix, message.has("maxSuffixRaw") ? message.get("maxSuffixRaw").getAsInt() : maxSuffix,
                Set.copyOf(tags), !message.has("space") || message.get("space").getAsBoolean());
        version++;
    }

    public static @Nullable Info info() {
        return info;
    }

    public static int version() {
        return version;
    }

    public static boolean available() {
        ServerState state = ClientNetwork.state();
        Info current = info;
        return state != null && state.plus() && current != null && current.enabled();
    }
}
