package me.JAKOP.hinexFeatures.mod.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.util.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LocalState {

    private static final int RECENT_LIMIT = 9;

    private final Path file;
    private String installId;
    private String tosAccepted;
    private String licensedUuid;
    private long licensedAt;
    private final Set<String> pinnedIps = new LinkedHashSet<>();
    private final List<String> recentEmojis = new ArrayList<>();
    private final List<Integer> recentColors = new ArrayList<>();

    public LocalState(Path file) {
        this.file = file;
    }

    public synchronized void load() {
        JsonObject json = Json.read(file);
        installId = Json.string(json, "installId", null);
        tosAccepted = Json.string(json, "tosAccepted", null);

        JsonObject license = Json.object(json, "license");
        licensedUuid = Json.string(license, "uuid", null);
        licensedAt = license != null && license.has("checkedAt") ? license.get("checkedAt").getAsLong() : 0L;

        pinnedIps.clear();
        for (JsonElement element : Json.array(json, "pinnedIps")) pinnedIps.add(element.getAsString());
        recentEmojis.clear();
        for (JsonElement element : Json.array(json, "recentEmojis")) recentEmojis.add(element.getAsString());
        recentColors.clear();
        for (JsonElement element : Json.array(json, "recentColors")) recentColors.add(element.getAsInt());

        if (installId == null) {
            installId = UUID.randomUUID().toString();
            save();
        }
    }

    public synchronized void save() {
        JsonObject json = new JsonObject();
        json.addProperty("installId", installId);
        if (tosAccepted != null) json.addProperty("tosAccepted", tosAccepted);
        if (licensedUuid != null) {
            JsonObject license = new JsonObject();
            license.addProperty("uuid", licensedUuid);
            license.addProperty("checkedAt", licensedAt);
            json.add("license", license);
        }
        JsonArray ips = new JsonArray();
        pinnedIps.forEach(ips::add);
        json.add("pinnedIps", ips);
        JsonArray emojis = new JsonArray();
        recentEmojis.forEach(emojis::add);
        json.add("recentEmojis", emojis);
        JsonArray colors = new JsonArray();
        recentColors.forEach(colors::add);
        json.add("recentColors", colors);
        Json.write(file, json);
    }

    public synchronized String installId() {
        return installId;
    }

    public synchronized String tosAccepted() {
        return tosAccepted;
    }

    public synchronized void acceptTos(String version) {
        tosAccepted = version;
        save();
    }

    public synchronized boolean wasLicensed(UUID uuid, long maxAgeMillis) {
        return uuid.toString().equals(licensedUuid) && System.currentTimeMillis() - licensedAt < maxAgeMillis;
    }

    public synchronized void rememberLicense(UUID uuid) {
        licensedUuid = uuid.toString();
        licensedAt = System.currentTimeMillis();
        save();
    }

    public synchronized Set<String> pinnedIps() {
        return new LinkedHashSet<>(pinnedIps);
    }

    public synchronized void setPinnedIps(Set<String> ips) {
        if (pinnedIps.equals(ips)) return;
        pinnedIps.clear();
        pinnedIps.addAll(ips);
        save();
    }

    public synchronized List<String> recentEmojis() {
        return List.copyOf(recentEmojis);
    }

    public synchronized void useEmoji(String symbol) {
        recentEmojis.remove(symbol);
        recentEmojis.addFirst(symbol);
        while (recentEmojis.size() > RECENT_LIMIT) recentEmojis.removeLast();
        save();
    }

    public synchronized List<Integer> recentColors() {
        return List.copyOf(recentColors);
    }

    public synchronized void useColor(int rgb) {
        recentColors.remove((Integer) rgb);
        recentColors.addFirst(rgb);
        while (recentColors.size() > 8) recentColors.removeLast();
        save();
    }
}
