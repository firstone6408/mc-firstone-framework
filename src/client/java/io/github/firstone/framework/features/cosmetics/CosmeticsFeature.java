package io.github.firstone.framework.features.cosmetics;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Cosmetics — custom looks and sounds chosen by the player (client only, not related to 1.7.10)
 *
 * <p>Client-only feature: registered in {@code FirstOneFrameworkClient}, all setup happens in
 * {@link #initializeClient()}. Images and sounds are files the player puts in
 * {@code config/firstone-framework/cosmetics/} ({@link CosmeticsFiles}); the game reads them through a built-in
 * resource pack ({@link CosmeticsPack}), so nothing is copied and no file ships with the mod. Sounds can be turned
 * into mono 3D sounds and trimmed while they load ({@link CosmeticAudio}).</p>
 *
 * <ul>
 *   <li>Death effect — custom particles and sound when a dead body disappears ({@link CosmeticEffects})</li>
 *   <li>Item break — custom particles and sound when a tool, weapon or armor piece breaks</li>
 *   <li>Music discs — a song file chosen for each disc</li>
 * </ul>
 *
 * <p>The mixins live in {@code client/mixin/cosmetics/} and read the settings through {@link #getConfig()}
 * every time they run, so changes apply immediately. Changed files apply after a resource reload.</p>
 */
public class CosmeticsFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "cosmetics.json";

    /** Current config; starts with the defaults until {@link #initializeClient()} loads the file */
    private static CosmeticsConfig config = new CosmeticsConfig();

    @Override
    public String getId() {
        return "cosmetics";
    }

    /**
     * Loads {@code cosmetics.json} and creates the folders for the files
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, CosmeticsConfig.class, new CosmeticsConfig()).fillMissing();
        CosmeticsFiles.createFolders();
    }

    /**
     * Returns the current Cosmetics config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static CosmeticsConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to {@code cosmetics.json}
     *
     * <p>Called by the config screen after a setting changes.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to {@code cosmetics.json}
     *
     * <p>Called by the Reset button of the config screen. The files are not touched.</p>
     */
    public static void resetConfig() {
        config = new CosmeticsConfig();
        saveConfig();
    }
}
