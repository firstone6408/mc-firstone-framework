package io.github.firstone.framework.client.mixin.legacyvehicles.boat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.legacyvehicles.boat.LegacyBoatHands;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the hands free while paddling, like 1.7.10 ({@link LegacyBoatHands})
 *
 * <p>Changes what {@code isHandsBusy} reports, so its three readers (the hand renderer, attacking and using items)
 * behave as when not paddling; the boat still gets the movement keys.</p>
 */
@Mixin(LocalPlayer.class)
public class LegacyBoatHandsMixin {

    @ModifyReturnValue(method = "isHandsBusy", at = @At("RETURN"))
    private boolean legacyvehicles$freeHands(boolean busy) {
        return LegacyBoatHands.handsBusy(busy);
    }
}
