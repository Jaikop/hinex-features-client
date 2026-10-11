package me.JAKOP.hinexFeatures.mod.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.Protocol;
import me.JAKOP.hinexFeatures.mod.util.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkspaceClient {

    public static final class Node {
        public final String id;
        public final String type;
        public int x;
        public int y;
        public JsonObject props;

        Node(String id, String type, int x, int y, JsonObject props) {
            this.id = id;
            this.type = type;
            this.x = x;
            this.y = y;
            this.props = props;
        }

        public String str(String key, String fallback) {
            return props.has(key) && props.get(key).isJsonPrimitive() ? props.get(key).getAsString() : fallback;
        }

        public boolean bool(String key) {
            return props.has(key) && props.get(key).isJsonPrimitive() && props.get(key).getAsBoolean();
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("type", type);
            json.addProperty("x", x);
            json.addProperty("y", y);
            json.add("props", props);
            return json;
        }
    }

    public record Wire(String id, String from, int fromPort, String to, int toPort) {
    }

    static final Map<String, Node> NODES = new LinkedHashMap<>();
    static final Map<String, Wire> WIRES = new LinkedHashMap<>();
    static final Map<String, boolean[]> LEVELS = new HashMap<>();
    static final List<JsonObject> LOG = new ArrayList<>();
    static final List<String> EDITORS = new ArrayList<>();
    static boolean running = true;
    static boolean loaded;
    static int version;

    private WorkspaceClient() {
    }

    public static void handle(JsonObject message) {
        if (message.has("graph") && message.get("graph").isJsonObject()) {
            JsonObject graph = message.getAsJsonObject("graph");
            NODES.clear();
            WIRES.clear();
            for (JsonElement element : Json.array(graph, "nodes")) {
                JsonObject node = element.getAsJsonObject();
                String id = Json.string(node, "id", "");
                NODES.put(id, new Node(id, Json.string(node, "type", "note"), node.get("x").getAsInt(), node.get("y").getAsInt(),
                        Json.object(node, "props") != null ? node.getAsJsonObject("props") : new JsonObject()));
            }
            for (JsonElement element : Json.array(graph, "wires")) {
                JsonObject wire = element.getAsJsonObject();
                String id = Json.string(wire, "id", "");
                WIRES.put(id, new Wire(id, Json.string(wire, "from", ""), wire.get("fromPort").getAsInt(), Json.string(wire, "to", ""),
                        wire.get("toPort").getAsInt()));
            }
            running = Json.bool(graph, "running", true);
            loaded = true;
            version++;
        }
        if (message.has("state") && message.get("state").isJsonObject()) {
            JsonObject state = message.getAsJsonObject("state");
            LEVELS.clear();
            JsonObject levels = Json.object(state, "levels");
            if (levels != null) {
                for (Map.Entry<String, JsonElement> entry : levels.entrySet()) {
                    JsonArray array = entry.getValue().getAsJsonArray();
                    boolean[] out = new boolean[array.size()];
                    for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsBoolean();
                    LEVELS.put(entry.getKey(), out);
                }
            }
            running = Json.bool(state, "running", running);
            EDITORS.clear();
            for (JsonElement element : Json.array(state, "editors")) EDITORS.add(element.getAsString());
        }
        if (message.has("log") && message.get("log").isJsonArray()) {
            if (Json.bool(message, "fullLog", false)) LOG.clear();
            for (JsonElement element : message.getAsJsonArray("log")) LOG.add(element.getAsJsonObject());
            while (LOG.size() > 120) LOG.removeFirst();
        }
    }

    public static boolean loaded() {
        return loaded;
    }

    public static Map<String, Node> nodes() {
        return NODES;
    }

    public static Map<String, Wire> wires() {
        return WIRES;
    }

    public static List<JsonObject> log() {
        return LOG;
    }

    public static boolean level(String node, int port) {
        boolean[] out = LEVELS.get(node);
        return out != null && port < out.length && out[port];
    }

    public static void send(String action, JsonObject extra) {
        JsonObject message = Protocol.message(Protocol.WORKSPACE);
        message.addProperty("action", action);
        extra.entrySet().forEach(entry -> message.add(entry.getKey(), entry.getValue()));
        ClientNetwork.send(message);
    }

    public static void edit(JsonObject... ops) {
        JsonArray array = new JsonArray();
        for (JsonObject op : ops) array.add(op);
        JsonObject extra = new JsonObject();
        extra.add("ops", array);
        send("edit", extra);
    }

    public static void reset() {
        NODES.clear();
        WIRES.clear();
        LEVELS.clear();
        LOG.clear();
        EDITORS.clear();
        loaded = false;
    }
}
