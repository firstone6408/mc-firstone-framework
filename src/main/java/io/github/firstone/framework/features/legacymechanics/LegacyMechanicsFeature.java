package io.github.firstone.framework.features.legacymechanics;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Legacy Mechanics — game rules decided by the server, as in Minecraft 1.7.10
 *
 * <p>Server-side counterpart of the 1.7.10 experience: it changes what the server calculates (knockback, the hitbox of
 * crouching players), so every player on the server gets it, with or without the mod. It is independent of the
 * client-only Animatium feature. Registered in {@code FirstOneFramework}; runs on the dedicated server and on the
 * integrated server of singleplayer.</p>
 */
public class LegacyMechanicsFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "legacy_mechanics.json";

    /** Current config, starts with the default values */
    private static LegacyMechanicsConfig config = new LegacyMechanicsConfig();

    @Override
    public String getId() {
        return "legacy_mechanics";
    }

    @Override
    public void initialize() {
        config = ConfigManager.load(CONFIG_FILE, LegacyMechanicsConfig.class, new LegacyMechanicsConfig());
    }

    /**
     * Returns the current Legacy Mechanics config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static LegacyMechanicsConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to its JSON file
     *
     * <p>Called after a setting is changed in the config screen.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to the config file
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new LegacyMechanicsConfig();
        saveConfig();
    }
}
