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
     * 1.7.10 item dropping: the arm never swings when dropping an item (with the drop key or out of a screen), and
     * dropping the last item (or the whole stack) lowers the item and raises the empty hand.
     */
    public boolean oldItemDrop = true;

    /**
     * 1.7.10 re-equip rules for the main hand: no dip after attacks (no attack cooldown in 1.7.10),
     * and no re-equip animation when the held item only changes durability in the same slot.
     */
    public boolean legacyReequip = true;

    /**
     * 1.7.10 hurt camera tilt: when the player is hurt the camera always rolls the same way, as the 1.7.10 client
     * did not know where the hit came from (1.21.1 rolls away from the attacker). The Damage Tilt accessibility
     * option still sets how strong it is.
     */
    public boolean legacyHurtTilt = true;

    /**
     * 1.7.10 first-person items: held items, tools and blocks sit where 1.7.10 drew them, with the 1.7.10 bow,
     * eating, drinking and sword blocking poses, and the fishing rod turned around. Items 1.7.10 did not have keep
     * their 1.21.1 look.
     */
    public boolean legacyFirstPersonItems = true;

    /**
     * 1.7.10 third-person items: players, zombies and skeletons hold items, tools, bows and blocks where 1.7.10 drew
     * them, and a player blocking with a sword raises the arm like 1.7.10. Items 1.7.10 did not have and newer mobs
     * keep their 1.21.1 look.
     */
    public boolean legacyThirdPersonItems = true;

    /**
     * 1.7.10 items on the ground: blocks spin lower down, flat items spin with Fancy graphics and turn to the camera
     * without spinning with Fast graphics, and stacks show the 1.7.10 number of copies. Items 1.7.10 did not have keep
     * their 1.21.1 look.
     */
    public boolean legacyGroundItems = true;

    /**
     * Keeps the 1.21.1 look for items whose model a resource pack or another mod changes, so the pack's look is not
     * lost to the 1.7.10 placement
     */
    public boolean resourcePackItems = true;

    /**
     * 1.7.10 right-click animations: no arm swing when using an item in the air (ender pearl, egg, snowball…)
     * or right-clicking an entity, and the hand only dips when the used stack changed. Swings the server
     * sends back to the player (1.21.1 only) are ignored, as 1.7.10 never sent them.
     */
    public boolean oldItemUse = true;

    /**
     * 1.7.10 entity sync: other entities move in 1/32-block steps and only after moving 0.125 block or turning 5.6°,
     * and heads turn instantly — the stepping look of 1.7.10 movement.
     */
    public boolean legacyEntitySync = true;

    /**
     * 1.7.10 mob physics: the client also moves server-controlled mobs with their own velocity, so knockback, sliding
     * and pushing look jerky like 1.7.10 instead of gliding smoothly.
     */
    public boolean legacyMobPhysics = true;

    /**
     * 1.7.10 body rotation: players and old-AI mobs turn their body towards where they walk (sideways when walking
     * backwards, up to 75° from the head); other mobs use the 1.7.10 body helper.
     */
    public boolean legacyBodyRotation = true;

    /**
     * 1.7.10 sneak pose: drawn whenever shift is held (also while flying), only the legs and head move, and the
     * model is lowered like 1.7.10.
     */
    public boolean legacySneakPose = true;

    /**
     * 1.7.10 zombie and skeleton arms: always held straight forward (skeletons point the bow forward, no aiming pose),
     * with the 1.7.10 attack swing
     */
    public boolean legacyZombieArms = true;

    /** 1.7.10 mob swing: mobs only swing their arm when they hold an item (no swing for empty-handed zombies) */
    public boolean legacyMobSwing = true;

    /**
     * 1.7.10 hurt tint: hurt and dying entities, armor included, get the 1.7.10 red pass (40 % red at block
     * brightness, not darkened by night) instead of the darker 1.21.1 tint
     */
    public boolean legacyHurtTint = true;

    /**
     * 1.7.10 fall particles: an entity landing from a fall throws the dust of the block it lands on outwards in a flat
     * ring from the middle of the block's top (1.21.1: a puff around its feet), and the dust slides along the ground
     */
    public boolean legacyFallParticles = true;


    /** Hides the sweep attack particle and mutes the sweep sound (added in 1.9) */
    public boolean noSweepEffect = true;

    /** Hides the damage indicator particles, the dark hearts shown when hitting (added in 1.9) */
    public boolean noDamageIndicator = true;

    /** Mutes the player attack sounds: crit, knockback, nodamage, strong, weak, sweep (added in 1.9) */
    public boolean noAttackSounds = true;
}
