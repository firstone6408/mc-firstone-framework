package io.github.firstone.framework.features.appearance.client.screen;

import io.github.firstone.framework.client.screen.CategoryMenuScreen;
import io.github.firstone.framework.client.screen.ConfigCategory;
import io.github.firstone.framework.features.appearance.AppearanceConfig;
import io.github.firstone.framework.features.appearance.AppearanceConfig.StartupBackground;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import io.github.firstone.framework.features.appearance.loading.LegacyLoading;
import io.github.firstone.framework.features.appearance.window.WindowIcon;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.firstone.framework.client.screen.ConfigCategory.action;
import static io.github.firstone.framework.client.screen.ConfigCategory.choice;
import static io.github.firstone.framework.client.screen.ConfigCategory.group;
import static io.github.firstone.framework.client.screen.ConfigCategory.toggle;

/**
 * Settings screen of the Appearance feature: its options in categories
 *
 * <ul>
 *   <li><b>Window</b> — the game icon (Minecraft's, or a PNG of the icons folder, and that folder), the window title,
 *       and asking before the window's close button quits</li>
 *   <li><b>Loading Screens</b> — the 1.7.10 startup screen, its logo (the game's, or a PNG of the logos folder),
 *       background (white or black) and bar; the 1.7.10 loading screens, their bar and the message screens without a
 *       box</li>
 * </ul>
 *
 * <p>Icon and title changes apply immediately, also when a Reset puts them back.</p>
 */
public class AppearanceConfigScreen extends CategoryMenuScreen<AppearanceConfig> {

    /** Prefix of the feature's text keys */
    private static final String KEY = "firstone-framework.appearance.";

    /** Longest window title that can be typed */
    private static final int MAX_TITLE_LENGTH = 256;

    /** The categories and their options, in the order they are shown */
    private static final List<ConfigCategory<AppearanceConfig>> CATEGORIES = List.of(
        ConfigCategory.of("window", Items.GLASS_PANE, List.of(
            choice("icon", () -> withDefault(WindowIcon.getAvailableIcons()), c -> c.selectedIcon, (c, v) -> {
                c.selectedIcon = v;
                WindowIcon.applyIcon(Minecraft.getInstance());
            }, icon -> fileText(icon, "icon.default")),
            action("icons_folder", () -> Util.getPlatform().openPath(WindowIcon.getIconsDir())),
            ConfigCategory.text("window_title", MAX_TITLE_LENGTH, c -> c.windowTitle, (c, v) -> {
                c.windowTitle = v;
                Minecraft.getInstance().updateTitle();
            }),
            toggle("confirm_quit", c -> c.confirmQuit, (c, v) -> c.confirmQuit = v))),
        ConfigCategory.grouped("loading", Items.DIRT, List.of(
            group("startup", List.of(
                toggle("legacy_startup_screen", c -> c.legacyStartupScreen, (c, v) -> c.legacyStartupScreen = v),
                choice("startup_logo", () -> withDefault(LegacyLoading.getAvailableLogos()), c -> c.startupLogo,
                    (c, v) -> c.startupLogo = v, logo -> fileText(logo, "startup_logo.default"),
                    c -> c.legacyStartupScreen),
                action("logos_folder", LegacyLoading::openLogosDir),
                choice("startup_background", () -> List.of(StartupBackground.values()),
                    AppearanceConfigScreen::startupBackground, (c, v) -> c.startupBackground = v,
                    value -> Component.translatable(KEY + "startup_background." + value.name().toLowerCase(Locale.ROOT)),
                    c -> c.legacyStartupScreen),
                toggle("legacy_startup_bar", c -> c.legacyStartupBar, (c, v) -> c.legacyStartupBar = v,
                    c -> c.legacyStartupScreen))),
            group("loading_world", List.of(
                toggle("legacy_loading_screens", c -> c.legacyLoadingScreens, (c, v) -> c.legacyLoadingScreens = v),
                toggle("legacy_loading_bar", c -> c.legacyLoadingBar, (c, v) -> c.legacyLoadingBar = v,
                    c -> c.legacyLoadingScreens),
                toggle("legacy_message_screens", c -> c.legacyMessageScreens,
                    (c, v) -> c.legacyMessageScreens = v))))));

    /**
     * Creates the Appearance settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public AppearanceConfigScreen(Screen parent) {
        super(parent, KEY, AppearanceFeature::getConfig, AppearanceConfig::new, AppearanceFeature::saveConfig, () -> {
            AppearanceFeature.resetConfig();
            WindowIcon.applyIcon(Minecraft.getInstance());
            Minecraft.getInstance().updateTitle();
        }, null, CATEGORIES);
    }

    /** The startup background of a config; an unknown value in the file shows as Light, as it is drawn */
    private static @NotNull StartupBackground startupBackground(AppearanceConfig config) {
        StartupBackground background = config.startupBackground;
        return background != null ? background : StartupBackground.LIGHT;
    }

    /** "" (the game's own) first, then the files of a folder */
    private static List<String> withDefault(List<String> files) {
        List<String> values = new ArrayList<>();
        values.add("");
        values.addAll(files);
        return values;
    }

    /** The text of a file choice: the default's text for "", else the file name in yellow */
    private static Component fileText(String file, String defaultKey) {
        return file.isEmpty() ? Component.translatable(KEY + defaultKey)
            : Component.literal(file).withStyle(ChatFormatting.YELLOW);
    }
}
