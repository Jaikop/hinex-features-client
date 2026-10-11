package me.JAKOP.hinexFeatures.mod;

import me.JAKOP.hinexFeatures.mod.admin.MacroRunner;
import me.JAKOP.hinexFeatures.mod.config.LocalState;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfig;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfigLoader;
import me.JAKOP.hinexFeatures.mod.deathgame.DeathGameClient;
import me.JAKOP.hinexFeatures.mod.discord.DiscordPresence;
import me.JAKOP.hinexFeatures.mod.dialog.DialogClient;
import me.JAKOP.hinexFeatures.mod.dossier.HardwareInfo;
import me.JAKOP.hinexFeatures.mod.gate.Gate;
import me.JAKOP.hinexFeatures.mod.license.LicenseChecker;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.packs.PackManager;
import me.JAKOP.hinexFeatures.mod.platform.Platform;
import me.JAKOP.hinexFeatures.mod.typing.TypingIndicators;
import me.JAKOP.hinexFeatures.mod.update.Updater;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class HinexFeaturesClient {

    public static final String MOD_ID = HinexConstants.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger("HinexFeatures");

    private static final List<Consumer<Minecraft>> TICK_LISTENERS = new CopyOnWriteArrayList<>();

    private static Platform platform;
    private static LocalState state;
    private static RemoteConfigLoader remoteConfig;
    private static PackManager packs;
    private static LicenseChecker license;

    private HinexFeaturesClient() {
    }

    public static void init(Platform platform) {
        HinexFeaturesClient.platform = platform;
        Path root = platform.gameDir().resolve("hinex");

        state = new LocalState(root.resolve("state.json"));
        state.load();
        Updater.init();

        remoteConfig = new RemoteConfigLoader(root.resolve("remote-config.json"));
        packs = new PackManager(root.resolve("packs"));
        packs.loadCached(remoteConfig.current());
        remoteConfig.loaded().thenAcceptAsync(packs::sync);
        remoteConfig.start();

        license = new LicenseChecker(state);
        license.start();
        HardwareInfo.start();

        LOGGER.info("HinexFeatures {} ({} {})", platform.modVersion(), platform.loaderName(), platform.loaderVersion());

        if (Boolean.getBoolean("hinex.selftest") && platform.isDevEnvironment()) {
            try {
                Class.forName("me.JAKOP.hinexFeatures.mod.selftest.SelfTest").getMethod("start").invoke(null);
            } catch (ReflectiveOperationException e) {
                LOGGER.error("Не удалось запустить автотест", e);
            }
        }
    }

    public static void addTickListener(Consumer<Minecraft> listener) {
        TICK_LISTENERS.add(listener);
    }

    public static void tick(Minecraft minecraft) {
        if (platform == null) return;
        ClientNetwork.tick(minecraft);
        TypingIndicators.tick(minecraft);
        DeathGameClient.tick(minecraft);
        DialogClient.tick(minecraft);
        MacroRunner.tick(minecraft);
        Gate.tick(minecraft);
        DiscordPresence.tick(minecraft);
        if (minecraft.isGameLoadFinished() && packs.takeReloadRequest()) {
            minecraft.reloadResourcePacks();
        }
        for (Consumer<Minecraft> listener : TICK_LISTENERS) listener.accept(minecraft);
    }

    public static Platform platform() {
        return platform;
    }

    public static String version() {
        return platform.modVersion();
    }

    public static LocalState state() {
        return state;
    }

    public static RemoteConfig config() {
        return remoteConfig.current();
    }

    public static RemoteConfigLoader remoteConfig() {
        return remoteConfig;
    }

    public static PackManager packs() {
        return packs;
    }

    public static LicenseChecker license() {
        return license;
    }
}
