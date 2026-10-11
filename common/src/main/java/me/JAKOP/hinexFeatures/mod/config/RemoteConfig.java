package me.JAKOP.hinexFeatures.mod.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.util.Json;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record RemoteConfig(String tosUrl, String tosUpdated, List<PackEntry> packs, List<ServerEntry> servers,
                           @Nullable ModRelease mod, Discord discord, List<String> blockedMods) {

    public record Discord(String appId, String image, String imageText, List<DiscordButton> buttons) {

        static Discord defaults() {
            return new Discord(HinexConstants.DISCORD_APP_ID, HinexConstants.DISCORD_IMAGE, "HINEX",
                    List.of(new DiscordButton("Telegram", HinexConstants.TELEGRAM_URL)));
        }
    }

    public record DiscordButton(String label, String url) {
    }

    public record ModRelease(String version, @Nullable String minecraft, List<String> changelog, Map<String, ModFile> files) {
    }

    public record ModFile(List<String> urls, @Nullable String sha256) {
    }

    public record PackEntry(String id, String name, List<String> urls, String sha1) {
    }

    public record ServerEntry(String name, String ip, boolean main, String separatorBelow) {
    }

    public static RemoteConfig defaults() {
        return new RemoteConfig(HinexConstants.DEFAULT_TOS_URL, null, List.of(), List.of(
                new ServerEntry("HINEX | HUB", "hinex.joinserver.xyz", true, null),
                new ServerEntry("HINEX | MAIN & OFF-SEASON", "mc.hinex-server.ru", true, "Резервные сервера"),
                new ServerEntry("HINEX | RESERV", "hinexreserv.joinserver.xyz", false, null),
                new ServerEntry("HINEX | RESERV2 (Shield)", "shield.hinex-server.ru", false, null)
        ), null, Discord.defaults(), HinexConstants.BLOCKED_MODS);
    }

    public static RemoteConfig parse(JsonObject json) {
        RemoteConfig defaults = defaults();

        JsonObject tos = Json.object(json, "tos");
        String tosUrl = Json.string(tos, "url", defaults.tosUrl());
        String tosUpdated = Json.string(tos, "updated", null);

        List<PackEntry> packs = new ArrayList<>();
        for (JsonElement element : Json.array(json, "resourcepacks")) {
            if (!element.isJsonObject()) continue;
            JsonObject pack = element.getAsJsonObject();
            String id = sanitizeId(Json.string(pack, "id", null));
            String url = Json.string(pack, "url", null);
            if (id == null || url == null || url.isBlank()) continue;

            List<String> urls = new ArrayList<>();
            urls.add(url);
            for (JsonElement mirror : Json.array(pack, "mirrors")) urls.add(mirror.getAsString());
            String sha1 = Json.string(pack, "sha1", "").trim().toLowerCase();
            packs.add(new PackEntry(id, Json.string(pack, "name", id), List.copyOf(urls), sha1.isEmpty() ? null : sha1));
        }

        List<ServerEntry> servers = new ArrayList<>();
        if (json.has("servers")) {
            for (JsonElement element : Json.array(json, "servers")) {
                if (!element.isJsonObject()) continue;
                JsonObject server = element.getAsJsonObject();
                if (server.has("separator")) {
                    if (!servers.isEmpty()) {
                        ServerEntry last = servers.removeLast();
                        servers.add(new ServerEntry(last.name(), last.ip(), last.main(), Json.string(server, "separator", "")));
                    }
                    continue;
                }
                String ip = Json.string(server, "ip", null);
                if (ip == null || ip.isBlank()) continue;
                boolean main = "main".equalsIgnoreCase(Json.string(server, "style", "reserve"));
                servers.add(new ServerEntry(Json.string(server, "name", ip), ip.trim(), main, null));
            }
        } else {
            servers.addAll(defaults.servers());
        }

        List<String> blocked = new ArrayList<>();
        if (json.has("blocked_mods")) {
            for (JsonElement id : Json.array(json, "blocked_mods")) {
                if (id.isJsonPrimitive() && !id.getAsString().isBlank()) blocked.add(id.getAsString().strip().toLowerCase(Locale.ROOT));
            }
        } else {
            blocked.addAll(defaults.blockedMods());
        }
        return new RemoteConfig(tosUrl, tosUpdated, List.copyOf(packs), List.copyOf(servers), parseMod(Json.object(json, "mod")),
                parseDiscord(Json.object(json, "discord")), List.copyOf(blocked));
    }

    private static Discord parseDiscord(@Nullable JsonObject discord) {
        Discord defaults = Discord.defaults();
        if (discord == null) return defaults;
        List<DiscordButton> buttons = new ArrayList<>();
        if (discord.has("buttons")) {
            for (JsonElement element : Json.array(discord, "buttons")) {
                if (!element.isJsonObject()) continue;
                String label = Json.string(element.getAsJsonObject(), "label", "");
                String url = Json.string(element.getAsJsonObject(), "url", "");
                if (!label.isBlank() && url.startsWith("https://")) buttons.add(new DiscordButton(label, url));
            }
        } else {
            buttons.addAll(defaults.buttons());
        }
        return new Discord(Json.string(discord, "app_id", defaults.appId()).trim(), Json.string(discord, "image", defaults.image()).trim(),
                Json.string(discord, "image_text", defaults.imageText()), List.copyOf(buttons));
    }

    private static @Nullable ModRelease parseMod(@Nullable JsonObject mod) {
        String version = mod == null ? null : Json.string(mod, "version", null);
        if (version == null || version.isBlank()) return null;
        List<String> changelog = new ArrayList<>();
        for (JsonElement line : Json.array(mod, "changelog")) changelog.add(line.getAsString());
        Map<String, ModFile> files = new LinkedHashMap<>();
        JsonObject filesJson = Json.object(mod, "files");
        if (filesJson != null) {
            for (String loader : filesJson.keySet()) {
                if (!filesJson.get(loader).isJsonObject()) continue;
                JsonObject file = filesJson.getAsJsonObject(loader);
                String url = Json.string(file, "url", null);
                if (url == null || url.isBlank()) continue;
                List<String> urls = new ArrayList<>();
                urls.add(url.trim());
                for (JsonElement mirror : Json.array(file, "mirrors")) urls.add(mirror.getAsString().trim());
                String sha256 = Json.string(file, "sha256", "").trim().toLowerCase(Locale.ROOT);
                files.put(loader.toLowerCase(Locale.ROOT), new ModFile(List.copyOf(urls), sha256.isEmpty() ? null : sha256));
            }
        }
        String minecraft = Json.string(mod, "minecraft", "").trim();
        return new ModRelease(version.trim(), minecraft.isEmpty() ? null : minecraft, List.copyOf(changelog), Map.copyOf(files));
    }

    private static String sanitizeId(String id) {
        if (id == null) return null;
        String clean = id.toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
        return clean.isEmpty() ? null : clean;
    }
}
