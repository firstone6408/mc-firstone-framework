package io.github.firstone.framework.common.boat;

import io.github.firstone.framework.FirstOneFramework;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A push the server found for a boat a player drives, sent to that player's client, which moves the boat
 *
 * <p>1.7.10 pushed boats whoever sat in them, and sent the new motion to the driver. In 1.21.1 the driver's client
 * moves its boat and the game's motion packet would replace its motion, so the push is sent on its own and added to
 * the boat's motion by the client. Registered by Legacy Mechanics, received by Legacy Vehicles; a client without the
 * mod is never sent one.</p>
 *
 * @param entityId id of the boat
 * @param x        push along x, in blocks per tick
 * @param z        push along z, in blocks per tick
 */
public record BoatPushPayload(int entityId, double x, double z) implements CustomPacketPayload {

    /** Id of this payload */
    public static final Type<BoatPushPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "boat_push"));

    /** Reads and writes this payload: the boat id, then the push */
    public static final StreamCodec<FriendlyByteBuf, BoatPushPayload> CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeVarInt(payload.entityId());
            buffer.writeDouble(payload.x());
            buffer.writeDouble(payload.z());
        },
        buffer -> new BoatPushPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
