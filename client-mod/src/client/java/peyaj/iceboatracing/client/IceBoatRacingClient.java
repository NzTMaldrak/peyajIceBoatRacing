package peyaj.iceboatracing.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import peyaj.iceboatracing.client.network.PhysicsPayload;

import java.nio.ByteBuffer;

public final class IceBoatRacingClient implements ClientModInitializer {

    public static final int PROTOCOL_VERSION = 1;

    private static final byte DISABLE = 0;
    private static final byte ENABLE = 1;
    private static final byte REQUEST_VERSION = 2;
    private static final byte VERSION = 3;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(PhysicsPayload.TYPE, PhysicsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhysicsPayload.TYPE, PhysicsPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(PhysicsPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handle(payload.data())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            RacePhysics.setEnabled(false);
            sendVersion();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RacePhysics.setEnabled(false));
    }

    private static void handle(byte[] data) {
        if (data.length == 0) {
            return;
        }

        switch (data[0]) {
            case DISABLE -> RacePhysics.setEnabled(false);
            case ENABLE -> RacePhysics.setEnabled(true);
            case REQUEST_VERSION -> sendVersion();
            default -> {
            }
        }
    }

    private static void sendVersion() {
        if (!ClientPlayNetworking.canSend(PhysicsPayload.TYPE)) {
            return;
        }

        byte[] data = ByteBuffer.allocate(5)
                .put(VERSION)
                .putInt(PROTOCOL_VERSION)
                .array();
        ClientPlayNetworking.send(new PhysicsPayload(data));
    }
}
