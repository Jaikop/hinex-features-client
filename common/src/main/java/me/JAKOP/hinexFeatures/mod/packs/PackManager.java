package me.JAKOP.hinexFeatures.mod.packs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfig;
import me.JAKOP.hinexFeatures.mod.util.Http;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class PackManager {

    public static final String ID_PREFIX = "hinex/";
    private static final PackSelectionConfig LOCKED = new PackSelectionConfig(true, Pack.Position.TOP, true);
    private static final PackSource SOURCE = PackSource.create(
            description -> Component.translatable("pack.nameAndSource", description,
                    Component.literal("Hinex").withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY),
            true);

    public record ReadyPack(String id, String name, Path file) {
    }

    public record Progress(String name, long done, long total, int index, int count) {
    }

    private final Path dir;
    private final Path indexFile;
    private volatile List<ReadyPack> ready = List.of();
    private volatile Progress progress;
    private volatile boolean reloadRequested;

    public PackManager(Path dir) {
        this.dir = dir;
        this.indexFile = dir.resolve("index.json");
    }

    public void loadCached(RemoteConfig config) {
        JsonObject index = Json.read(indexFile);
        List<ReadyPack> result = new ArrayList<>();
        for (RemoteConfig.PackEntry entry : config.packs()) {
            JsonObject cached = Json.object(index, entry.id());
            String fileName = Json.string(cached, "file", null);
            if (fileName == null) continue;
            Path file = dir.resolve(fileName);
            if (Files.isRegularFile(file)) result.add(new ReadyPack(entry.id(), entry.name(), file));
        }
        ready = List.copyOf(result);
    }

    public void sync(RemoteConfig config) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            HinexFeaturesClient.LOGGER.error("Не удалось создать {}", dir, e);
            return;
        }

        JsonObject index = Optional.ofNullable(Json.read(indexFile)).orElseGet(JsonObject::new);
        JsonObject newIndex = new JsonObject();
        List<ReadyPack> result = new ArrayList<>();

        List<RemoteConfig.PackEntry> packs = config.packs();
        for (int i = 0; i < packs.size(); i++) {
            RemoteConfig.PackEntry entry = packs.get(i);
            JsonObject cached = Json.object(index, entry.id());
            JsonObject updated = syncPack(entry, cached, i, packs.size());
            if (updated == null && cached != null) updated = cached;
            if (updated == null) continue;

            Path file = dir.resolve(updated.get("file").getAsString());
            if (Files.isRegularFile(file)) {
                newIndex.add(entry.id(), updated);
                result.add(new ReadyPack(entry.id(), entry.name(), file));
            }
        }

        progress = null;
        Json.write(indexFile, newIndex);
        cleanup(newIndex);

        List<ReadyPack> previous = ready;
        ready = List.copyOf(result);
        if (!previous.equals(ready)) {
            HinexFeaturesClient.LOGGER.info("Ресурспаки Hinex обновлены: {}", ready.stream().map(ReadyPack::id).toList());
            reloadRequested = true;
        }
    }

    private JsonObject syncPack(RemoteConfig.PackEntry entry, JsonObject cached, int index, int count) {
        String cachedFile = Json.string(cached, "file", null);
        String cachedSha1 = Json.string(cached, "sha1", null);
        boolean haveFile = cachedFile != null && Files.isRegularFile(dir.resolve(cachedFile));

        if (haveFile && entry.sha1() != null && entry.sha1().equals(cachedSha1)) {
            return cached;
        }

        for (String url : entry.urls()) {
            try {
                HttpRequest.Builder request = Http.request(url, Duration.ofMinutes(5)).GET();
                if (haveFile && entry.sha1() == null && url.equals(Json.string(cached, "url", null))) {
                    String etag = Json.string(cached, "etag", null);
                    if (etag != null) request.header("If-None-Match", etag);
                }

                HttpResponse<InputStream> response = Http.CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 304) {
                    response.body().close();
                    return cached;
                }
                if (response.statusCode() != 200) {
                    response.body().close();
                    HinexFeaturesClient.LOGGER.warn("Пак {}: {} ответил {}", entry.id(), url, response.statusCode());
                    continue;
                }

                long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                Path part = dir.resolve(entry.id() + ".part");
                String sha1 = download(response.body(), part, entry.name(), total, index, count);

                if (entry.sha1() != null && !entry.sha1().equals(sha1)) {
                    Files.deleteIfExists(part);
                    HinexFeaturesClient.LOGGER.warn("Пак {}: хеш не совпал (ожидался {}, получен {})", entry.id(), entry.sha1(), sha1);
                    continue;
                }

                String fileName = entry.id() + "-" + sha1.substring(0, 12) + ".zip";
                Path target = dir.resolve(fileName);
                if (!Files.exists(target)) {
                    Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(part);
                }

                JsonObject result = new JsonObject();
                result.addProperty("file", fileName);
                result.addProperty("sha1", sha1);
                result.addProperty("url", url);
                response.headers().firstValue("ETag").ifPresent(etag -> result.addProperty("etag", etag));
                HinexFeaturesClient.LOGGER.info("Пак {} скачан с {} ({})", entry.id(), url, fileName);
                return result;
            } catch (Exception e) {
                HinexFeaturesClient.LOGGER.warn("Пак {}: ошибка загрузки с {}: {}", entry.id(), url, e.toString());
            }
        }
        return null;
    }

    private String download(InputStream in, Path part, String name, long total, int index, int count) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] buffer = new byte[64 * 1024];
        long done = 0;
        try (in; OutputStream out = Files.newOutputStream(part)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                done += read;
                progress = new Progress(name, done, total, index, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void cleanup(JsonObject index) {
        Set<String> keep = new HashSet<>();
        keep.add(indexFile.getFileName().toString());
        for (var entry : index.entrySet()) {
            JsonElement file = entry.getValue().getAsJsonObject().get("file");
            if (file != null) keep.add(file.getAsString());
        }
        for (ReadyPack pack : ready) keep.add(pack.file().getFileName().toString());

        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                if (!keep.contains(file.getFileName().toString())) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            HinexFeaturesClient.LOGGER.warn("Не удалось очистить {}", dir, e);
        }
    }

    public List<ReadyPack> ready() {
        return ready;
    }

    public Progress progress() {
        return progress;
    }

    public boolean takeReloadRequest() {
        boolean requested = reloadRequested;
        reloadRequested = false;
        return requested;
    }

    public boolean isHinexPack(Pack pack) {
        return pack.getId().startsWith(ID_PREFIX);
    }

    public int order(Pack pack) {
        List<ReadyPack> packs = ready;
        for (int i = 0; i < packs.size(); i++) {
            if (pack.getId().equals(ID_PREFIX + packs.get(i).id())) return i;
        }
        return Integer.MAX_VALUE;
    }

    public RepositorySource repositorySource() {
        return this::loadPacks;
    }

    private void loadPacks(Consumer<Pack> result) {
        for (ReadyPack ready : this.ready) {
            PackLocationInfo location = new PackLocationInfo(ID_PREFIX + ready.id(),
                    Component.literal(ready.name()), SOURCE, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(location, new FilePackResources.FileResourcesSupplier(ready.file()),
                    PackType.CLIENT_RESOURCES, LOCKED);
            if (pack != null) {
                result.accept(pack);
            } else {
                HinexFeaturesClient.LOGGER.warn("Пак {} повреждён или без pack.mcmeta: {}", ready.id(), ready.file());
            }
        }
    }
}
