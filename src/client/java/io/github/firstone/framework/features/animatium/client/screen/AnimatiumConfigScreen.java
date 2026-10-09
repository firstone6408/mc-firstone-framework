package io.github.firstone.framework.features.animatium.client.screen;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Settings screen of the Animatium feature
 *
 * <p>Built on the vanilla {@link OptionsSubScreen}, so it gets the vanilla scrolling list, tooltips and
 * layout. Every option is an on/off toggle; each change is saved to {@code animatium.json} immediately
 * and takes effect right away. Texts come from the language file
 * ({@code assets/firstone-framework/lang/en_us.json}).</p>
 */
public class AnimatiumConfigScreen extends OptionsSubScreen {

    /** Prefix of every translation key used by this screen */
    private static final String KEY = "firstone-framework.animatium.";

    /** Size of a category title row (same width as a full-width option) */
    private static final int HEADER_WIDTH = 310;
    private static final int HEADER_HEIGHT = 20;

    /**
     * Creates the Animatium settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public AnimatiumConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
    }

    @Override
    protected void addOptions() {
        AnimatiumConfig config = AnimatiumFeature.getConfig();

        addHeader("category.animation");
        this.list.addBig(toggle("old_sneak", config.oldSneak, value -> config.oldSneak = value));
        this.list.addBig(toggle("old_item_drop", config.oldItemDrop, value -> config.oldItemDrop = value));
        this.list.addBig(toggle("old_item_use", config.oldItemUse, value -> config.oldItemUse = value));
        this.list.addBig(toggle("legacy_reequip", config.legacyReequip, value -> config.legacyReequip = value));

        addHeader("category.entities");
        this.list.addBig(toggle("legacy_entity_sync", config.legacyEntitySync, value -> config.legacyEntitySync = value));
        this.list.addBig(toggle("legacy_mob_physics", config.legacyMobPhysics, value -> config.legacyMobPhysics = value));
        this.list.addBig(toggle("legacy_body_rotation", config.legacyBodyRotation, value -> config.legacyBodyRotation = value));
        this.list.addBig(toggle("legacy_sneak_pose", config.legacySneakPose, value -> config.legacySneakPose = value));
        this.list.addBig(toggle("legacy_zombie_arms", config.legacyZombieArms, value -> config.legacyZombieArms = value));
        this.list.addBig(toggle("legacy_mob_swing", config.legacyMobSwing, value -> config.legacyMobSwing = value));
        this.list.addBig(toggle("legacy_hurt_tint", config.legacyHurtTint, value -> config.legacyHurtTint = value));

        addHeader("category.combat");
        this.list.addBig(toggle("no_sweep_effect", config.noSweepEffect, value -> config.noSweepEffect = value));
        this.list.addBig(toggle("no_damage_indicator", config.noDamageIndicator, value -> config.noDamageIndicator = value));
        this.list.addBig(toggle("no_attack_sounds", config.noAttackSounds, value -> config.noAttackSounds = value));
    }

    /**
     * Adds a centered category title row to the list
     *
     * @param name category name used in the translation key
     */
    private void addHeader(String name) {
        this.list.addSmall(new StringWidget(HEADER_WIDTH, HEADER_HEIGHT, Component.translatable(KEY + name), this.font), null);
    }

    /**
     * Creates an on/off option that saves the config when changed
     *
     * @param name   option name used in the translation keys ({@code <name>} and {@code <name>.tooltip})
     * @param value  current value
     * @param setter writes the new value into the config
     * @return the option to add to the list
     */
    private static OptionInstance<Boolean> toggle(String name, boolean value, Consumer<Boolean> setter) {
        return OptionInstance.createBoolean(
            KEY + name,
            OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip")),
            value,
            newValue -> {
                setter.accept(newValue);
                AnimatiumFeature.saveConfig();
            }
        );
    }
}
