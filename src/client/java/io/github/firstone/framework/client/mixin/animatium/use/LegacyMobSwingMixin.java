package io.github.firstone.framework.client.mixin.animatium.use;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the arm swing of mobs with nothing in the swinging hand, like 1.7.10
 *
 * <p>In 1.7.10 a mob only swung when it held an item ({@code EntityAIAttackOnCollide}:
 * {@code if (getHeldItem() != null) swingItem()}) and never when breaking doors. 1.21.1 mobs swing on every melee
 * attack ({@code MeleeAttackGoal}, {@code BreakDoorGoal}), which makes an empty-handed zombie raise its arms. The
 * server sends that swing as a {@link ClientboundAnimatePacket}; it is ignored when the mob's hand is empty. Players'
 * swings are never dropped. Runs after the packet has been moved to the main thread.</p>
 */
@Mixin(ClientPacketListener.class)
public class LegacyMobSwingMixin {

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
    private void animatium$dropEmptyHandedMobSwing(ClientboundAnimatePacket packet, CallbackInfo ci) {
        int action = packet.getAction();
        if (action != SWING_MAIN_HAND && action != SWING_OFF_HAND || !AnimatiumFeature.getConfig().legacyMobSwing) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(packet.getId());
        if (entity instanceof LivingEntity mob && !(entity instanceof Player)) {
            InteractionHand hand = action == SWING_MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (mob.getItemInHand(hand).isEmpty()) {
                ci.cancel();
            }
        }
    }
}
