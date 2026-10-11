package me.JAKOP.hinexFeatures.mod.mixin.servers;

import me.JAKOP.hinexFeatures.mod.servers.PinnedServers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerSelectionList.class)
public abstract class ServerSelectionListMixin extends ObjectSelectionList<ServerSelectionList.Entry> {

    private ServerSelectionListMixin(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @Inject(method = "refreshEntries", at = @At("TAIL"))
    private void hinex$separators(CallbackInfo ci) {
        List<ServerSelectionList.Entry> children = children();
        for (int i = 0; i < children.size(); i++) {
            if (!(children.get(i) instanceof ServerSelectionList.OnlineServerEntry entry)) continue;
            ServerData data = entry.getServerData();
            boolean userServersBelow = i + 1 < children.size()
                    && children.get(i + 1) instanceof ServerSelectionList.OnlineServerEntry next
                    && !PinnedServers.isPinned(next.getServerData());

            String separator = PinnedServers.separatorBelow(data, userServersBelow);
            PinnedServers.setShownSeparator(data, separator);
            entry.setHeight(defaultEntryHeight + (separator != null ? PinnedServers.SEPARATOR_HEIGHT : 0));
        }
        setScrollAmount(scrollAmount());
    }
}
