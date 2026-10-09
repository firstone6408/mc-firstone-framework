package io.github.firstone.framework.features.animatium;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The 1.7.10 zombie arm pose, used by zombies and skeletons
 *
 * <p>1.7.10 {@code ModelZombie.setRotationAngles}: both arms always straight forward (−π/2), swung with
 * {@code xRot -= sin·1.2 − sin2·0.4}, plus the idle bob. {@code ModelSkeleton} extended {@code ModelZombie}, so
 * skeletons (with or without a bow) held their arms the same way. 1.21.1 lowers the arms when idle, raises them when
 * aggressive and gives skeletons a bow-aiming pose.</p>
 */
public final class LegacyZombieArms {

    private LegacyZombieArms() {}

    /**
     * Tells whether the 1.7.10 arm pose is used
     *
     * @return true if "Legacy Zombie &amp; Skeleton Arms" is on
     */
    public static boolean enabled() {
        return AnimatiumFeature.getConfig().legacyZombieArms;
    }

    /**
     * Poses both arms like 1.7.10 {@code ModelZombie}
     *
     * @param leftArm    the left arm
     * @param rightArm   the right arm
     * @param attackTime swing progress (0 when not swinging)
     * @param ageInTicks entity age, for the idle bob
     */
    public static void apply(ModelPart leftArm, ModelPart rightArm, float attackTime, float ageInTicks) {
        float swing = Mth.sin(attackTime * (float) Math.PI);
        float swing2 = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightArm.yRot = -(0.1F - swing * 0.6F);
        leftArm.yRot = 0.1F - swing * 0.6F;
        rightArm.xRot = (float) (-Math.PI / 2) - (swing * 1.2F - swing2 * 0.4F);
        leftArm.xRot = (float) (-Math.PI / 2) - (swing * 1.2F - swing2 * 0.4F);
        AnimationUtils.bobArms(rightArm, leftArm, ageInTicks);
    }
}
