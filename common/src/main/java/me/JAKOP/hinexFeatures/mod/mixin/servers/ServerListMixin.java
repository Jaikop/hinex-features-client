package me.JAKOP.hinexFeatures.mod.mixin.servers;

import me.JAKOP.hinexFeatures.mod.servers.PinnedServers;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerList.class)
public abstract class ServerListMixin {

    @Shadow
    @Final
    private List<ServerData> serverList;

    @Shadow
    @Final
    private List<ServerData> hiddenServerList;

    @Shadow
    public abstract ServerData get(int index);

    @Inject(method = "load", at = @At("RETURN"))
    private void hinex$pinServers(CallbackInfo ci) {
        PinnedServers.apply(serverList, hiddenServerList);
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void hinex$keepPinned(ServerData server, CallbackInfo ci) {
        if (PinnedServers.isPinned(server)) ci.cancel();
    }

    @Inject(method = "swap", at = @At("HEAD"), cancellable = true)
    private void hinex$noSwapPinned(int a, int b, CallbackInfo ci) {
        if (PinnedServers.isPinned(get(a)) || PinnedServers.isPinned(get(b))) ci.cancel();
    }

    @Inject(method = "replace", at = @At("HEAD"), cancellable = true)
    private void hinex$noReplacePinned(int index, ServerData data, CallbackInfo ci) {
        if (PinnedServers.isPinned(get(index))) ci.cancel();
    }
}
