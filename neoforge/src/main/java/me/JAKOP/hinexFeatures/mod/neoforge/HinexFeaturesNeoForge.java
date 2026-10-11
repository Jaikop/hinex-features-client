package me.JAKOP.hinexFeatures.mod.neoforge;

import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.HinexPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(value = HinexConstants.MOD_ID, dist = Dist.CLIENT)
public final class HinexFeaturesNeoForge {

    public HinexFeaturesNeoForge(IEventBus modBus) {
        modBus.addListener(HinexFeaturesNeoForge::registerPayloads);
        HinexFeaturesClient.init(new NeoForgePlatform());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playBidirectional(HinexPayload.TYPE, HinexPayload.CODEC,
                (payload, context) -> {
                },
                (payload, context) -> ClientNetwork.receive(payload.data()));
    }
}
