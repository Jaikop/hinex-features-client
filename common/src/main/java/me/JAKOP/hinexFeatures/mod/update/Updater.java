package me.JAKOP.hinexFeatures.mod.update;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfig;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import me.JAKOP.hinexFeatures.mod.util.Http;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

public final class Updater {

    public enum Status { IDLE, DOWNLOADING, RESTARTING, FAILED }

    public record Release(String version, List<String> changelog, RemoteConfig.ModFile file) {
    }

    private static volatile Status status = Status.IDLE;
    private static volatile float progress;
    private static volatile String error = "";

    private Updater() {
    }

    public static @Nullable Release available() {
        Platform platform = HinexFeaturesClient.platform();
        RemoteConfig.ModRelease mod = HinexFeaturesClient.config().mod();
        if (platform == null || mod == null) return null;
        if (mod.minecraft() != null && !mod.minecraft().equals(SharedConstants.getCurrentVersion().name())) return null;
        RemoteConfig.ModFile file = mod.files().get(platform.loaderName());
        if (file == null || compare(mod.version(), platform.modVersion()) <= 0 || modJar() == null) return null;
        return new Release(mod.version(), mod.changelog(), file);
    }

    public static Status status() {
        return status;
    }

    public static float progress() {
        return progress;
    }

    public static String error() {
        return error;
    }

    private static @Nullable Path modJar() {
        Path jar = HinexFeaturesClient.platform().modJar();
        String test = System.getProperty("hinex.update.jar");
        if (jar == null && test != null && HinexFeaturesClient.platform().isDevEnvironment() && Files.isRegularFile(Path.of(test))) {
            jar = Path.of(test);
        }
        return jar;
    }

    public static synchronized void start(Release release) {
        if (status == Status.DOWNLOADING || status == Status.RESTARTING) return;
        status = Status.DOWNLOADING;
        progress = 0;
        error = "";
        Thread thread = new Thread(() -> download(release), "HinexFeatures-Update");
        thread.setDaemon(true);
        thread.start();
    }

    private static void download(Release release) {
        Path jar = modJar();
        if (jar == null) {
            fail("мод установлен не из jar");
            return;
        }
        String fileName = fileName(release);
        Path target = jar.resolveSibling(fileName);
        Path pending = jar.resolveSibling(fileName + ".pending");
        Path part = jar.resolveSibling(fileName + ".part");
        String problem = "нет ссылки на файл";
        for (String url : release.file().urls()) {
            try {
                HttpResponse<InputStream> response = Http.CLIENT.send(Http.request(url, Duration.ofMinutes(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    problem = "сервер ответил " + response.statusCode();
                    HinexFeaturesClient.LOGGER.warn("Обновление: {} ответил {}", url, response.statusCode());
                    continue;
                }
                long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[64 * 1024];
                    long done = 0;
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        done += read;
                        if (total > 0) progress = Math.min(1f, (float) done / total);
                    }
                }
                String sha256 = HexFormat.of().formatHex(digest.digest());
                if (release.file().sha256() != null && !release.file().sha256().equals(sha256)) {
                    Files.deleteIfExists(part);
                    problem = "файл повреждён (не совпал sha256)";
                    HinexFeaturesClient.LOGGER.warn("Обновление: {} — sha256 {} вместо {}", url, sha256, release.file().sha256());
                    continue;
                }
                if (!isZip(part)) {
                    Files.deleteIfExists(part);
                    problem = "по ссылке не jar";
                    continue;
                }
                Files.move(part, pending, StandardCopyOption.REPLACE_EXISTING);
                Json.write(sidecar(), pendingJson(jar, pending, target, release.version()));
                progress = 1;
                HinexFeaturesClient.LOGGER.info("Обновление {} скачано: {}", release.version(), pending.getFileName());
                Minecraft.getInstance().execute(() -> restart(jar, pending, target));
                return;
            } catch (Exception e) {
                problem = e instanceof IOException ? "нет связи с " + host(url) : e.toString();
                HinexFeaturesClient.LOGGER.warn("Обновление: не удалось скачать {}", url, e);
            }
        }
        try {
            Files.deleteIfExists(part);
        } catch (IOException ignored) {
        }
        fail(problem);
    }

    private static void fail(String message) {
        error = message;
        status = Status.FAILED;
    }

    private static void restart(Path jar, Path pending, Path target) {
        boolean relaunch = HinexFeaturesClient.platform().modJar() != null;
        if (!ExitActions.schedule(List.of(ExitActions.delete(jar), ExitActions.move(pending, target)), relaunch)) {
            fail("не удалось запустить установку");
            return;
        }
        status = Status.RESTARTING;
    }

    public static void init() {
        JsonObject json = Json.read(sidecar());
        if (json == null) return;
        Path pending = Path.of(Json.string(json, "pending", ""));
        Path target = Path.of(Json.string(json, "target", ""));
        Path jar = modJar();
        boolean newer = compare(Json.string(json, "version", "0"), HinexFeaturesClient.platform().modVersion()) > 0;
        try {
            if (newer && jar != null && Files.isRegularFile(pending)) {
                HinexFeaturesClient.LOGGER.info("Обновление {} поставится при выходе из игры", Json.string(json, "version", "?"));
                Runtime.getRuntime().addShutdownHook(new Thread(() -> ExitActions.schedule(
                        List.of(ExitActions.delete(jar), ExitActions.move(pending, target)), false), "HinexFeatures-Update"));
                return;
            }
            if (!newer) Files.deleteIfExists(pending);
            Files.deleteIfExists(sidecar());
        } catch (IOException e) {
            HinexFeaturesClient.LOGGER.warn("Обновление: не удалось убрать старые файлы", e);
        }
    }

    private static Path sidecar() {
        return HinexFeaturesClient.platform().gameDir().resolve("hinex").resolve("update-pending.json");
    }

    private static JsonObject pendingJson(Path jar, Path pending, Path target, String version) {
        JsonObject json = new JsonObject();
        json.addProperty("version", version);
        json.addProperty("jar", jar.toString());
        json.addProperty("pending", pending.toString());
        json.addProperty("target", target.toString());
        return json;
    }

    private static String fileName(Release release) {
        try {
            String path = URI.create(release.file().urls().getFirst()).getPath();
            String name = URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
            if (name.toLowerCase(Locale.ROOT).endsWith(".jar") && name.matches("[A-Za-z0-9._+-]+")) return name;
        } catch (RuntimeException ignored) {
        }
        return "hinexfeatures-" + HinexFeaturesClient.platform().loaderName() + "-" + release.version() + ".jar";
    }

    private static boolean isZip(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.read() == 'P' && in.read() == 'K';
        }
    }

    private static String host(String url) {
        try {
            return URI.create(url).getHost();
        } catch (RuntimeException e) {
            return url;
        }
    }

    static int compare(String a, String b) {
        String[] left = a.trim().split("[.\\-+_]");
        String[] right = b.trim().split("[.\\-+_]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            String x = i < left.length ? left[i] : "0";
            String y = i < right.length ? right[i] : "0";
            int result = x.matches("\\d+") && y.matches("\\d+")
                    ? Long.compare(Long.parseLong(x), Long.parseLong(y))
                    : x.compareToIgnoreCase(y);
            if (result != 0) return result;
        }
        return 0;
    }
}
