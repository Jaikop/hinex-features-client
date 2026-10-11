package me.JAKOP.hinexFeatures.mod.fabric;

import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.net.HinexPayload;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class FabricPlatform implements Platform {

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public String loaderVersion() {
        return version("fabricloader");
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public String modVersion() {
        return version(HinexConstants.MOD_ID);
    }

    @Override
    public boolean isDevEnvironment() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isDevelopmentEnvironment() && loader.getModContainer(HinexConstants.MOD_ID)
                .map(mod -> mod.getOrigin().getKind() == ModOrigin.Kind.PATH
                        && mod.getOrigin().getPaths().stream().allMatch(Files::isDirectory))
                .orElse(false);
    }

    @Override
    public List<ModInfo> mods() {
        return FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> mod.getContainingMod().isEmpty())
                .filter(mod -> !"builtin".equals(mod.getMetadata().getType()))
                .map(mod -> new ModInfo(mod.getMetadata().getId(), mod.getMetadata().getName(),
                        mod.getMetadata().getVersion().getFriendlyString()))
                .toList();
    }

    @Override
    public @Nullable Path modJar() {
        return FabricLoader.getInstance().getModContainer(HinexConstants.MOD_ID)
                .filter(mod -> mod.getOrigin().getKind() == ModOrigin.Kind.PATH)
                .flatMap(mod -> mod.getOrigin().getPaths().stream().filter(Files::isRegularFile).findFirst())
                .orElse(null);
    }

    @Override
    public @Nullable Path modFile(String modId) {
        var mod = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        for (int depth = 0; mod != null && mod.getOrigin().getKind() == ModOrigin.Kind.NESTED && depth < 8; depth++) {
            mod = mod.getContainingMod().orElse(null);
        }
        if (mod == null || mod.getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
        return mod.getOrigin().getPaths().stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    @Override
    public boolean sendToServer(byte[] data) {
        ClientPlayNetworking.send(new HinexPayload(data));
        return true;
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }
}
