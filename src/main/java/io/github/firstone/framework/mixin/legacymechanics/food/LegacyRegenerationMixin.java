package io.github.firstone.framework.mixin.legacymechanics.food;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.legacymechanics.food.LegacyRegeneration;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the food tick the 1.7.10 natural regeneration ({@link LegacyRegeneration})
 *
 * <p>{@code FoodData.tick} checks the fast saturation rule first ({@code isHurt} ordinal 0) and the slow rule second
 * ({@code isHurt} ordinal 1, {@code addExhaustion} ordinal 1). Turning off the fast rule's check lets the tick fall
 * through to the slow rule, whose exhaustion is set to the 1.7.10 value. Runs on the server (also the integrated
 * one).</p>
 */
@Mixin(FoodData.class)
public class LegacyRegenerationMixin {

    @ModifyExpressionValue(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z", ordinal = 0))
    private boolean legacymechanics$noSaturationHealing(boolean hurt) {
        return LegacyRegeneration.allowsSaturationHealing(hurt);
    }

    @ModifyArg(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V", ordinal = 1))
    private float legacymechanics$legacyExhaustion(float exhaustion) {
        return LegacyRegeneration.exhaustionPerHeal(exhaustion);
    }
}
