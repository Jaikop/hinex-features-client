package me.JAKOP.hinexFeatures.mod.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public final class DiscordPresence {

    private record Wanted(String appId, String details, String state, RemoteConfig.Discord config) {
    }

    private static final long STARTED = System.currentTimeMillis() / 1000;
    private static final long MIN_INTERVAL_MS = 4000;
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(15);

    private static volatile @Nullable Wanted wanted;
    private static @Nullable Thread worker;
    private static String lastKey = "";
    private static int ticks;

    private DiscordPresence() {
    }

    public static void tick(Minecraft mc) {
        if (++ticks % 20 != 0) return;
        RemoteConfig.Discord config = HinexFeaturesClient.config().discord();
        if (config.appId().isBlank()) return;
        String details = mc.getUser().getName();
        String state = state(mc);
        String key = config.appId() + '|' + details + '|' + state;
        if (key.equals(lastKey)) return;
        lastKey = key;
        wanted = new Wanted(config.appId(), details, state, config);
        if (worker == null) {
            worker = new Thread(DiscordPresence::run, "HinexFeatures-Discord");
            worker.setDaemon(true);
            worker.start();
        } else {
            LockSupport.unpark(worker);
        }
    }

    private static String state(Minecraft mc) {
        if (mc.level == null) return "В меню";
        if (mc.hasSingleplayerServer()) return "Одиночная игра";
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            String ip = host(server.ip);
            for (RemoteConfig.ServerEntry entry : HinexFeaturesClient.config().servers()) {
                if (host(entry.ip()).equals(ip)) return entry.name();
            }
        }
        return "Сетевая игра";
    }

    private static String host(String ip) {
        String host = ip.trim().toLowerCase(Locale.ROOT);
        return host.endsWith(":25565") ? host.substring(0, host.length() - 6) : host;
    }

    private static void run() {
        DiscordIpc ipc = null;
        String connectedApp = null;
        Wanted sent = null;
        long lastSent = 0;
        String lastProblem = "";
        while (true) {
            Wanted target = wanted;
            try {
                if (target != null && ipc != null && !target.appId().equals(connectedApp)) {
                    ipc.close();
                    ipc = null;
                }
                if (target != null && ipc == null) {
                    ipc = DiscordIpc.connect(target.appId());
                    connectedApp = target.appId();
                    sent = null;
                    String problem = ipc == null ? "Discord не запущен (нет discord-ipc) — пробую раз в 15 секунд" : "";
                    if (ipc != null) HinexFeaturesClient.LOGGER.info("Discord: подключился (приложение {})", target.appId());
                    else if (!problem.equals(lastProblem)) HinexFeaturesClient.LOGGER.info("Discord: {}", problem);
                    lastProblem = problem;
                }
                if (ipc != null && target != null && target != sent) {
                    long wait = lastSent + MIN_INTERVAL_MS - System.currentTimeMillis();
                    if (wait > 0) {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(wait));
                        continue;
                    }
                    ipc.write(DiscordIpc.FRAME, command(target));
                    JsonObject reply = ipc.read();
                    if ("ERROR".equals(reply.has("evt") && !reply.get("evt").isJsonNull() ? reply.get("evt").getAsString() : null)) {
                        HinexFeaturesClient.LOGGER.warn("Discord не принял статус: {}", reply);
                    } else if (sent == null) {
                        HinexFeaturesClient.LOGGER.info("Discord: статус показан ({} · {})", target.details(), target.state());
                    }
                    sent = target;
                    lastSent = System.currentTimeMillis();
                }
            } catch (IOException | RuntimeException e) {
                String problem = String.valueOf(e.getMessage());
                if (!problem.equals(lastProblem)) HinexFeaturesClient.LOGGER.warn("Discord: {}", problem);
                lastProblem = problem;
                if (ipc != null) ipc.close();
                ipc = null;
                sent = null;
            }
            LockSupport.parkNanos(RETRY_NANOS);
        }
    }

    private static JsonObject command(Wanted target) {
        JsonObject activity = new JsonObject();
        activity.addProperty("details", fit(target.details()));
        activity.addProperty("state", fit(target.state()));
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", STARTED);
        activity.add("timestamps", timestamps);
        RemoteConfig.Discord config = target.config();
        if (!config.image().isBlank()) {
            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", config.image());
            assets.addProperty("large_text", fit(config.imageText()));
            activity.add("assets", assets);
        }
        if (!config.buttons().isEmpty()) {
            JsonArray buttons = new JsonArray();
            for (RemoteConfig.DiscordButton button : config.buttons().subList(0, Math.min(2, config.buttons().size()))) {
                JsonObject json = new JsonObject();
                json.addProperty("label", button.label().length() > 32 ? button.label().substring(0, 32) : button.label());
                json.addProperty("url", button.url());
                buttons.add(json);
            }
            activity.add("buttons", buttons);
        }

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject command = new JsonObject();
        command.addProperty("cmd", "SET_ACTIVITY");
        command.add("args", args);
        command.addProperty("nonce", UUID.randomUUID().toString());
        return command;
    }

    private static String fit(String text) {
        if (text.length() < 2) return text + "  ";
        return text.length() > 128 ? text.substring(0, 128) : text;
    }
}
