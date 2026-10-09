package io.github.firstone.framework.mixin.legacymechanics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.LegacyKnockback;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Applies the attacker's extra knockback like 1.7.10 ({@link LegacyKnockback#attackerPush})
 *
 * <ul>
 *   <li>Sprint knockback: 1.7.10 added it whenever the attacker sprinted; 1.21.1 also needs an attack charged above
 *       90 % ({@code h > 0.9F}, the only 0.9 constant in {@code attack}). While sprinting, that threshold is lowered
 *       so the sprint bonus always applies. When sprinting, the charge only decides the sprint bonus and the
 *       strong/weak attack sound (critical hits and sweeps already require not sprinting).</li>
 *   <li>The bonus itself (first {@code knockback} call, ordinal 0; the second one is the sweep) becomes a plain push
 *       with 0.1 upward motion, not reduced by knockback resistance.</li>
 * </ul>
 * <p>On the client {@code hurt} returns false, so the push only happens on the server.</p>
 */
@Mixin(Player.class)
public class LegacyAttackKnockbackMixin {

    @ModifyConstant(method = "attack", constant = @Constant(floatValue = 0.9F))
    private float legacymechanics$sprintKnockbackWithoutCharge(float threshold) {
        Player self = (Player) (Object) this;
        return LegacyMechanicsFeature.getConfig().legacyAttackKnockback && self.isSprinting() ? -1.0F : threshold;
    }

    @WrapOperation(
        method = "attack",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V", ordinal = 0)
    )
    private void legacymechanics$legacyAttackKnockback(LivingEntity target, double strength, double sinYaw, double negCosYaw,
                                                      Operation<Void> original) {
        if (LegacyMechanicsFeature.getConfig().legacyAttackKnockback) {
            LegacyKnockback.attackerPush(target, strength, sinYaw, negCosYaw);
        } else {
            original.call(target, strength, sinYaw, negCosYaw);
        }
    }
}
