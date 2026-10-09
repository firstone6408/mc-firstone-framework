package io.github.firstone.framework.mixin.legacymechanics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.LegacyKnockback;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies a mob's extra attack knockback like 1.7.10 {@code EntityMob.attackEntityAsMob}
 *
 * <p>1.7.10 pushed the target with {@code addVelocity(-sin·k·0.5, 0.1, cos·k·0.5)}; 1.21.1 calls {@code knockback}
 * (the only call in {@code doHurtTarget}). Same push as the player's ({@link LegacyKnockback#attackerPush}).
 * Server only: mobs attack on the server.</p>
 */
@Mixin(Mob.class)
public class LegacyMobAttackKnockbackMixin {

    @WrapOperation(
        method = "doHurtTarget",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V")
    )
    private void legacymechanics$legacyMobAttackKnockback(LivingEntity target, double strength, double sinYaw, double negCosYaw,
                                                         Operation<Void> original) {
        if (LegacyMechanicsFeature.getConfig().legacyAttackKnockback) {
            LegacyKnockback.attackerPush(target, strength, sinYaw, negCosYaw);
        } else {
            original.call(target, strength, sinYaw, negCosYaw);
        }
    }
}
