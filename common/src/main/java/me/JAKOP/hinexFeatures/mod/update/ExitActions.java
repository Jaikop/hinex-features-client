package me.JAKOP.hinexFeatures.mod.update;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ExitActions {

    private static final long STOP_DELAY_MS = 1200;

    private static volatile long stopAt = -1;
    private static volatile boolean relaunch;

    private ExitActions() {
    }

    static String delete(Path file) {
        return "delete\t" + file;
    }

    static String move(Path from, Path to) {
        return "move\t" + from + "\t" + to;
    }

    static boolean schedule(List<String> actions, boolean relaunchAllowed) {
        try {
            Path classpath = helperClasspath();
            if (classpath == null) return false;
            List<String> plan = new ArrayList<>();
            plan.add("wait\t" + ProcessHandle.current().pid());
            plan.addAll(actions);
            List<String> command = relaunchAllowed ? GameCommandLine.current() : null;
            if (command != null) {
                HinexFeaturesClient.LOGGER.info("Игра запустится снова ({} аргументов, {})", command.size(), command.getFirst());
                plan.add("cwd\t" + System.getProperty("user.dir"));
                command.forEach(arg -> plan.add("arg\t" + arg));
            }
            Path planFile = Files.createTempFile("hinex-plan", ".txt");
            Files.write(planFile, plan, StandardCharsets.UTF_8);
            new ProcessBuilder(java(), "-cp", classpath.toString(), UpdateHelper.class.getName(), planFile.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            relaunch = command != null;
            stopAt = Util.getMillis() + STOP_DELAY_MS;
            return true;
        } catch (Exception e) {
            HinexFeaturesClient.LOGGER.error("Не удалось запустить установщик", e);
            return false;
        }
    }

    public static boolean shouldStop() {
        return stopAt >= 0 && Util.getMillis() >= stopAt;
    }

    public static boolean willRelaunch() {
        return relaunch;
    }

    private static @Nullable Path helperClasspath() throws IOException, URISyntaxException {
        Path jar = HinexFeaturesClient.platform().modJar();
        if (jar == null) {
            var source = UpdateHelper.class.getProtectionDomain().getCodeSource();
            Path location = source == null ? null : Path.of(source.getLocation().toURI());
            if (location != null && Files.isDirectory(location)) return location;
            jar = location != null && Files.isRegularFile(location) ? location : null;
            if (jar == null) {
                HinexFeaturesClient.LOGGER.error("Не нашёл jar мода для установщика");
                return null;
            }
        }
        Path copy = Path.of(System.getProperty("java.io.tmpdir"), "hinex-updater.jar");
        try {
            Files.copy(jar, copy, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException busy) {
            copy = Files.createTempFile("hinex-updater", ".jar");
            Files.copy(jar, copy, StandardCopyOption.REPLACE_EXISTING);
        }
        return copy;
    }

    private static String java() {
        String command = ProcessHandle.current().info().command().orElse(null);
        Path java = command != null ? Path.of(command) : Path.of(System.getProperty("java.home"), "bin", "java");
        Path javaw = java.resolveSibling("javaw.exe");
        return java.getFileName().toString().equalsIgnoreCase("java.exe") && Files.isRegularFile(javaw) ? javaw.toString() : java.toString();
    }
}
