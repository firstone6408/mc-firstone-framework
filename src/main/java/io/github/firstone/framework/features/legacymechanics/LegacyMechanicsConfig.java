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
}
