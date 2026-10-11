package me.JAKOP.hinexFeatures.mod.fabric;

import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.HinexPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class HinexFeaturesFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.serverboundPlay().register(HinexPayload.TYPE, HinexPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().registerLarge(HinexPayload.TYPE, HinexPayload.CODEC, ClientNetwork.MAX_STATE_SIZE);
        ClientPlayNetworking.registerGlobalReceiver(HinexPayload.TYPE, (payload, context) -> ClientNetwork.receive(payload.data()));

        HinexFeaturesClient.init(new FabricPlatform());
    }
}
