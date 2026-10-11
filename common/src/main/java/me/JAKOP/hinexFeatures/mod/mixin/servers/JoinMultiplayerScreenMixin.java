package me.JAKOP.hinexFeatures.mod.mixin.servers;

import me.JAKOP.hinexFeatures.mod.servers.PinnedServers;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {

    @Shadow
    private Button editButton;

    @Shadow
    private Button deleteButton;

    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Inject(method = "onSelectedChange", at = @At("TAIL"))
    private void hinex$lockPinned(CallbackInfo ci) {
        boolean pinned = serverSelectionList.getSelected() instanceof ServerSelectionList.OnlineServerEntry entry
                && PinnedServers.isPinned(entry.getServerData());
        Tooltip tooltip = pinned ? Tooltip.create(Component.literal("Сервер Hinex нельзя изменить или удалить")) : null;
        if (pinned) {
            editButton.active = false;
            deleteButton.active = false;
        }
        editButton.setTooltip(tooltip);
        deleteButton.setTooltip(tooltip);
    }
}
