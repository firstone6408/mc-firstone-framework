package io.github.firstone.framework.client.mixin.animatium.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.animatium.LegacyMobPhysics;
import net.minecraft.world.entity.FlyingMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Runs 1.7.10 client-side physics for flying mobs (ghast, phantom) controlled by the server
 *
 * <p>1.7.10 {@code EntityFlying.moveEntityWithHeading} also ran on the client. {@code FlyingMob.travel} has its own
 * {@code isControlledByLocalInstance()} check; see {@link LegacyMobPhysicsMixin}.</p>
 */
@Mixin(FlyingMob.class)
public class LegacyFlyingMobPhysicsMixin {

    @ModifyExpressionValue(
        method = "travel",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/FlyingMob;isControlledByLocalInstance()Z")
    )
    private boolean animatium$simulateRemoteFlyingMob(boolean controlled) {
        return controlled || LegacyMobPhysics.appliesTo((FlyingMob) (Object) this);
    }
}
