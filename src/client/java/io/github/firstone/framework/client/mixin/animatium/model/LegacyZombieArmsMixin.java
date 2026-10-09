package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses the 1.7.10 zombie arm pose ({@code ModelZombie.setRotationAngles}) everywhere the game uses zombie arms
 *
 * <p>1.7.10 always held the arms straight forward (−π/2) and swung them with {@code xRot -= sin·1.2 − sin2·0.4}.
 * 1.21.1 holds them lower when idle (−π/2.25), higher when aggressive (−π/1.5), and swings the other way. The idle
 * arm bob is the same in both versions.</p>
 */
@Mixin(AnimationUtils.class)
public class LegacyZombieArmsMixin {

    @Inject(method = "animateZombieArms", at = @At("HEAD"), cancellable = true)
    private static void animatium$legacyZombieArms(ModelPart leftArm, ModelPart rightArm, boolean aggressive,
                                                   float attackTime, float ageInTicks, CallbackInfo ci) {
        if (!AnimatiumFeature.getConfig().legacyZombieArms) {
            return;
        }
        float swing = Mth.sin(attackTime * (float) Math.PI);
        float swing2 = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightArm.yRot = -(0.1F - swing * 0.6F);
        leftArm.yRot = 0.1F - swing * 0.6F;
        rightArm.xRot = (float) (-Math.PI / 2) - (swing * 1.2F - swing2 * 0.4F);
        leftArm.xRot = (float) (-Math.PI / 2) - (swing * 1.2F - swing2 * 0.4F);
        AnimationUtils.bobArms(rightArm, leftArm, ageInTicks);
        ci.cancel();
    }
}
