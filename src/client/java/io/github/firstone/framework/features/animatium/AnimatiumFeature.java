package io.github.firstone.framework.features.animatium;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;
import io.github.firstone.framework.features.animatium.item.PackItemModels;

/**
 * Animatium — restores Minecraft 1.7.10 animations and feel on the client
 *
 * <p>Client-only feature: registered in {@code FirstOneFrameworkClient}, all setup happens in
 * {@link #initializeClient()}. Its mixins live in {@code client/mixin/animatium/} and read the
 * settings through {@link #getConfig()} every time they run, so changes apply immediately.</p>
 *
 * <p>Each part has its own folder, here and in {@code client/mixin/animatium/}: {@code sneak/} (1.7.10 sneaking:
 * eyes, camera, hitbox and pose), {@code hand/} (the first-person hand: dropping, using, re-equip and held items),
 * {@code camera/} (the hurt tilt), {@code entity/} (other entities: sync, physics, body rotation, mob swing),
 * {@code model/} (zombie arms, hurt tint, third-person items and the sword blocking arm), {@code item/} (the 1.7.10
 * item shapes both hands share, and items on the ground), {@code particle/} (the dust of a landing) and
 * {@code combat/} (1.9 combat effects, mixins only).</p>
 *
 * <p>Every behavior is a reimplementation of the 1.7.10 code (see the JavaDoc of each mixin
 * for the 1.7.10 logic it follows).</p>
 */
public class AnimatiumFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "animatium.json";

    /** Current config; starts with the defaults until {@link #initializeClient()} loads the file */
    private static AnimatiumConfig config = new AnimatiumConfig();

    @Override
    public String getId() {
        return "animatium";
    }

    /**
     * Loads {@code animatium.json}
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, AnimatiumConfig.class, new AnimatiumConfig());
        PackItemModels.registerReloadListener();
    }

    /**
     * Returns the current Animatium config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static AnimatiumConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to {@code animatium.json}
     *
     * <p>Called by the config screen after a setting changes.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to {@code animatium.json}
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new AnimatiumConfig();
        saveConfig();
    }
}
