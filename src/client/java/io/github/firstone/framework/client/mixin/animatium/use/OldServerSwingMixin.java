package io.github.firstone.framework.client.mixin.animatium.use;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ignores arm swings that the server sends back to the local player, like 1.7.10
 *
 * <p>1.7.10 {@code EntityLivingBase.swingItem} sent the swing animation only to the <em>other</em> players
 * tracking the entity ({@code sendToAllTrackingEntity}), never back to the player who swung. 1.21.1 calls
 * {@code player.swing(hand, true)} after entity interactions and item uses, which uses
 * {@code broadcastAndSend} and also makes the player's own arm swing — for example when feeding an animal,
 * even though the client did not swing.</p>
 *
 * <p>Every swing the player really makes (attacking, mining, using a block) is already started by the
 * client itself, so ignoring the server's echo removes only these extra swings. Other players' swings are
 * not affected. Runs after the packet has been moved to the main thread.</p>
 */
@Mixin(ClientPacketListener.class)
public class OldServerSwingMixin {

    /** {@link ClientboundAnimatePacket} action: swing main hand */
    @Unique
    private static final int SWING_MAIN_HAND = 0;

    /** {@link ClientboundAnimatePacket} action: swing off hand */
    @Unique
    private static final int SWING_OFF_HAND = 3;

    @Inject(
        method = "handleAnimate",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = At.Shift.AFTER),
        cancellable = true
    )
    private void animatium$ignoreOwnSwingEcho(ClientboundAnimatePacket packet, CallbackInfo ci) {
        int action = packet.getAction();
        if (action != SWING_MAIN_HAND && action != SWING_OFF_HAND || !AnimatiumFeature.getConfig().oldItemUse) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && packet.getId() == minecraft.player.getId()) {
            ci.cancel();
        }
    }
}
