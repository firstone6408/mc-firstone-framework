package io.github.firstone.framework.devtest.features.appearance;

import io.github.firstone.framework.features.appearance.AppearanceFeature;
import io.github.firstone.framework.features.appearance.QuitConfirmation;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

/**
 * The only place where the Appearance tests reach into the feature's inner classes
 *
 * <p>The tests check what the window system shows (title, icon) and what the player sees; the quit dialog and
 * applying the icon have no other way in, so they go through here. After a refactor only this file changes.</p>
 */
final class AppearanceInternals {

    private AppearanceInternals() {}

    /**
     * Tells whether the quit confirmation dialog is open ({@code QuitConfirmation.isOpen})
     *
     * @return true while it is open
     */
    static boolean quitDialogOpen() {
        return QuitConfirmation.isOpen();
    }

    /**
     * Asks the quit confirmation whether it takes over a close request ({@code QuitConfirmation.interceptClose}); call
     * on the client thread
     *
     * @param minecraft the game
     * @return true if the dialog opens instead of the game closing
     */
    static boolean interceptClose(Minecraft minecraft) {
        return QuitConfirmation.interceptClose(minecraft);
    }

    /**
     * Applies the icon chosen in the config ({@code AppearanceFeature.applyIcon}); call on the client thread
     *
     * @param minecraft the game
     */
    static void applyIcon(Minecraft minecraft) {
        AppearanceFeature.applyIcon(minecraft);
    }

    /**
     * Returns the folder the icons are chosen from ({@code AppearanceFeature.getIconsDir})
     *
     * @return the icons folder
     */
    static Path iconsDir() {
        return AppearanceFeature.getIconsDir();
    }
}
