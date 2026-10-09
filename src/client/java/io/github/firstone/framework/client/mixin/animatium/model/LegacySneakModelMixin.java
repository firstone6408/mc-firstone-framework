package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.LegacySneakPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses the 1.7.10 sneak pose of {@code ModelBiped}
 *
 * <p>Both versions rotate the body by 0.5 rad, raise the arms by 0.4 rad and move the legs back (z = 4). 1.21.1 also
 * lowers the head, body and arms by 3.2–4.2 pixels and the legs to y = 12.2. 1.7.10 only moved the legs up to y = 9
 * and the head down to y = 1. This runs at the end of {@code setupAnim} and restores the 1.7.10 positions; the armor
 * layers copy these parts, so armor follows.</p>
 */
@Mixin(HumanoidModel.class)
public class LegacySneakModelMixin {

    @Shadow public boolean crouching;
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void animatium$legacySneakPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                           float headYaw, float headPitch, CallbackInfo ci) {
        if (!this.crouching || !LegacySneakPose.enabled()) {
            return;
        }
        this.head.y = 1.0F;
        this.body.y = 0.0F;
        this.rightArm.y = 2.0F;
        this.leftArm.y = 2.0F;
        this.rightLeg.y = 9.0F;
        this.leftLeg.y = 9.0F;
        this.hat.copyFrom(this.head);
    }
}
