package io.github.firstone.framework.features.appearance.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.appearance.AppearanceConfig;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings screen of the Appearance feature
 *
 * <ul>
 *   <li><b>Game Icon</b> — cycle through the PNGs in the icons folder (or the default icon), and open that folder</li>
 *   <li><b>Window Title</b> — type a title, or leave it empty for the default</li>
 *   <li><b>Window</b> — ask before the window's close button quits the game</li>
 * </ul>
 *
 * <p>Icon and title changes apply immediately.</p>
 */
public class AppearanceConfigScreen extends ConfigScreen {

    /** Longest window title that can be typed */
    private static final int MAX_TITLE_LENGTH = 256;

    /**
     * Creates the Appearance settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public AppearanceConfigScreen(Screen parent) {
        super(parent, "firstone-framework.appearance.", AppearanceFeature::saveConfig, () -> {
            AppearanceFeature.resetConfig();
            AppearanceFeature.applyIcon(Minecraft.getInstance());
            Minecraft.getInstance().updateTitle();
        });
    }

    @Override
    protected void addOptions() {
        AppearanceConfig config = AppearanceFeature.getConfig();

        // "" is the default icon; it comes first, then every PNG of the icons folder
        List<String> icons = new ArrayList<>();
        icons.add("");
        icons.addAll(AppearanceFeature.getAvailableIcons());

        // a selected file that no longer exists shows as Default, which is what the window has then
        String current = icons.contains(config.selectedIcon) ? config.selectedIcon : "";

        addSection("category.icon");
        addCycle("icon", icons, current, this::iconText, value -> {
            config.selectedIcon = value;
            AppearanceFeature.applyIcon(this.minecraft);
        });
        addButton("icons_folder", () -> Util.getPlatform().openPath(AppearanceFeature.getIconsDir()));

        addSection("category.title");
        addText("window_title", config.windowTitle, MAX_TITLE_LENGTH, value -> {
            config.windowTitle = value;
            this.minecraft.updateTitle();
        });

        addSection("category.window");
        addToggle("confirm_quit", config.confirmQuit, value -> config.confirmQuit = value);
    }

    /**
     * Returns the text shown on the icon button
     *
     * @param icon file name, or "" for the default icon
     * @return "Default", or the file name in yellow
     */
    private Component iconText(String icon) {
        return icon.isEmpty() ? text("icon.default") : Component.literal(icon).withStyle(ChatFormatting.YELLOW);
    }
}
