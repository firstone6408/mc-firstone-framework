package io.github.firstone.framework.client.mixin.animatium.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.LegacyEntitySync;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the 1.7.10 entity sync rules ({@link LegacyEntitySync}) to received entity updates
 *
 * <ul>
 *   <li>{@code handleMoveEntity}: both {@code lerpTo} calls (move and rotate-only) go through
 *       {@link LegacyEntitySync#filterMove}; an update 1.7.10 would not have sent is dropped.</li>
 *   <li>{@code handleTeleportEntity}: 1.21.1 teleports that 1.7.10 would have sent as a normal move (for example when an
 *       entity leaves or touches the ground) get the move rules; real teleports are quantized and raised by 1/64 block.</li>
 *   <li>{@code handleRotateMob}: small head turns are dropped and the head yaw is set directly instead of being
 *       smoothed over 3 ticks.</li>
 *   <li>{@code handleSetEntityMotion}: velocity changes of 0.02 or less are dropped
 *       ({@link LegacyEntitySync#acceptVelocity}); the velocity of the local player and its vehicle is always applied.</li>
 * </ul>
 *
 * <p>Vanilla only calls these for entities that are not controlled by the local player, so the player's own
 * movement and vehicle are never affected. Runs on the main thread (after the packet thread hand-off).</p>
 */
@Mixin(ClientPacketListener.class)
public class LegacyEntitySyncMixin {

    @WrapOperation(
        method = "handleMoveEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V")
    )
    private void animatium$legacyMove(Entity entity, double x, double y, double z, float yaw, float pitch, int steps,
                                      Operation<Void> original) {
        if (!AnimatiumFeature.getConfig().legacyEntitySync) {
            original.call(entity, x, y, z, yaw, pitch, steps);
            return;
        }
        LegacyEntitySync.Target target = LegacyEntitySync.filterMove(entity, x, y, z, yaw, pitch);
        if (target != null) {
            original.call(entity, target.x(), target.y(), target.z(), target.yaw(), target.pitch(), steps);
        }
    }

    @WrapOperation(
        method = "handleTeleportEntity",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFI)V")
    )
    private void animatium$legacyTeleport(Entity entity, double x, double y, double z, float yaw, float pitch, int steps,
                                          Operation<Void> original) {
        if (!AnimatiumFeature.getConfig().legacyEntitySync) {
            original.call(entity, x, y, z, yaw, pitch, steps);
            return;
        }
        LegacyEntitySync.Target target = LegacyEntitySync.filterTeleport(entity, x, y, z, yaw, pitch);
        if (target != null) {
            original.call(entity, target.x(), target.y(), target.z(), target.yaw(), target.pitch(), steps);
        }
    }

    @WrapOperation(
        method = "handleRotateMob",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpHeadTo(FI)V")
    )
    private void animatium$legacyHeadYaw(Entity entity, float headYaw, int steps, Operation<Void> original) {
        if (!AnimatiumFeature.getConfig().legacyEntitySync) {
            original.call(entity, headYaw, steps);
            return;
        }
        Float legacyHeadYaw = LegacyEntitySync.filterHeadYaw(entity, headYaw);
        if (legacyHeadYaw != null) {
            entity.setYHeadRot(legacyHeadYaw);
        }
    }

    @WrapOperation(
        method = "handleSetEntityMotion",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(DDD)V")
    )
    private void animatium$legacyVelocity(Entity entity, double x, double y, double z, Operation<Void> original) {
        if (!AnimatiumFeature.getConfig().legacyEntitySync || entity.isControlledByLocalInstance()
            || LegacyEntitySync.acceptVelocity(entity, x, y, z)) {
            original.call(entity, x, y, z);
        }
    }
}
