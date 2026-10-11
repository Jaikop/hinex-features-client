package me.JAKOP.hinexFeatures.mod.config;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.util.Http;
import me.JAKOP.hinexFeatures.mod.util.Json;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class RemoteConfigLoader {

    private final Path cacheFile;
    private volatile RemoteConfig current;
    private volatile boolean fresh;
    private final CompletableFuture<RemoteConfig> loaded = new CompletableFuture<>();

    public RemoteConfigLoader(Path cacheFile) {
        this.cacheFile = cacheFile;
        JsonObject cached = Json.read(cacheFile);
        this.current = cached != null ? safeParse(cached) : RemoteConfig.defaults();
    }

    public RemoteConfig current() {
        return current;
    }

    public boolean isFresh() {
        return fresh;
    }

    public CompletableFuture<RemoteConfig> loaded() {
        return loaded;
    }

    public void start() {
        Thread thread = new Thread(this::fetch, "HinexFeatures-Config");
        thread.setDaemon(true);
        thread.start();
    }

    private void fetch() {
        for (String url : HinexConstants.configUrls()) {
            try {
                String requestUrl = url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis() / 60000;
                HttpResponse<String> response = Http.CLIENT.send(
                        Http.request(requestUrl, Duration.ofSeconds(10)).header("Cache-Control", "no-cache").GET().build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    HinexFeaturesClient.LOGGER.warn("config.json: {} ответил {}", url, response.statusCode());
                    continue;
                }

                JsonObject json = Json.GSON.fromJson(response.body(), JsonObject.class);
                RemoteConfig config = RemoteConfig.parse(json);
                Json.write(cacheFile, json);
                current = config;
                fresh = true;
                HinexFeaturesClient.LOGGER.info("config.json загружен с {}: паков {}, серверов {}",
                        url, config.packs().size(), config.servers().size());
                loaded.complete(config);
                return;
            } catch (Exception e) {
                HinexFeaturesClient.LOGGER.warn("config.json: не удалось загрузить {}: {}", url, e.toString());
            }
        }
        HinexFeaturesClient.LOGGER.warn("config.json недоступен, используется сохранённая копия");
        loaded.complete(current);
    }

    private static RemoteConfig safeParse(JsonObject json) {
        try {
            return RemoteConfig.parse(json);
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Сохранённый config.json повреждён", e);
            return RemoteConfig.defaults();
        }
    }
}
