package io.github.firstone.framework.client.mixin.animatium.entity;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.LegacyBodyRotation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns players' bodies with the 1.7.10 rules ({@link LegacyBodyRotation#turnTowardsMovement})
 *
 * <p>Replaces {@code LivingEntity.tickHeadTurn} for players on the client: no forward flip when walking backwards and
 * a 75° head limit instead of 50°. Body rotation is computed by each client, so this works on any server. Mobs have
 * their own override ({@code Mob.tickHeadTurn}, see {@link LegacyMobBodyRotationMixin}); the integrated server is never
 * touched.</p>
 */
@Mixin(LivingEntity.class)
public class LegacyPlayerBodyRotationMixin {

    @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyPlayerBodyTurn(float movementYaw, float walkAmount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player && self.level().isClientSide() && AnimatiumFeature.getConfig().legacyBodyRotation) {
            cir.setReturnValue(LegacyBodyRotation.turnTowardsMovement(self, walkAmount));
        }
    }
}
