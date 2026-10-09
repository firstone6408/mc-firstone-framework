package io.github.firstone.framework.mixin.legacymechanics;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.firstone.framework.features.legacymechanics.LegacyKnockback;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the 1.7.10 knockback of a hit inside {@code LivingEntity.hurt} ({@link LegacyKnockback})
 *
 * <ul>
 *   <li>{@code markHurt()}: like 1.7.10 {@code setBeenAttacked}, only marks the entity (which sends its velocity
 *       right away, the only way a player learns about its knockback) when a knockback-resistance roll passes.</li>
 *   <li>{@code is(NO_KNOCKBACK)} (ordinal 7, checked to really be that tag): explosions caused by an entity are not
 *       treated as "no knockback", because 1.7.10 also knocked players back from the creeper/TNT.</li>
 *   <li>{@code knockback(0.4, dx, dz)}, the only call: replaced by the 1.7.10 knockback.</li>
 * </ul>
 * <p>{@code hurt} returns early on the client, so all of this only runs on the server. Other knockbacks (shields,
 * ravagers, the explosion's own push…) are unchanged.</p>
 */
@Mixin(LivingEntity.class)
public class LegacyKnockbackMixin {

    @WrapWithCondition(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;markHurt()V"))
    private boolean legacymechanics$legacyMarkHurt(LivingEntity victim) {
        return !LegacyMechanicsFeature.getConfig().legacyKnockback || LegacyKnockback.passesResistanceRoll(victim);
    }

    @WrapOperation(
        method = "hurt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 7)
    )
    private boolean legacymechanics$explosionKnockback(DamageSource source, TagKey<DamageType> tag, Operation<Boolean> original) {
        boolean result = original.call(source, tag);
        if (result && tag == DamageTypeTags.NO_KNOCKBACK && LegacyMechanicsFeature.getConfig().legacyKnockback
            && source.is(DamageTypeTags.IS_EXPLOSION) && LegacyKnockback.attackerOf(source) != null) {
            return false;
        }
        return result;
    }

    @WrapOperation(
        method = "hurt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V")
    )
    private void legacymechanics$legacyKnockback(LivingEntity victim, double strength, double dx, double dz,
                                                Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
        if (LegacyMechanicsFeature.getConfig().legacyKnockback) {
            LegacyKnockback.knockBack(victim, LegacyKnockback.attackerOf(source));
        } else {
            original.call(victim, strength, dx, dz);
        }
    }
}
