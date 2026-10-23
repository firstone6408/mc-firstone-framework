package io.github.firstone.framework.common.enchanting;

import io.github.firstone.framework.FirstOneFramework;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a client the server's enchanting rules, so the enchanting table screen shows what the server does
 *
 * <p>Sent by Legacy Mechanics when a player joins (only when one of the rules is on) and to everyone whenever they
 * change; received by Legacy HUD, which shows the Show Clues button and the real levels an enchantment takes. A
 * server without the mod sends nothing, so the client keeps the screen as in 1.21.1.</p>
 *
 * @param fullCost   true if enchanting takes the levels on the line
 * @param paidClues  true if the Show Clues button works
 * @param clueLapis  lapis lazuli one look at the clues takes
 * @param clueLevels levels one look at the clues takes
 */
public record EnchantingRulesPayload(boolean fullCost, boolean paidClues, int clueLapis, int clueLevels)
    implements CustomPacketPayload {

    /** Id of this payload */
    public static final Type<EnchantingRulesPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "enchanting_rules"));

    /** Reads and writes this payload: two booleans and two small numbers */
    public static final StreamCodec<FriendlyByteBuf, EnchantingRulesPayload> CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeBoolean(payload.fullCost());
            buffer.writeBoolean(payload.paidClues());
            buffer.writeVarInt(payload.clueLapis());
            buffer.writeVarInt(payload.clueLevels());
        },
        buffer -> new EnchantingRulesPayload(buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(),
            buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
