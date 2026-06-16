package io.github.firstone.framework.features.appearance;

/**
 * Holds the settings of the Appearance feature
 *
 * <p>Every value in this class is saved to a JSON file and loaded when the game starts</p>
 *
 * <p>This feature works on the client only</p>
 */
public class AppearanceConfig {

    /**
     * File name of the selected icon (file name only, e.g. "myicon.png")
     *
     * <p>The file must be in {@code config/firstone-framework/appearance/icons/}</p>
     * <p>Empty ("") = use Minecraft's default icon</p>
     */
    public String selectedIcon = "";

    /**
     * Custom game window title
     *
     * <p>Empty ("") = use Minecraft's default title</p>
     */
    public String windowTitle = "";
}
