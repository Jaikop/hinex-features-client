package me.JAKOP.hinexFeatures.mod.neoforge;

import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.net.HinexPayload;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class NeoForgePlatform implements Platform {

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public String loaderVersion() {
        return version("neoforge");
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public String modVersion() {
        return version(HinexConstants.MOD_ID);
    }

    @Override
    public boolean isDevEnvironment() {
        if (FMLEnvironment.isProduction()) return false;
        IModFileInfo file = ModList.get().getModFileById(HinexConstants.MOD_ID);
        return file != null && Files.isDirectory(file.getFile().getFilePath());
    }

    @Override
    public List<ModInfo> mods() {
        return ModList.get().getMods().stream()
                .map(mod -> new ModInfo(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString()))
                .toList();
    }

    @Override
    public @Nullable Path modJar() {
        return modFile(HinexConstants.MOD_ID);
    }

    @Override
    public @Nullable Path modFile(String modId) {
        IModFileInfo file = ModList.get().getModFileById(modId);
        Path path = file == null ? null : file.getFile().getFilePath();
        return path != null && Files.isRegularFile(path) ? path : null;
    }

    @Override
    public boolean sendToServer(byte[] data) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !NetworkRegistry.hasChannel(connection, HinexPayload.TYPE.id())) return false;
        ClientPacketDistributor.sendToServer(new HinexPayload(data));
        return true;
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
