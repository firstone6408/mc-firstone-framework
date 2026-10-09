package io.github.firstone.framework.features.animatium;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Animatium — restores Minecraft 1.7.10 animations and feel on the client
 *
 * <p>Client-only feature: registered in {@code FirstOneFrameworkClient}, all setup happens in
 * {@link #initializeClient()}. Its mixins live in {@code client/mixin/animatium/} and read the
 * settings through {@link #getConfig()} every time they run, so changes apply immediately.</p>
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

    @Override
    public String getDisplayName() {
        return "Animatium — Legacy Animations";
    }

    @Override
    public String getDescription() {
        return "Minecraft 1.7.10 animations and feel (client only)";
    }

    /**
     * Loads {@code animatium.json}
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, AnimatiumConfig.class, new AnimatiumConfig());
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
}
