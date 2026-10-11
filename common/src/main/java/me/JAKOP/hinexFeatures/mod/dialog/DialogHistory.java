package me.JAKOP.hinexFeatures.mod.dialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DialogHistory {

    public static final class Entry {
        public final JsonObject dialog;
        public final long shown;
        public final String server;
        public @Nullable String answer;
        private @Nullable DialogView view;

        Entry(JsonObject dialog, long shown, String server, @Nullable String answer) {
            this.dialog = dialog;
            this.shown = shown;
            this.server = server;
            this.answer = answer;
        }

        public DialogView view() {
            if (view == null) view = DialogView.of(dialog);
            return view;
        }

        public String id() {
            return dialog.has("id") ? dialog.get("id").getAsString() : "";
        }
    }

    private static final int LIMIT = 1000;
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "HinexFeatures-DialogHistory");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean loaded;

    private DialogHistory() {
    }

    private static Path file() {
        return HinexFeaturesClient.platform().gameDir().resolve("hinex").resolve("dialog-history.json");
    }

    public static synchronized List<Entry> entries() {
        load();
        return List.copyOf(ENTRIES);
    }

    public static synchronized void add(JsonObject dialog) {
        load();
        String id = dialog.has("id") ? dialog.get("id").getAsString() : "";
        if (!id.isEmpty()) {
            for (Entry entry : ENTRIES) if (entry.id().equals(id)) return;
        }
        ENTRIES.addFirst(new Entry(dialog.deepCopy(), System.currentTimeMillis(), serverName(), null));
        while (ENTRIES.size() > LIMIT) ENTRIES.removeLast();
        save();
    }

    public static synchronized void answer(String id, String answer) {
        load();
        for (Entry entry : ENTRIES) {
            if (entry.id().equals(id)) {
                entry.answer = answer;
                save();
                return;
            }
        }
    }

    private static String serverName() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null) return Minecraft.getInstance().hasSingleplayerServer() ? "Одиночная игра" : "—";
        return server.name == null || server.name.isBlank() ? server.ip : server.name;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        JsonObject json = Json.read(file());
        for (JsonElement element : Json.array(json, "entries")) {
            try {
                JsonObject object = element.getAsJsonObject();
                ENTRIES.add(new Entry(object.getAsJsonObject("dialog"), object.get("shown").getAsLong(),
                        Json.string(object, "server", "—"), Json.string(object, "answer", null)));
            } catch (RuntimeException e) {
            }
        }
    }

    private static void save() {
        JsonArray array = new JsonArray();
        for (Entry entry : ENTRIES) {
            JsonObject object = new JsonObject();
            object.add("dialog", entry.dialog);
            object.addProperty("shown", entry.shown);
            object.addProperty("server", entry.server);
            if (entry.answer != null) object.addProperty("answer", entry.answer);
            array.add(object);
        }
        JsonObject json = new JsonObject();
        json.add("entries", array);
        Path file = file();
        IO.execute(() -> Json.write(file, json));
    }
}
