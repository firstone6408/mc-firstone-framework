package io.github.firstone.framework.mixin.legacymechanics;

import io.github.firstone.framework.features.legacymechanics.LegacyBoatRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves out the water splash sound of a floating boat bobbing back into the water on the server
 * ({@link LegacyBoatRules#skipsSplash}); the client leaves out its particles ({@code LegacyBoatSplashMixin} of
 * Legacy Vehicles)
 */
@Mixin(Entity.class)
public class LegacyBoatSplashMixin {

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void legacyMechanics$noBobbingSplash(CallbackInfo ci) {
        if (LegacyBoatRules.skipsSplash((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}
