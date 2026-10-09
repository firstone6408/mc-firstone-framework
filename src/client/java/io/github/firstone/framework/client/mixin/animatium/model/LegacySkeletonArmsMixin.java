package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.LegacyZombieArms;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives skeletons the 1.7.10 zombie arm pose ({@link LegacyZombieArms})
 *
 * <p>1.7.10 {@code ModelSkeleton} extended {@code ModelZombie}: skeletons always held their arms straight forward,
 * pointing the bow forward, with no aiming pose. 1.21.1 {@code SkeletonModel} lowers the arms until the skeleton is
 * aggressive, then aims the bow. Runs at the end of {@code setupAnim}, so it covers skeletons, wither skeletons,
 * strays and bogged (and the stray's clothing layer, which poses its own copy of the model).</p>
 */
@Mixin(SkeletonModel.class)
public class LegacySkeletonArmsMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/Mob;FFFFF)V", at = @At("TAIL"))
    private void animatium$legacySkeletonArms(Mob mob, float limbSwing, float limbSwingAmount, float ageInTicks,
                                             float headYaw, float headPitch, CallbackInfo ci) {
        if (LegacyZombieArms.enabled()) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            LegacyZombieArms.apply(model.leftArm, model.rightArm, model.attackTime, ageInTicks);
        }
    }
}
