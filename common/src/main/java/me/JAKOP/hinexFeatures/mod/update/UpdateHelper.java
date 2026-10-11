package me.JAKOP.hinexFeatures.mod.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class UpdateHelper {

    private static Path log;

    private UpdateHelper() {
    }

    public static void main(String[] args) {
        log = Path.of(System.getProperty("java.io.tmpdir"), "hinex-update.log");
        try {
            run(Path.of(args[0]));
        } catch (Exception e) {
            log("ошибка: " + e);
        }
    }

    private static void run(Path planFile) throws Exception {
        List<String> plan = Files.readAllLines(planFile, StandardCharsets.UTF_8);
        Files.deleteIfExists(planFile);
        String cwd = null;
        List<String> command = new ArrayList<>();
        for (String line : plan) {
            String[] parts = line.split("\t", -1);
            switch (parts[0]) {
                case "wait" -> {
                    if (!waitFor(Long.parseLong(parts[1]))) return;
                }
                case "delete" -> {
                    if (!retry(() -> Files.deleteIfExists(Path.of(parts[1])))) {
                        log("не удалось удалить " + parts[1]);
                        return;
                    }
                }
                case "move" -> {
                    if (!retry(() -> Files.move(Path.of(parts[1]), Path.of(parts[2]), StandardCopyOption.REPLACE_EXISTING))) {
                        log("не удалось переименовать " + parts[1]);
                        return;
                    }
                    log(Path.of(parts[1]).getFileName() + " → " + Path.of(parts[2]).getFileName());
                }
                case "cwd" -> cwd = parts[1];
                case "arg" -> command.add(parts[1]);
                default -> log("непонятная строка плана: " + line);
            }
        }
        if (!command.isEmpty()) {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            if (cwd != null) builder.directory(Path.of(cwd).toFile());
            builder.start();
            log("игра запущена снова");
        }
    }

    private static boolean waitFor(long pid) {
        log("жду закрытия игры (pid " + pid + ")");
        var game = ProcessHandle.of(pid);
        if (game.isEmpty()) return true;
        try {
            game.get().onExit().get(10, TimeUnit.MINUTES);
            return true;
        } catch (Exception e) {
            log("игра не закрылась за 10 минут — выхожу");
            return false;
        }
    }

    private interface FileAction {
        void run() throws IOException;
    }

    private static boolean retry(FileAction action) throws InterruptedException {
        for (int attempt = 0; attempt < 120; attempt++) {
            try {
                action.run();
                return true;
            } catch (IOException e) {
                Thread.sleep(250);
            }
        }
        return false;
    }

    private static void log(String message) {
        try {
            Files.writeString(log, LocalDateTime.now() + " " + message + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
