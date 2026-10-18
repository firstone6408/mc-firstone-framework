package io.github.firstone.framework.features.mobtweaks;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;
import io.github.firstone.framework.features.mobtweaks.enderman.EndermanBlocks;
import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;

/**
 * Mob Tweaks — server rules that keep mobs from spoiling the world, while they look and act as before
 *
 * <p>Server feature: registered in {@code FirstOneFramework}, set up in {@link #initialize()} (runs on the dedicated
 * server and in singleplayer). A vanilla client needs nothing: what the mobs show comes through the game's own
 * entity data.</p>
 *
 * <ul>
 *   <li>{@code enderman/} — Enderman Blocks Stay: an enderman picks up a block only for show; the block stays in the
 *       world ({@link EndermanBlocks})</li>
 *   <li>{@code nether/} — Spawn Only in the Dark and Attack Only in the Dark for piglins and hoglins, so lit Nether
 *       paths stay safe ({@link NetherMobs})</li>
 * </ul>
 */
public class MobTweaksFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "mob_tweaks.json";

    /** Current config; starts with the defaults until {@link #initialize()} loads the file */
    private static MobTweaksConfig config = new MobTweaksConfig();

    @Override
    public String getId() {
        return "mob_tweaks";
    }

    /**
     * Loads {@code mob_tweaks.json} and registers the data the mobs keep in their save
     */
    @Override
    public void initialize() {
        config = ConfigManager.load(CONFIG_FILE, MobTweaksConfig.class, new MobTweaksConfig());
        EndermanBlocks.register();
        NetherMobs.register();
    }

    /**
     * Returns the current Mob Tweaks config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static MobTweaksConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to {@code mob_tweaks.json}
     *
     * <p>Called by the config screen after a setting changes.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to {@code mob_tweaks.json}
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new MobTweaksConfig();
        saveConfig();
    }
}
