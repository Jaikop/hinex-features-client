package me.JAKOP.hinexFeatures.mod.admin;

import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.ServerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

public final class AdminKeys {

    private AdminKeys() {
    }

    public static boolean allowed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        if (minecraft.hasSingleplayerServer()) return true;
        ServerState state = ClientNetwork.state();
        return state != null && state.admin();
    }

    public static boolean onPress(KeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() != null || !allowed()) return false;
        HotKey panel = MacroStore.panelKey();
        if (panel != null && panel.matches(event)) {
            minecraft.gui.setScreen(new MacroScreen());
            return true;
        }
        HotKey quick = MacroStore.quickKey();
        if (quick != null && quick.matches(event) && !MacroStore.quickMacros().isEmpty()) {
            minecraft.gui.setScreen(new QuickMenuScreen(quick));
            return true;
        }
        for (Macro macro : MacroStore.macros()) {
            if (macro.key != null && macro.key.matches(event)) {
                if (MacroRunner.running(macro)) MacroRunner.stop(macro);
                else MacroRunner.start(macro);
                return true;
            }
        }
        return false;
    }
}
