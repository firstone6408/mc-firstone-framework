package io.github.firstone.framework.features.legacymechanics.sword;

import io.github.firstone.framework.FirstOneFramework;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a client whether the server lets swords block, so both sides agree on what a right click with a sword does
 *
 * <p>Sent by Legacy Mechanics when a player joins and whenever the setting changes; received by the client part of
 * Legacy Mechanics ({@code SwordBlockingSync}), which keeps the answer in {@link SwordBlockingSupport}. A server
 * without the mod sends nothing, so swords never block there and the client does not try to.</p>
 *
 * @param allowed true if swords block on this server
 */
public record SwordBlockingPayload(boolean allowed) implements CustomPacketPayload {

    /** Id of this payload */
    public static final Type<SwordBlockingPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "sword_blocking"));

    /** Reads and writes this payload: one boolean */
    public static final StreamCodec<FriendlyByteBuf, SwordBlockingPayload> CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeBoolean(payload.allowed()),
        buffer -> new SwordBlockingPayload(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
