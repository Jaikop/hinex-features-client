package me.JAKOP.hinexFeatures.mod.gate;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.config.RemoteConfigLoader;
import me.JAKOP.hinexFeatures.mod.update.BlockedMods;
import me.JAKOP.hinexFeatures.mod.update.Updater;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class Gate {

    public enum Stage {
        CHECKING,
        NO_LICENSE,
        LICENSE_ERROR,
        BLOCKED_MODS,
        UPDATE,
        TOS,
        TOS_UPDATED,
        DONE
    }

    public static final String UNKNOWN_VERSION = "unknown";
    private static final long CONFIG_WAIT_MS = 6000;
    private static final long CHECKING_GRACE_MS = 1500;

    private static long firstAskedAt;
    private static long menuReadyAt;

    private Gate() {
    }

    public static Stage stage() {
        switch (HinexFeaturesClient.license().status()) {
            case CHECKING -> {
                return Stage.CHECKING;
            }
            case UNLICENSED -> {
                return Stage.NO_LICENSE;
            }
            case ERROR -> {
                return Stage.LICENSE_ERROR;
            }
            default -> {
            }
        }

        if (!BlockedMods.found().isEmpty()) return Stage.BLOCKED_MODS;
        if (Updater.available() != null) return Stage.UPDATE;

        String accepted = HinexFeaturesClient.state().tosAccepted();
        if (accepted == null || accepted.equals(UNKNOWN_VERSION) && HinexFeaturesClient.config().tosUpdated() != null) {
            return Stage.TOS;
        }

        RemoteConfigLoader loader = HinexFeaturesClient.remoteConfig();
        if (firstAskedAt == 0) firstAskedAt = System.currentTimeMillis();
        if (!loader.loaded().isDone() && System.currentTimeMillis() - firstAskedAt < CONFIG_WAIT_MS) {
            return Stage.CHECKING;
        }

        String updated = HinexFeaturesClient.config().tosUpdated();
        if (updated != null && !updated.equals(accepted)) return Stage.TOS_UPDATED;
        return Stage.DONE;
    }

    public static String currentTosVersion() {
        String updated = HinexFeaturesClient.config().tosUpdated();
        return updated != null ? updated : UNKNOWN_VERSION;
    }

    public static void tick(Minecraft minecraft) {
        if (!minecraft.isGameLoadFinished() || minecraft.gui.overlay() != null || minecraft.level != null) return;
        if (menuReadyAt == 0) menuReadyAt = System.currentTimeMillis();

        Screen screen = minecraft.gui.screen();
        if (screen instanceof GateScreen) return;
        Stage stage = stage();
        if (stage == Stage.CHECKING && System.currentTimeMillis() - menuReadyAt < CHECKING_GRACE_MS) return;
        if (stage != Stage.DONE) {
            minecraft.gui.setScreen(new GateScreen(screen));
        }
    }
}
