package io.github.firstone.framework.features.animatium;

/**
 * Holds the settings of the Animatium feature
 *
 * <p>Every value in this class is saved to a JSON file and loaded when the game starts</p>
 *
 * <p>Every option defaults to enabled (true) so legacy animations apply right away</p>
 */
public class AnimatiumConfig {

    /**
     * Disables the re-equip animation, like versions before 1.9
     *
     * <p>When enabled: the held item no longer dips down after an attack (attack cooldown) or when the held item only changes durability;
     * switching to a different item still lowers and raises the hand</p>
     * <p>Vanilla behavior: the held item dips down and comes back up after attacks and whenever the held stack changes</p>
     */
    public boolean noReequipAnimation = true;

    /**
     * Enables the old sneak animation, like versions before 1.9
     *
     * <p>When enabled: pressing Shift moves the camera to the sneaking height instantly with no transition,
     * and the camera only drops slightly</p>
     * <p>Vanilla behavior: the camera lowers smoothly over a few frames when Shift is pressed</p>
     */
    public boolean oldSneakAnimation = true;

    /**
     * Enables the old item drop animation, like versions before 1.9
     *
     * <p>When enabled: dropping an item plays no throwing gesture; the arm stays still
     * (except when the main-hand stack has only one item left, which keeps the swing)</p>
     * <p>Vanilla behavior: the arm swings when an item is dropped</p>
     */
    public boolean oldItemDropAnimation = true;

    /**
     * Hides the sweep attack effect, like versions before 1.9
     *
     * <p>When enabled: sweep attacks show no sweep particle and play no PLAYER_ATTACK_SWEEP sound</p>
     * <p>Vanilla behavior: sweep attacks show a sweep particle animation and play a special sound</p>
     */
    public boolean noSweepEffect = true;

    /**
     * Hides the damage indicator particle, like versions before 1.9
     *
     * <p>When enabled: attacks show no particles indicating the amount of damage</p>
     * <p>Vanilla behavior: small particles float up after a successful hit</p>
     */
    public boolean noDamageIndicatorParticle = true;

    /**
     * Mutes attack sounds, like versions before 1.9
     *
     * <p>When enabled: attacks play no CRIT, STRONG, WEAK, NODAMAGE or KNOCKBACK sounds</p>
     * <p>Vanilla behavior: attacks play different sounds depending on the result of the attack</p>
     */
    public boolean noAttackSounds = true;
}
