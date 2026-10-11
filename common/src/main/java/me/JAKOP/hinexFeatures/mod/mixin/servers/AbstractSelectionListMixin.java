package me.JAKOP.hinexFeatures.mod.mixin.servers;

import me.JAKOP.hinexFeatures.mod.servers.PinnedServers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {

    @Inject(method = "extractSelection", at = @At("HEAD"), cancellable = true)
    private void hinex$selectionWithoutSeparator(GuiGraphicsExtractor g, @Coerce Object entry, int outlineColor, CallbackInfo ci) {
        if (!(entry instanceof ServerSelectionList.OnlineServerEntry server)) return;
        if (PinnedServers.shownSeparator(server.getServerData()) == null) return;

        int x0 = server.getX();
        int y0 = server.getY();
        int x1 = x0 + server.getWidth();
        int y1 = y0 + server.getHeight() - PinnedServers.SEPARATOR_HEIGHT;
        g.fill(x0, y0, x1, y1, outlineColor);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000);
        ci.cancel();
    }
}
