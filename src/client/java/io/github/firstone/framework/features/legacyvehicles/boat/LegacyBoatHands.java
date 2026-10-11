package io.github.firstone.framework.features.legacyvehicles.boat;

import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;

/**
 * 1.7.10 hands while paddling a boat
 *
 * <p>1.21.1 marks the local player's hands as busy while they hold a movement key in a boat they steer
 * ({@code LocalPlayer.rideTick}, the only place that sets it). Busy hands lower the hand and the held item out of
 * view ({@code ItemInHandRenderer.tick}) and stop attacking and using items ({@code Minecraft.startAttack} and
 * {@code startUseItem}). 1.7.10 had none of this: the hand stayed up and every click worked while paddling.</p>
 */
public final class LegacyBoatHands {

    private LegacyBoatHands() {}

    /**
     * Tells whether the local player's hands count as busy
     *
     * @param busy whether 1.21.1 counts them as busy (paddling a boat)
     * @return never busy when "Legacy Hands" is on, otherwise {@code busy}
     */
    public static boolean handsBusy(boolean busy) {
        return busy && !LegacyVehiclesFeature.getConfig().legacyBoatHands;
    }
}
