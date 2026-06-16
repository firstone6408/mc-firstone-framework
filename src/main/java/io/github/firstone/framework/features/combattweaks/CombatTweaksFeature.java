package io.github.firstone.framework.features.combattweaks;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Main feature class of Combat Tweaks - pre-1.9 combat
 *
 * <p>This feature works on both client and server to make combat behave like versions before 1.9:</p>
 * <ul>
 *   <li>No Attack Cooldown - no damage reduction from the cooldown</li>
 *   <li>Disable Sweeping Attack - disables AOE damage around the player</li>
 *   <li>Sweeping Edge Required - the enchantment is needed to hit nearby entities</li>
 * </ul>
 *
 * <p>Each setting can be toggled from Mod Menu or the config screen</p>
 *
 * <p><b>Important — Multiplayer limitation:</b> The in-game config screen only affects
 * the local config file on the machine it runs on. In a dedicated server environment,
 * each side loads its own JSON independently via {@link #initialize()}.
 * Combat mechanics (damage, sweep) are enforced server-side, so <b>changes made through
 * the config screen on a client have no effect on the server</b>.
 * To change the server's behavior, edit
 * {@code config/firstone-framework/combat_tweaks.json} on the server directly and restart.
 * In singleplayer the client and server share the same process, so the config screen
 * works as expected.</p>
 */
public class CombatTweaksFeature implements Feature {

    /** Name of the config file used to store the settings */
    private static final String CONFIG_FILE = "combat_tweaks.json";

    /** Current config of this feature, starts with the default values */
    private static CombatTweaksConfig config = new CombatTweaksConfig();

    @Override
    public String getId() {
        return "combat_tweaks";
    }

    @Override
    public String getDisplayName() {
        return "Combat Tweaks — Legacy Combat";
    }

    @Override
    public String getDescription() {
        return "Restores pre-1.9 combat mechanics\nIncludes: No Attack Cooldown, Disable Sweeping Attack, Sweeping Edge Required";
    }

    @Override
    public void initialize() {
        config = ConfigManager.load(CONFIG_FILE, CombatTweaksConfig.class, new CombatTweaksConfig());
    }

    /**
     * Returns the current Combat Tweaks config
     *
     * <p>Used by the mixins and the config screen to read and change the settings</p>
     *
     * @return the current config, never null
     */
    public static CombatTweaksConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to its JSON file
     *
     * <p>Called after the player changes a setting in the config screen</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }
}
