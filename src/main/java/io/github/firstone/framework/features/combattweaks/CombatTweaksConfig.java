package io.github.firstone.framework.features.combattweaks;

/**
 * Holds the settings of the Combat Tweaks feature
 *
 * <p>Every value in this class is saved to a JSON file and loaded when the game starts</p>
 *
 * <p>Every option defaults to enabled (true) so pre-1.9 combat applies right away</p>
 */
public class CombatTweaksConfig {

    /**
     * Disables the attack cooldown, like versions before 1.9
     *
     * <p>When enabled: you can attack rapidly and every hit deals full damage, with no damage reduction
     * from the cooldown</p>
     * <p>Vanilla behavior: you must wait for the cooldown to deal full damage</p>
     */
    public boolean noAttackCooldown = true;

    /**
     * Disables the sweeping attack (AOE), like versions before 1.9
     *
     * <p>When enabled: attacks do not damage nearby entities,
     * unless {@link #sweepingEdgeRequired} is on and the weapon has the Sweeping Edge enchantment</p>
     * <p>Vanilla behavior: every full-strength attack also hits nearby entities</p>
     */
    public boolean disableSweepingAttack = true;

    /**
     * Requires the Sweeping Edge enchantment to use the sweeping attack
     *
     * <p>Only used together with {@link #disableSweepingAttack}.
     * When enabled: the player can only sweep if the weapon has Sweeping Edge (level 1-3)</p>
     * <p>If {@code disableSweepingAttack} is false, this value has no effect</p>
     */
    public boolean sweepingEdgeRequired = true;
}
