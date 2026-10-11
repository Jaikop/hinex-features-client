package me.JAKOP.hinexFeatures.mod.mixin.servers;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import me.JAKOP.hinexFeatures.mod.servers.PinnedServers;
import me.JAKOP.hinexFeatures.mod.servers.ServerEntryStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class OnlineServerEntryMixin {

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    @Final
    private JoinMultiplayerScreen screen;

    @Unique
    private ServerSelectionList.OnlineServerEntry hinex$self() {
        return (ServerSelectionList.OnlineServerEntry) (Object) this;
    }

    @Unique
    private int hinex$rowHeight() {
        String separator = PinnedServers.shownSeparator(serverData);
        return hinex$self().getHeight() - (separator != null ? PinnedServers.SEPARATOR_HEIGHT : 0);
    }

    @Inject(method = "extractContent", at = @At("HEAD"))
    private void hinex$background(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
        PinnedServers.Pin pin = PinnedServers.pin(serverData);
        if (pin == null) return;
        ServerSelectionList.OnlineServerEntry self = hinex$self();
        ServerEntryStyle.background(g, self.getX() + 1, self.getY() + 1, self.getWidth() - 2, hinex$rowHeight() - 2, pin);
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void hinex$overlay(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a, CallbackInfo ci) {
        ServerSelectionList.OnlineServerEntry self = hinex$self();
        PinnedServers.Pin pin = PinnedServers.pin(serverData);
        if (pin != null && pin.main()) {
            Font font = Minecraft.getInstance().font;
            int badgeX = self.getContentX() + 32 + 3 + font.width(serverData.name) + 5;
            Component status = serverData.state() == ServerData.State.INCOMPATIBLE ? serverData.version : serverData.status;
            int statusX = self.getContentRight() - 10 - 5 - font.width(status) - 5;
            if (badgeX + ServerEntryStyle.badgeWidth() + 4 <= statusX) {
                ServerEntryStyle.badge(g, badgeX, self.getContentY() - 1);
            }
        }

        String separator = PinnedServers.shownSeparator(serverData);
        if (separator != null) {
            ServerEntryStyle.separator(g, self.getX(), self.getY() + hinex$rowHeight(), self.getWidth(), separator);
        }
    }

    @WrapWithCondition(method = "extractContent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private boolean hinex$hideMoveArrows(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
        String path = sprite.getPath();
        if (!path.startsWith("server_list/move_")) return true;
        if (PinnedServers.isPinned(serverData)) return false;
        return !path.startsWith("server_list/move_up") || !PinnedServers.isPinned(hinex$neighbour(-1));
    }

    @Inject(method = "swap", at = @At("HEAD"), cancellable = true)
    private void hinex$noSwapPinned(int currentIndex, int newIndex, CallbackInfo ci) {
        ServerList servers = screen.getServers();
        if (currentIndex >= servers.size() || newIndex >= servers.size()
                || PinnedServers.isPinned(servers.get(currentIndex)) || PinnedServers.isPinned(servers.get(newIndex))) {
            ci.cancel();
        }
    }

    @Unique
    private ServerData hinex$neighbour(int offset) {
        ServerList servers = screen.getServers();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i) == serverData) {
                int index = i + offset;
                return index >= 0 && index < servers.size() ? servers.get(index) : null;
            }
        }
        return null;
    }
}
