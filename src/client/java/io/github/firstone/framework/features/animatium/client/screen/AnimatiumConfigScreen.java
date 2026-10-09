package io.github.firstone.framework.features.animatium.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen of the Animatium feature
 *
 * <p>One on/off option per 1.7.10 behavior, grouped into Animation, Players &amp; Mobs and Combat. Each change is
 * saved to {@code animatium.json} and takes effect right away.</p>
 */
public class AnimatiumConfigScreen extends ConfigScreen {

    /**
     * Creates the Animatium settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public AnimatiumConfigScreen(Screen parent) {
        super(parent, "firstone-framework.animatium.", AnimatiumFeature::saveConfig, AnimatiumFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        AnimatiumConfig config = AnimatiumFeature.getConfig();

        addSection("category.animation");
        addToggle("old_sneak", config.oldSneak, value -> config.oldSneak = value);
        addToggle("old_item_drop", config.oldItemDrop, value -> config.oldItemDrop = value);
        addToggle("old_item_use", config.oldItemUse, value -> config.oldItemUse = value);
        addToggle("legacy_reequip", config.legacyReequip, value -> config.legacyReequip = value);

        addSection("category.entities");
        addToggle("legacy_entity_sync", config.legacyEntitySync, value -> config.legacyEntitySync = value);
        addToggle("legacy_mob_physics", config.legacyMobPhysics, value -> config.legacyMobPhysics = value);
        addToggle("legacy_body_rotation", config.legacyBodyRotation, value -> config.legacyBodyRotation = value);
        addToggle("legacy_sneak_pose", config.legacySneakPose, value -> config.legacySneakPose = value);
        addToggle("legacy_zombie_arms", config.legacyZombieArms, value -> config.legacyZombieArms = value);
        addToggle("legacy_mob_swing", config.legacyMobSwing, value -> config.legacyMobSwing = value);
        addToggle("legacy_hurt_tint", config.legacyHurtTint, value -> config.legacyHurtTint = value);

        addSection("category.combat");
        addToggle("no_sweep_effect", config.noSweepEffect, value -> config.noSweepEffect = value);
        addToggle("no_damage_indicator", config.noDamageIndicator, value -> config.noDamageIndicator = value);
        addToggle("no_attack_sounds", config.noAttackSounds, value -> config.noAttackSounds = value);
    }
}
