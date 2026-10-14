package io.github.firstone.framework.features.legacyhud;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Legacy HUD — makes the in-game HUD look like Minecraft 1.7.10 (client only)
 *
 * <p>Client-only feature: registered in {@code FirstOneFrameworkClient}, all setup happens in
 * {@link #initializeClient()}. Its mixins live in {@code client/mixin/legacyhud/} and read the
 * settings through {@link #getConfig()} every time they run, so changes apply immediately.</p>
 *
 * <ul>
 *   <li>{@code HideEffectIconsMixin} — no status effect icons in the top-right corner (1.7.10 showed effects
 *       only in the inventory)</li>
 *   <li>{@code LegacyInventoryPositionMixin}, {@code LegacyCreativePositionMixin}, {@code LegacyInventoryEffectsMixin}
 *       — effect list on the left of the inventory window ({@link LegacyInventoryEffects})</li>
 * </ul>
 */
public class LegacyHudFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "legacy_hud.json";

    /** Current config; starts with the defaults until {@link #initializeClient()} loads the file */
    private static LegacyHudConfig config = new LegacyHudConfig();

    @Override
    public String getId() {
        return "legacy_hud";
    }

    /**
     * Loads {@code legacy_hud.json}
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, LegacyHudConfig.class, new LegacyHudConfig());
    }

    /**
     * Returns the current Legacy HUD config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static LegacyHudConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to {@code legacy_hud.json}
     *
     * <p>Called by the config screen after a setting changes.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to {@code legacy_hud.json}
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new LegacyHudConfig();
        saveConfig();
    }
}
