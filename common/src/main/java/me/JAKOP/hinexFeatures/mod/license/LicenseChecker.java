package me.JAKOP.hinexFeatures.mod.license;

import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.config.LocalState;
import me.JAKOP.hinexFeatures.mod.util.Http;
import me.JAKOP.hinexFeatures.mod.util.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class LicenseChecker {

    public enum Status {
        CHECKING,
        LICENSED,
        UNLICENSED,
        ERROR
    }

    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final long OFFLINE_GRACE = TimeUnit.DAYS.toMillis(14);

    private final LocalState state;
    private volatile Status status = Status.CHECKING;

    public LicenseChecker(LocalState state) {
        this.state = state;
    }

    public Status status() {
        return status;
    }

    public void start() {
        status = Status.CHECKING;
        Thread thread = new Thread(this::check, "HinexFeatures-License");
        thread.setDaemon(true);
        thread.start();
    }

    private void check() {
        if (HinexFeaturesClient.platform().isDevEnvironment()) {
            status = Status.LICENSED;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        for (int waited = 0; minecraft == null && waited < 600; waited++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            minecraft = Minecraft.getInstance();
        }
        if (minecraft == null) {
            status = Status.ERROR;
            return;
        }

        User user = minecraft.getUser();
        UUID uuid = user.getProfileId();
        String token = user.getAccessToken();
        if (token == null || token.length() < 32 || token.equals("0") || token.equalsIgnoreCase("null")) {
            HinexFeaturesClient.LOGGER.info("Лицензия: у сессии нет токена Microsoft");
            status = Status.UNLICENSED;
            return;
        }

        try {
            HttpResponse<String> response = Http.CLIENT.send(
                    Http.request(PROFILE_URL, Duration.ofSeconds(12))
                            .header("Authorization", "Bearer " + token)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            int code = response.statusCode();
            if (code == 200) {
                JsonObject profile = Json.GSON.fromJson(response.body(), JsonObject.class);
                String id = Json.string(profile, "id", "");
                if (id.equalsIgnoreCase(uuid.toString().replace("-", ""))) {
                    state.rememberLicense(uuid);
                    status = Status.LICENSED;
                } else {
                    HinexFeaturesClient.LOGGER.info("Лицензия: профиль токена не совпадает с сессией");
                    status = Status.UNLICENSED;
                }
                return;
            }
            if (code == 401 || code == 403 || code == 404) {
                HinexFeaturesClient.LOGGER.info("Лицензия: API ответил {}", code);
                status = Status.UNLICENSED;
                return;
            }
            HinexFeaturesClient.LOGGER.warn("Лицензия: API ответил {}", code);
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.warn("Лицензия: API недоступен: {}", e.toString());
        }

        status = state.wasLicensed(uuid, OFFLINE_GRACE) ? Status.LICENSED : Status.ERROR;
    }
}
