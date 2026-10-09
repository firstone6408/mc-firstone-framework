package io.github.firstone.framework.client.mixin.animatium.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.animatium.LegacyMobPhysics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Runs 1.7.10 client-side physics for mobs controlled by the server ({@link LegacyMobPhysics})
 *
 * <ul>
 *   <li>{@code travel}: the only {@code isControlledByLocalInstance()} check is treated as true for those mobs, so the
 *       client moves them with their velocity like 1.7.10 {@code moveEntityWithHeading}. Their movement input is
 *       zero on the client, as in 1.7.10, so only velocity, gravity and friction move them.</li>
 *   <li>{@code aiStep}: small velocities are cut at 0.005 like 1.7.10 instead of 0.003.</li>
 * </ul>
 *
 * <p>Players and everything the client already controls are unchanged; the integrated server is never touched.</p>
 */
@Mixin(LivingEntity.class)
public class LegacyMobPhysicsMixin {

    @ModifyExpressionValue(
        method = "travel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isControlledByLocalInstance()Z")
    )
    private boolean animatium$simulateRemoteMob(boolean controlled) {
        return controlled || LegacyMobPhysics.appliesTo((LivingEntity) (Object) this);
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(doubleValue = 0.003))
    private double animatium$legacyMinVelocity(double minVelocity) {
        return LegacyMobPhysics.appliesTo((LivingEntity) (Object) this) ? LegacyMobPhysics.MIN_VELOCITY : minVelocity;
    }
}
