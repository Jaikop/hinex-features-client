package me.JAKOP.hinexFeatures.mod.platform;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public interface Platform {

    record ModInfo(String id, String name, String version) {
    }

    String loaderName();

    String loaderVersion();

    Path gameDir();

    String modVersion();

    boolean isDevEnvironment();

    List<ModInfo> mods();

    @Nullable Path modJar();

    @Nullable Path modFile(String modId);

    boolean sendToServer(byte[] data);
}
