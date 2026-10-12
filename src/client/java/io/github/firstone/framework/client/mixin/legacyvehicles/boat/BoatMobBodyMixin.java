package io.github.firstone.framework.client.mixin.legacyvehicles.boat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.firstone.framework.features.legacyvehicles.boat.BoatMobTurning;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Turns the body of a mob carried by a boat as when it stands on the ground, on this client ({@link BoatMobTurning})
 *
 * <p>Wraps {@code Mob.tickHeadTurn}, where the mob's body rules run ({@code BodyRotationControl}, or Animatium's
 * 1.7.10 rules, which are kept inside the wrap). Only the client's copy of the mob is changed.</p>
 */
@Mixin(Mob.class)
public class BoatMobBodyMixin {

    @WrapMethod(method = "tickHeadTurn")
    private float legacyVehicles$turnBodyStanding(float movementYaw, float walkAmount, Operation<Float> original) {
        Mob mob = (Mob) (Object) this;
        if (BoatMobTurning.isCarried(mob)) {
            return BoatMobTurning.turnBodyStanding(mob, () -> original.call(movementYaw, walkAmount));
        }
        return original.call(movementYaw, walkAmount);
    }
}
