package io.github.firstone.framework.features.animatium;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;

/**
 * Main feature class of Animatium - legacy animations like versions before 1.9
 *
 * <p>This feature works on the client only. It includes:</p>
 * <ul>
 *   <li>No Re-equip Animation - no hand dip after attacks or when the held item only changes durability</li>
 *   <li>Old Sneak Animation - the camera moves instantly when pressing Shift</li>
 *   <li>Old Item Drop Animation - no throwing gesture when dropping an item</li>
 *   <li>No Sweep Effect - hides the sweep particle and the sweep sound</li>
 *   <li>No Damage Indicator - hides the damage indicator particles</li>
 *   <li>No Attack Sounds - mutes the attack sounds</li>
 *   <li>Walk / Head / Body Rotation FPS - limits the animation frame rate (0 = off)</li>
 * </ul>
 *
 * <p>Each setting can be toggled from Mod Menu or the config screen</p>
 */
public class AnimatiumFeature implements Feature {

    /** Name of the config file used to store the settings */
    private static final String CONFIG_FILE = "animatium.json";

    /** Current config of this feature, starts with the default values */
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
        return "Legacy animations and combat feel from Minecraft pre-1.9\nIncludes: No Re-equip, Old Sneak, Old Item Drop, No Sweep Effect, No Damage Indicator, No Attack Sounds";
    }

    @Override
    public void initialize() {
        config = ConfigManager.load(CONFIG_FILE, AnimatiumConfig.class, new AnimatiumConfig());
    }

    /**
     * Returns the current Animatium config
     *
     * <p>Used by the mixins and the config screen to read and change the settings</p>
     *
     * @return the current config, never null
     */
    public static AnimatiumConfig getConfig() {
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
