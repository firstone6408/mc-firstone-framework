package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.model.LegacyThirdPersonItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Raises a sword blocking arm like 1.7.10 instead of the 1.21.1 shield pose ({@link LegacyThirdPersonItem})
 *
 * <p>The blocking arm's pose is chosen in {@code poseRightArm} and {@code poseLeftArm}; for a player blocking with a
 * sword the 1.7.10 pose replaces it. Shields keep their 1.21.1 pose.</p>
 */
@Mixin(HumanoidModel.class)
public class LegacySwordBlockPoseMixin {

    @Inject(method = "poseRightArm", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyBlockRight(LivingEntity entity, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (model.rightArmPose == HumanoidModel.ArmPose.BLOCK
            && LegacyThirdPersonItem.blocksWithSword(entity, HumanoidArm.RIGHT)) {
            LegacyThirdPersonItem.poseBlockingArm(model.rightArm);
            ci.cancel();
        }
    }

    @Inject(method = "poseLeftArm", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyBlockLeft(LivingEntity entity, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (model.leftArmPose == HumanoidModel.ArmPose.BLOCK
            && LegacyThirdPersonItem.blocksWithSword(entity, HumanoidArm.LEFT)) {
            LegacyThirdPersonItem.poseBlockingArm(model.leftArm);
            ci.cancel();
        }
    }
}
