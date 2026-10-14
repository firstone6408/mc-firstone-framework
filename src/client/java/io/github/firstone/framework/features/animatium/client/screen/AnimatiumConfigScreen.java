package io.github.firstone.framework.features.animatium.client.screen;

import io.github.firstone.framework.client.screen.CategoryMenuScreen;
import io.github.firstone.framework.client.screen.ConfigCategory;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

import java.util.List;

import static io.github.firstone.framework.client.screen.ConfigCategory.toggle;

/**
 * Settings screen of the Animatium feature: its options in categories, grouped by what they change in the game
 *
 * <ul>
 *   <li><b>Items</b> — where held items and items on the ground are drawn</li>
 *   <li><b>Hand Animations</b> — how the first-person hand moves when using, dropping and switching items</li>
 *   <li><b>Sneaking</b> — the eyes, hitbox and pose of sneaking players</li>
 *   <li><b>Players &amp; Mobs</b> — how other entities move, turn and swing</li>
 *   <li><b>Hits &amp; Effects</b> — the hurt camera tilt and tint, landing dust and the effects added in 1.9</li>
 * </ul>
 *
 * <p>Each change is saved to {@code animatium.json} and takes effect right away.</p>
 */
public class AnimatiumConfigScreen extends CategoryMenuScreen<AnimatiumConfig> {

    /** The categories and their options, in the order they are shown */
    private static final List<ConfigCategory<AnimatiumConfig>> CATEGORIES = List.of(
        ConfigCategory.of("items", Items.IRON_SWORD, List.of(
            toggle("legacy_first_person_items", c -> c.legacyFirstPersonItems, (c, v) -> c.legacyFirstPersonItems = v),
            toggle("legacy_third_person_items", c -> c.legacyThirdPersonItems, (c, v) -> c.legacyThirdPersonItems = v),
            toggle("legacy_ground_items", c -> c.legacyGroundItems, (c, v) -> c.legacyGroundItems = v),
            toggle("resource_pack_items", c -> c.resourcePackItems, (c, v) -> c.resourcePackItems = v,
                c -> c.legacyFirstPersonItems || c.legacyThirdPersonItems || c.legacyGroundItems))),
        ConfigCategory.of("hand", Items.SNOWBALL, List.of(
            toggle("old_item_use", c -> c.oldItemUse, (c, v) -> c.oldItemUse = v),
            toggle("old_item_drop", c -> c.oldItemDrop, (c, v) -> c.oldItemDrop = v),
            toggle("legacy_reequip", c -> c.legacyReequip, (c, v) -> c.legacyReequip = v))),
        ConfigCategory.of("sneaking", Items.LEATHER_BOOTS, List.of(
            toggle("old_sneak", c -> c.oldSneak, (c, v) -> c.oldSneak = v),
            toggle("legacy_sneak_pose", c -> c.legacySneakPose, (c, v) -> c.legacySneakPose = v))),
        ConfigCategory.of("entities", Items.ZOMBIE_HEAD, List.of(
            toggle("legacy_entity_sync", c -> c.legacyEntitySync, (c, v) -> c.legacyEntitySync = v),
            toggle("legacy_mob_physics", c -> c.legacyMobPhysics, (c, v) -> c.legacyMobPhysics = v),
            toggle("legacy_body_rotation", c -> c.legacyBodyRotation, (c, v) -> c.legacyBodyRotation = v),
            toggle("legacy_zombie_arms", c -> c.legacyZombieArms, (c, v) -> c.legacyZombieArms = v),
            toggle("legacy_mob_swing", c -> c.legacyMobSwing, (c, v) -> c.legacyMobSwing = v))),
        ConfigCategory.of("effects", Items.REDSTONE, List.of(
            toggle("legacy_hurt_tilt", c -> c.legacyHurtTilt, (c, v) -> c.legacyHurtTilt = v),
            toggle("legacy_hurt_tint", c -> c.legacyHurtTint, (c, v) -> c.legacyHurtTint = v),
            toggle("legacy_fall_particles", c -> c.legacyFallParticles, (c, v) -> c.legacyFallParticles = v),
            toggle("no_sweep_effect", c -> c.noSweepEffect, (c, v) -> c.noSweepEffect = v),
            toggle("no_damage_indicator", c -> c.noDamageIndicator, (c, v) -> c.noDamageIndicator = v),
            toggle("no_attack_sounds", c -> c.noAttackSounds, (c, v) -> c.noAttackSounds = v))));

    /**
     * Creates the Animatium settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public AnimatiumConfigScreen(Screen parent) {
        super(parent, "firstone-framework.animatium.", AnimatiumFeature::getConfig, AnimatiumConfig::new,
            AnimatiumFeature::saveConfig, AnimatiumFeature::resetConfig, null, CATEGORIES);
    }
}
