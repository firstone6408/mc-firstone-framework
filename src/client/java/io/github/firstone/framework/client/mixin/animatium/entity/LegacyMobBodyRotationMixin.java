package io.github.firstone.framework.client.mixin.animatium.entity;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.LegacyBodyRotation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns mobs' bodies with the 1.7.10 rules ({@link LegacyBodyRotation}) on the client
 *
 * <ul>
 *   <li>Mobs that used the old AI in 1.7.10 ({@link LegacyBodyRotation#usesOldAi}): body follows movement like a player.</li>
 *   <li>Other mobs with the standard {@link BodyRotationControl}: the 1.7.10 body helper, with the mob's own head limit.</li>
 *   <li>Mobs with their own body control (shulker, phantom, camel) keep it; an armadillo keeps still while rolled up.</li>
 *   <li>The ender dragon sets its body yaw itself (in both versions) and is left alone.</li>
 * </ul>
 *
 * <p>Body rotation is computed by each client, so this works on any server; the integrated server is never touched.</p>
 */
@Mixin(Mob.class)
public class LegacyMobBodyRotationMixin {

    @Shadow
    @Final
    private BodyRotationControl bodyRotationControl;

    @Unique
    private final LegacyBodyRotation.HelperState animatium$helperState = new LegacyBodyRotation.HelperState();

    @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyMobBodyTurn(float movementYaw, float walkAmount, CallbackInfoReturnable<Float> cir) {
        Mob self = (Mob) (Object) this;
        if (!self.level().isClientSide() || !AnimatiumFeature.getConfig().legacyBodyRotation || self instanceof EnderDragon) {
            return;
        }
        if (LegacyBodyRotation.usesOldAi(self)) {
            cir.setReturnValue(LegacyBodyRotation.turnTowardsMovement(self, walkAmount));
        } else if (self instanceof Armadillo armadillo) {
            if (!armadillo.isScared()) {
                LegacyBodyRotation.followHead(self, this.animatium$helperState);
            }
            cir.setReturnValue(walkAmount);
        } else if (this.bodyRotationControl.getClass() == BodyRotationControl.class) {
            LegacyBodyRotation.followHead(self, this.animatium$helperState);
            cir.setReturnValue(walkAmount);
        }
    }
}
