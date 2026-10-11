package me.JAKOP.hinexFeatures.mod.update;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockedMods {

    public enum Status { IDLE, RESTARTING, FAILED }

    public record Found(String id, String name, @Nullable Path file) {
    }

    private static volatile Status status = Status.IDLE;
    private static volatile String error = "";

    private BlockedMods() {
    }

    public static List<Found> found() {
        Platform platform = HinexFeaturesClient.platform();
        if (platform == null) return List.of();
        List<String> blocked = HinexFeaturesClient.config().blockedMods();
        if (blocked.isEmpty()) return List.of();
        Map<String, Platform.ModInfo> installed = new LinkedHashMap<>();
        for (Platform.ModInfo mod : platform.mods()) installed.put(mod.id().toLowerCase(Locale.ROOT), mod);
        List<Found> found = new ArrayList<>();
        for (String id : blocked) {
            Platform.ModInfo mod = installed.get(id);
            Path file = platform.modFile(id);
            if (mod == null && file == null) continue;
            found.add(new Found(id, mod != null ? mod.name() : id, file));
        }
        return found;
    }

    public static Status status() {
        return status;
    }

    public static String error() {
        return error;
    }

    public static synchronized void disable() {
        if (status == Status.RESTARTING) return;
        List<String> actions = new ArrayList<>();
        for (Found mod : found()) {
            if (mod.file() != null) actions.add(ExitActions.move(mod.file(), disabledName(mod.file())));
        }
        if (actions.isEmpty()) {
            fail("не нашёл файлы модов — удалите их из папки mods вручную");
            return;
        }
        if (!ExitActions.schedule(actions, !HinexFeaturesClient.platform().isDevEnvironment())) {
            fail("не удалось запустить отключение");
            return;
        }
        HinexFeaturesClient.LOGGER.info("Запрещённые моды отключатся при выходе: {}", actions.size());
        status = Status.RESTARTING;
    }

    private static void fail(String message) {
        error = message;
        status = Status.FAILED;
    }

    private static Path disabledName(Path file) {
        Path target = file.resolveSibling(file.getFileName() + ".disabled");
        for (int i = 2; Files.exists(target) && i < 100; i++) target = file.resolveSibling(file.getFileName() + ".disabled" + i);
        return target;
    }
}
