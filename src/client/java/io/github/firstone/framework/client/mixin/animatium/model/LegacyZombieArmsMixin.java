package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.LegacyZombieArms;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses the 1.7.10 zombie arm pose ({@link LegacyZombieArms}) everywhere 1.21.1 uses zombie arms
 * ({@code AnimationUtils.animateZombieArms}: zombies, husks, drowned, zombie villagers, piglins, illagers)
 */
@Mixin(AnimationUtils.class)
public class LegacyZombieArmsMixin {

    @Inject(method = "animateZombieArms", at = @At("HEAD"), cancellable = true)
    private static void animatium$legacyZombieArms(ModelPart leftArm, ModelPart rightArm, boolean aggressive,
                                                   float attackTime, float ageInTicks, CallbackInfo ci) {
        if (LegacyZombieArms.enabled()) {
            LegacyZombieArms.apply(leftArm, rightArm, attackTime, ageInTicks);
            ci.cancel();
        }
    }
}
