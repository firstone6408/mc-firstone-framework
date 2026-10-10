package io.github.firstone.framework.client.mixin.legacyvehicles;

import io.github.firstone.framework.features.legacyvehicles.LegacyBoats;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out the water splash particles of a floating boat bobbing back into the water on the client
 * ({@link LegacyBoats#skipsSplash})
 */
@Mixin(Entity.class)
public class LegacyBoatSplashMixin {

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void legacyVehicles$noBobbingSplash(CallbackInfo ci) {
        if (LegacyBoats.skipsSplash((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
