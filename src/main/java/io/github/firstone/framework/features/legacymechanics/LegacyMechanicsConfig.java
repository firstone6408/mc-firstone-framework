package io.github.firstone.framework.features.legacymechanics;

/**
 * Settings of the Legacy Mechanics feature, saved in {@code legacy_mechanics.json}
 *
 * <p>These are server rules: in singleplayer they can be changed from the config screen; on a dedicated server the
 * file on the server is used. Every option defaults to on, so 1.7.10 mechanics apply right away.</p>
 */
public class LegacyMechanicsConfig {

    /**
     * 1.7.10 knockback when an entity is hit: always pushed up by 0.4 (also in the air), knockback resistance is a
     * chance to ignore knockback completely, the push comes from the attacker (for arrows: the shooter), and explosions
     * caused by an entity also knock back from it. Hit players get the full knockback right away (1.21.1 sends it one
     * server tick late, after friction has removed about half of it).
     */
    public boolean legacyKnockback = true;

    /**
     * 1.7.10 extra knockback from the attacker (players and mobs): sprint knockback without a fully charged attack,
     * applied as a push with 0.1 upward motion, not reduced by knockback resistance.
     */
    public boolean legacyAttackKnockback = true;

    /**
     * 1.7.10 crouch hitbox: crouching players keep the full 1.8-block hitbox and 1.62 eye height on the server.
     */
    public boolean legacyCrouchHitbox = true;

    /**
     * 1.7.10 boat breaking: a wooden boat that hits a wall while faster than 0.2 blocks per tick breaks into 3 planks
     * and 2 sticks, and boats remove the snow layers they go through.
     */
    public boolean legacyBoatBreaking = true;

    /**
     * Chest boats and bamboo rafts break the same way (they did not exist in 1.7.10); a chest boat also drops its
     * chest and everything in it. Only used while {@link #legacyBoatBreaking} is on.
     */
    public boolean breakChestBoatsAndRafts = true;

    /**
     * Boats on slippery blocks (ice, packed ice, blue ice) do not break when they hit something, so ice roads stay
     * usable. Only used while {@link #legacyBoatBreaking} is on.
     */
    public boolean boatsSafeOnIce = true;

    /**
     * 1.7.10 motion for boats without a driver: a boat keeps going after its driver gets out and slows down slowly,
     * empty boats float as high as in 1.7.10 (1.21.1 stops the boat at once), someone standing on a boat pushes
     * it away from where they stand, so it slides out from under them, and boats with someone inside are pushed by
     * boats and entities they touch.
     */
    public boolean legacyBoatCoasting = true;
}
