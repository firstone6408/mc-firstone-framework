package io.github.firstone.framework.client.mixin.legacyvehicles;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.legacyvehicles.LegacyBoatModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws wooden boats with the 1.7.10 model ({@link LegacyBoatModel}) instead of the 1.21.1 model
 *
 * <p>The name tag (if any) is still drawn by {@code EntityRenderer.render}, as the 1.21.1 renderer does.</p>
 */
@Mixin(BoatRenderer.class)
public abstract class LegacyBoatModelMixin extends EntityRenderer<Boat> {

    private LegacyBoatModelMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"), cancellable = true)
    private void legacyVehicles$legacyModel(Boat boat, float yaw, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (LegacyBoatModel.draws(boat)) {
            LegacyBoatModel.render(boat, yaw, partialTick, poseStack, buffers, light);
            super.render(boat, yaw, partialTick, poseStack, buffers, light);
            ci.cancel();
        }
    }
}
