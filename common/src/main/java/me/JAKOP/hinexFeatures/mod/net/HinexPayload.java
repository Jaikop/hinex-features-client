package me.JAKOP.hinexFeatures.mod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HinexPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<HinexPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("hinex", "features"));

    public static final StreamCodec<FriendlyByteBuf, HinexPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBytes(payload.data),
            buf -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new HinexPayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
