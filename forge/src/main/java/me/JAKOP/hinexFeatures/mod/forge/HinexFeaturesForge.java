package me.JAKOP.hinexFeatures.mod.forge;

import io.netty.buffer.Unpooled;
import me.JAKOP.hinexFeatures.mod.HinexConstants;
import me.JAKOP.hinexFeatures.mod.HinexFeaturesClient;
import me.JAKOP.hinexFeatures.mod.net.ClientNetwork;
import me.JAKOP.hinexFeatures.mod.net.HinexPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;

@Mod(HinexConstants.MOD_ID)
public final class HinexFeaturesForge {

    private static EventNetworkChannel channel;

    public HinexFeaturesForge(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            HinexFeaturesClient.LOGGER.warn("HinexFeatures — клиентский мод, на сервере он не нужен");
            return;
        }

        channel = ChannelBuilder.named(HinexPayload.TYPE.id())
                .networkProtocolVersion(1)
                .optional()
                .eventNetworkChannel();
        channel.addListener(HinexFeaturesForge::receive);

        HinexFeaturesClient.init(new ForgePlatform());
    }

    private static void receive(CustomPayloadEvent event) {
        FriendlyByteBuf payload = event.getPayload();        if (payload == null) return;
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);
        event.getSource().setPacketHandled(true);
        ClientNetwork.receive(data);
    }

    static EventNetworkChannel channel() {
        return channel;
    }

    static FriendlyByteBuf wrap(byte[] data) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
    }
}
