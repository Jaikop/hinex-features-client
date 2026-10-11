package me.JAKOP.hinexFeatures.mod.dossier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.license.LicenseChecker;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.packs.repository.Pack;

import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Locale;

public final class ClientDossier {

    private ClientDossier() {
    }

    public static JsonObject collect() {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject client = new JsonObject();

        client.addProperty("modVersion", HinexFeaturesClient.version());
        client.addProperty("installId", HinexFeaturesClient.state().installId());
        client.addProperty("hwid", HardwareInfo.hwid());

        JsonObject account = new JsonObject();
        account.addProperty("name", minecraft.getUser().getName());
        account.addProperty("uuid", minecraft.getUser().getProfileId().toString());
        account.addProperty("type", minecraft.getUser().getXuid().isPresent() ? "msa" : "unknown");
        LicenseChecker.Status license = HinexFeaturesClient.license().status();
        account.addProperty("license", license.name().toLowerCase(Locale.ROOT));
        client.add("account", account);

        JsonObject tos = new JsonObject();
        tos.addProperty("accepted", HinexFeaturesClient.state().tosAccepted());
        client.add("tos", tos);

        client.add("game", game(minecraft));
        client.add("options", options(minecraft.options));
        client.add("system", system(minecraft));
        client.add("mods", mods());
        client.add("resourcePacks", resourcePacks(minecraft));
        return client;
    }

    private static JsonObject game(Minecraft minecraft) {
        JsonObject game = new JsonObject();
        game.addProperty("version", SharedConstants.getCurrentVersion().name());
        Platform platform = HinexFeaturesClient.platform();
        game.addProperty("loader", platform.loaderName() + " " + platform.loaderVersion());
        game.addProperty("launcher", Minecraft.getLauncherBrand());
        game.addProperty("launchedVersion", minecraft.getLaunchedVersion());
        game.addProperty("language", minecraft.options.languageCode);
        game.addProperty("uptimeMinutes", ManagementFactory.getRuntimeMXBean().getUptime() / 60_000L);
        ServerData server = minecraft.getCurrentServer();
        if (server != null) game.addProperty("serverAddress", server.ip);
        return game;
    }

    private static JsonObject options(Options options) {
        JsonObject json = new JsonObject();
        json.addProperty("renderDistance", options.renderDistance().get());
        json.addProperty("simulationDistance", options.simulationDistance().get());
        json.addProperty("maxFps", options.framerateLimit().get());
        json.addProperty("vsync", options.enableVsync().get());
        json.addProperty("fullscreen", options.fullscreen().get());
        json.addProperty("guiScale", options.guiScale().get());
        json.addProperty("fov", options.fov().get());
        json.addProperty("graphicsPreset", String.valueOf(options.graphicsPreset().get()));
        json.addProperty("graphicsApi", String.valueOf(options.preferredGraphicsBackend().get()));
        return json;
    }

    private static JsonObject system(Minecraft minecraft) {
        JsonObject hardware = HardwareInfo.now();
        JsonObject system = new JsonObject();

        JsonObject os = new JsonObject();
        os.addProperty("name", System.getProperty("os.name"));
        os.addProperty("version", System.getProperty("os.version"));
        os.addProperty("arch", System.getProperty("os.arch"));
        if (hardware.has("osDetails")) os.add("details", hardware.get("osDetails"));
        system.add("os", os);

        JsonObject java = new JsonObject();
        java.addProperty("version", System.getProperty("java.version"));
        java.addProperty("vendor", System.getProperty("java.vendor"));
        java.addProperty("vm", System.getProperty("java.vm.name"));
        system.add("java", java);

        JsonObject cpu = hardware.has("cpu") ? hardware.getAsJsonObject("cpu") : new JsonObject();
        if (!cpu.has("threads")) cpu.addProperty("threads", Runtime.getRuntime().availableProcessors());
        system.add("cpu", cpu);

        JsonObject memory = new JsonObject();
        if (hardware.has("totalMemoryMb")) memory.add("totalMb", hardware.get("totalMemoryMb"));
        memory.addProperty("maxHeapMb", Runtime.getRuntime().maxMemory() / 1_048_576L);
        system.add("memory", memory);

        JsonObject gpu = new JsonObject();
        try {
            var device = RenderSystem.getDevice().getDeviceInfo();
            gpu.addProperty("renderer", device.name());
            gpu.addProperty("vendor", device.vendorName());
            gpu.addProperty("driver", device.driverInfo());
            gpu.addProperty("backend", device.backendName());
            gpu.addProperty("type", String.valueOf(device.type()));
        } catch (Exception e) {
            gpu.addProperty("renderer", "?");
        }
        if (hardware.has("gpuCards")) gpu.add("cards", hardware.get("gpuCards"));
        system.add("gpu", gpu);

        JsonObject display = new JsonObject();
        display.addProperty("width", minecraft.getWindow().getWidth());
        display.addProperty("height", minecraft.getWindow().getHeight());
        system.add("display", display);

        if (hardware.has("computer")) system.add("computer", hardware.get("computer"));
        system.addProperty("locale", Locale.getDefault().toLanguageTag());
        system.addProperty("timezone", ZoneId.systemDefault().getId());
        return system;
    }

    private static JsonArray mods() {
        JsonArray mods = new JsonArray();
        HinexFeaturesClient.platform().mods().stream()
                .sorted(Comparator.comparing(Platform.ModInfo::id))
                .map(ClientDossier::mod)
                .forEach(mods::add);
        return mods;
    }

    private static JsonElement mod(Platform.ModInfo info) {
        JsonObject mod = new JsonObject();
        mod.addProperty("id", info.id());
        mod.addProperty("name", info.name());
        mod.addProperty("version", info.version());
        return mod;
    }

    private static JsonArray resourcePacks(Minecraft minecraft) {
        JsonArray packs = new JsonArray();
        for (Pack pack : minecraft.getResourcePackRepository().getSelectedPacks()) {
            packs.add(pack.getId());
        }
        return packs;
    }
}
