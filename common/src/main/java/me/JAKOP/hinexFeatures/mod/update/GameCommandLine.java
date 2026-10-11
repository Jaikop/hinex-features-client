package me.JAKOP.hinexFeatures.mod.update;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Shell32Util;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GameCommandLine {

    private static final List<String> STDIN_LAUNCHERS = List.of("org.prismlauncher.EntryPoint", "org.multimc.EntryPoint");

    private interface Kernel32 extends Library {
        Pointer GetCommandLineW();
    }

    private GameCommandLine() {
    }

    static @Nullable List<String> current() {
        try {
            List<String> command = read();
            if (command == null || command.size() < 2) return null;
            for (String arg : command) {
                if (STDIN_LAUNCHERS.contains(arg)) return null;
                if (arg.startsWith("@") && !Files.isRegularFile(Path.of(arg.substring(1)))) return null;
            }
            return command;
        } catch (Throwable e) {
            HinexFeaturesClient.LOGGER.warn("Обновление: не удалось узнать команду запуска игры", e);
            return null;
        }
    }

    private static @Nullable List<String> read() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            Kernel32 kernel = Native.load("kernel32", Kernel32.class);
            String line = kernel.GetCommandLineW().getWideString(0);
            command.addAll(List.of(Shell32Util.CommandLineToArgv(line)));
            if (!command.isEmpty() && info.command().isPresent()) command.set(0, info.command().get());
            return command;
        }
        if (info.command().isEmpty() || info.arguments().isEmpty()) return null;
        command.add(info.command().get());
        command.addAll(List.of(info.arguments().get()));
        return command;
    }
}
