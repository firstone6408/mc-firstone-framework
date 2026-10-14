package io.github.firstone.framework.features.legacyhud;

/**
 * Holds the settings of the Legacy HUD feature
 *
 * <p>Every value in this class is saved to {@code legacy_hud.json} and loaded when the game starts.</p>
 *
 * <p>This feature works on the client only</p>
 */
public class LegacyHudConfig {

    /**
     * Hide the status effect icons in the top-right corner of the HUD
     *
     * <p>1.7.10 had no effect icons on the HUD; active effects were only listed in the inventory.
     * The effects themselves and the inventory list are not changed.</p>
     */
    public boolean hideEffectIcons = true;

    /**
     * List the effects on the left of the inventory window, like 1.7.10 ({@code LegacyInventoryEffects})
     *
     * <p>When the inventory or the creative inventory opens while the player has effects, the window moves right
     * and the effects are listed on its left. While the recipe book is open, the 1.21.1 layout is kept.</p>
     */
    public boolean legacyInventoryEffects = true;
}
