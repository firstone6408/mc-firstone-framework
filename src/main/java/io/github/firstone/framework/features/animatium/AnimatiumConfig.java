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
}
