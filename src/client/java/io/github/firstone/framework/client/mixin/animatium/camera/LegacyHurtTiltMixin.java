package io.github.firstone.framework.client.mixin.animatium.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.animatium.camera.LegacyHurtTilt;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Turns the hurt camera tilt like 1.7.10 ({@link LegacyHurtTilt})
 *
 * <p>Replaces the hurt direction read by {@code GameRenderer.bobHurt}, the only place it is used for the camera; the
 * strength, timing and death roll stay as they are (the same in both versions).</p>
 */
@Mixin(GameRenderer.class)
public class LegacyHurtTiltMixin {

    @WrapOperation(method = "bobHurt",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getHurtDir()F"))
    private float animatium$legacyHurtDirection(LivingEntity entity, Operation<Float> original) {
        return LegacyHurtTilt.direction(original.call(entity));
    }
}
