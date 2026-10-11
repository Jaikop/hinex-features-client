package me.JAKOP.hinexFeatures.mod.forge;

import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.network.ForgePayload;
import net.minecraftforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ForgePlatform implements Platform {

    private static final Identifier REGISTER = Identifier.withDefaultNamespace("register");

    private WeakReference<Connection> registeredOn = new WeakReference<>(null);

    @Override
    public String loaderName() {
        return "forge";
    }

    @Override
    public String loaderVersion() {
        return version("forge");
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
        if (FMLEnvironment.production) return false;
        IModFileInfo file = ModList.getModFileById(HinexConstants.MOD_ID);
        return file != null && Files.isDirectory(file.getFile().getFilePath());
    }

    @Override
    public List<ModInfo> mods() {
        return ModList.getMods().stream()
                .map(mod -> new ModInfo(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString()))
                .toList();
    }

    @Override
    public @Nullable Path modJar() {
        return modFile(HinexConstants.MOD_ID);
    }

    @Override
    public @Nullable Path modFile(String modId) {
        IModFileInfo file = ModList.getModFileById(modId);
        Path path = file == null ? null : file.getFile().getFilePath();
        return path != null && Files.isRegularFile(path) ? path : null;
    }

    @Override
    public boolean sendToServer(byte[] data) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !HinexFeaturesForge.channel().isRemotePresent(connection.getConnection())) return false;
        Connection network = connection.getConnection();
        if (registeredOn.get() != network) {
            registeredOn = new WeakReference<>(network);
            byte[] channel = HinexFeaturesForge.channel().getName().toString().getBytes(StandardCharsets.UTF_8);
            network.send(new ServerboundCustomPayloadPacket(ForgePayload.create(REGISTER, buf -> buf.writeBytes(channel))));
        }
        HinexFeaturesForge.channel().send(HinexFeaturesForge.wrap(data), PacketDistributor.SERVER.noArg());
        return true;
    }

    private static String version(String modId) {
        return ModList.getModContainerById(modId)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElse("?");
    }
}
