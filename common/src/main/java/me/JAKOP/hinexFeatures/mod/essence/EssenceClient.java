package me.JAKOP.hinexFeatures.mod.essence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

public final class EssenceClient {

    private static volatile boolean enabled;
    private static volatile int essence;
    private static volatile int global;
    private static @Nullable KnowledgeData tree;
    private static int version;

    private EssenceClient() {
    }

    public static void reset() {
        enabled = false;
        essence = 0;
        global = 0;
        tree = null;
        version++;
        EssenceHud.reset();
    }

    public static boolean available() {
        return enabled && ClientNetwork.state() != null;
    }

    public static int essence() {
        return essence;
    }

    public static int global() {
        return global;
    }

    public static @Nullable KnowledgeData tree() {
        return tree;
    }

    public static int version() {
        return version;
    }

    public static void handleEssence(JsonObject message) {
        enabled = Json.bool(message, "enabled", false);
        if (!enabled) {
            tree = null;
            version++;
            return;
        }
        essence = message.has("essence") ? message.get("essence").getAsInt() : essence;
        global = message.has("global") ? message.get("global").getAsInt() : global;
        int gained = message.has("gained") ? message.get("gained").getAsInt() : 0;
        if (gained > 0) EssenceHud.gain(gained, essence);
        KnowledgeData current = tree;
        if (current != null) {
            current.essence = essence;
            current.global = global;
        }
        for (JsonElement element : Json.array(message, "unlocked")) {
            JsonObject node = element.getAsJsonObject();
            EssenceHud.unlocked(ItemViews.stack(node.has("icon") ? node.getAsJsonObject("icon") : null),
                    ItemViews.text(Json.string(node, "name", "")));
            if (current != null) {
                KnowledgeData.Node known = current.node(Json.string(node, "id", ""));
                if (known != null) known.unlocked = true;
            }
        }
        version++;
    }

    public static void handleTree(JsonObject message) {
        tree = KnowledgeData.parse(message);
        essence = tree.essence;
        global = tree.global;
        version++;
        Minecraft minecraft = Minecraft.getInstance();
        boolean requested = System.currentTimeMillis() - requestedAt < 5000;
        requestedAt = 0;
        if (!requested && !(minecraft.gui.screen() instanceof KnowledgeScreen) && minecraft.level != null) {
            minecraft.gui.setScreen(new KnowledgeScreen(minecraft.gui.screen()));
        }
    }

    private static long requestedAt;

    public static void open(@Nullable Screen parent) {
        JsonObject request = Protocol.message(Protocol.KNOWLEDGE);
        request.addProperty("action", "open");
        requestedAt = System.currentTimeMillis();
        ClientNetwork.send(request);
        Minecraft.getInstance().gui.setScreen(new KnowledgeScreen(parent));
    }
}
