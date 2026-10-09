package io.github.firstone.framework.features.animatium;

/**
 * Settings of the Animatium feature, stored in {@code animatium.json}
 *
 * <p>Each option restores one Minecraft 1.7.10 behavior and can be toggled on its own.
 * Every option defaults to {@code true} so the legacy behavior applies right away.</p>
 */
public class AnimatiumConfig {

    /**
     * 1.7.10 sneaking: the hitbox keeps its standing height (1.8 blocks) and the eyes drop only
     * 0.08 blocks, with the 1.7.10 timing. The camera and the aim (crosshair target) use the same
     * eye height, so what you see is what you hit.
     */
    public boolean oldSneak = true;

    /**
     * 1.7.10 item dropping: the arm never swings when dropping an item, and dropping the last item
     * (or the whole stack) lowers the item and raises the empty hand.
     */
    public boolean oldItemDrop = true;

    /**
     * 1.7.10 re-equip rules for the main hand: no dip after attacks (no attack cooldown in 1.7.10),
     * and no re-equip animation when the held item only changes durability in the same slot.
     */
    public boolean legacyReequip = true;

    /**
     * 1.7.10 right-click animations: no arm swing when using an item in the air (ender pearl, egg, snowball…)
     * or right-clicking an entity, and the hand only dips when the used stack changed. Swings the server
     * sends back to the player (1.21.1 only) are ignored, as 1.7.10 never sent them.
     */
    public boolean oldItemUse = true;

    /** Hides the sweep attack particle and mutes the sweep sound (added in 1.9) */
    public boolean noSweepEffect = true;

    /** Hides the damage indicator particles, the dark hearts shown when hitting (added in 1.9) */
    public boolean noDamageIndicator = true;

    /** Mutes the player attack sounds: crit, knockback, nodamage, strong, weak, sweep (added in 1.9) */
    public boolean noAttackSounds = true;
}
