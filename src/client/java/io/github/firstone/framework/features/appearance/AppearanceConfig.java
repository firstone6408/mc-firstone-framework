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

    /**
     * Ask "Quit the game?" when the window's close button (X) is pressed, with Cancel as the default answer
     *
     * <p>Prevents closing the game by accident. The in-game "Quit Game" button never asks.</p>
     */
    public boolean confirmQuit = true;

    /**
     * 1.7.10 startup screen: a white screen with the logo in the middle, without a progress bar or fading, when the
     * game starts and when it loads its resources again (resource packs, F3+T)
     *
     * <p>Off: the 1.21.1 screen (red or black, Mojang Studios logo, progress bar, fading).</p>
     */
    public boolean legacyStartupScreen = true;

    /**
     * File name of the logo shown on the 1.7.10 startup screen (file name only, e.g. "mojang.png")
     *
     * <p>The file must be in {@code config/firstone-framework/appearance/logos/}. Empty ("") = the game's own logo
     * (Mojang Studios), in its red on the white screen.</p>
     */
    public String startupLogo = "";

    /**
     * Background of the 1.7.10 startup screen: {@code LIGHT} white (1.7.10), {@code DARK} black, easier on the eyes
     *
     * <p>The game's logo stays Mojang red on both. A missing or unknown value is read as {@code LIGHT}.</p>
     */
    public StartupBackground startupBackground = StartupBackground.LIGHT;

    /**
     * A progress bar on the 1.7.10 startup screen, in the style of the 1.7.10 loading bar (gray, filled in green, 100
     * by 2), under the logo where 1.21.1 has its bar
     *
     * <p>1.7.10 had no bar there, so it is off by default. Only used while {@link #legacyStartupScreen} is on.</p>
     */
    public boolean legacyStartupBar = false;

    /**
     * 1.7.10 loading screens: the dark dirt background on every loading screen (opening a world, connecting to a
     * server, changing dimension, saving), and the 1.7.10 texts: "Loading world" and "Building terrain" in place of
     * the chunk map, "Downloading terrain" when a world or dimension is received
     *
     * <p>Off: the 1.21.1 screens (blurred panorama, chunk map, portal backgrounds).</p>
     */
    public boolean legacyLoadingScreens = true;

    /**
     * The 1.7.10 progress bar (gray and green, 100 wide) under "Building terrain" while a world is made
     *
     * <p>1.7.10 had this bar but did not show it there, so it is off by default. Only used while
     * {@link #legacyLoadingScreens} is on.</p>
     */
    public boolean legacyLoadingBar = false;

    /**
     * 1.7.10 message screens: no box around the message; while a world opens or is made, "Loading world" where
     * 1.7.10 wrote it; while the world is saved on leaving, only the background, as 1.7.10 showed nothing; other
     * messages keep their text, without the box
     *
     * <p>Off: the 1.21.1 message screens ("Reading world data...", "Saving world" in a box).</p>
     */
    public boolean legacyMessageScreens = true;

    /**
     * The backgrounds the 1.7.10 startup screen can have
     */
    public enum StartupBackground {

        /** White, as in 1.7.10 */
        LIGHT,

        /** Black */
        DARK
    }
}
