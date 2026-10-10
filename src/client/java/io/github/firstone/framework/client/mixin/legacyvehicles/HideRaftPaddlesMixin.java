package io.github.firstone.framework.client.mixin.legacyvehicles;

import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.client.model.RaftModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the paddles of bamboo rafts, when "Hide Paddles" is on
 */
@Mixin(RaftModel.class)
public class HideRaftPaddlesMixin {

    @Shadow
    @Final
    private ModelPart leftPaddle;

    @Shadow
    @Final
    private ModelPart rightPaddle;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/vehicle/Boat;FFFFF)V", at = @At("TAIL"))
    private void legacyVehicles$hidePaddles(Boat boat, float limbSwing, float limbSwingAmount, float ageInTicks,
                                            float netHeadYaw, float headPitch, CallbackInfo ci) {
        boolean visible = !LegacyVehiclesFeature.getConfig().hidePaddles;
        this.leftPaddle.visible = visible;
        this.rightPaddle.visible = visible;
    }
}
