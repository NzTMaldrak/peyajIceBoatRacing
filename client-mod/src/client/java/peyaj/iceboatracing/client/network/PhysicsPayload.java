package peyaj.iceboatracing.client.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PhysicsPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<PhysicsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("iceboatracing", "physics"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhysicsPayload> CODEC = StreamCodec.ofMember(
            (payload, buffer) -> buffer.writeBytes(payload.data),
            buffer -> {
                byte[] data = new byte[buffer.readableBytes()];
                buffer.readBytes(data);
                return new PhysicsPayload(data);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
