package io.github.firstone.framework.mixin.combattweaks;

import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that disables the attack cooldown, like versions before 1.9
 *
 * <p>In 1.9+ {@code getAttackStrengthScale()} decides whether this attack
 * deals full damage (0.0 = none, 1.0 = full).
 * The value depends on how long it has been since the last attack</p>
 *
 * <p>This mixin always forces the value to 1.0, so every attack deals full damage
 * without waiting for the cooldown</p>
 *
 * <p>Target: {@link Player#getAttackStrengthScale(float)}</p>
 */
@Mixin(Player.class)
public class NoAttackCooldownMixin {

    /**
     * Forces the attack strength scale to 1.0 while No Attack Cooldown is enabled
     *
     * <p>The returned value is used by {@code Player.attack()} to compute the final damage
     * and by the HUD to show the cooldown indicator under the crosshair</p>
     *
     * @param adjustTicks extra ticks added to the elapsed time when computing the value
     * @param cir         CallbackInfo of a method returning float
     */
    @Inject(method = "getAttackStrengthScale(F)F", at = @At("HEAD"), cancellable = true)
    private void onGetAttackStrengthScale(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        if (CombatTweaksFeature.getConfig().noAttackCooldown) {
            cir.setReturnValue(1.0f);
        }
    }
}
