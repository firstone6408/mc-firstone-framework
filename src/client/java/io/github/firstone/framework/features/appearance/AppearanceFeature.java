package io.github.firstone.framework.features.appearance;

import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;
import io.github.firstone.framework.features.appearance.quit.QuitConfirmation;
import io.github.firstone.framework.features.appearance.window.WindowIcon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Feature that customizes the game's appearance (client only)
 *
 * <p>This feature only works on the client, so it lives in {@code src/client/java}
 * and is registered from {@link io.github.firstone.framework.client.FirstOneFrameworkClient}
 * instead of {@link io.github.firstone.framework.FirstOneFramework}</p>
 *
 * <p>Each part has its own folder, here and in {@code client/mixin/appearance/}:</p>
 * <ul>
 *   <li>{@code window/} — the game icon ({@link WindowIcon}, set on {@code CLIENT_STARTED} once the window is
 *       ready) and the window title ({@code WindowTitleMixin}, whenever Minecraft calls {@code Window.setTitle()})</li>
 *   <li>{@code quit/} — {@link QuitConfirmation} and its mixins ask before the window's close button quits</li>
 * </ul>
 *
 * <p>Put icons (PNG) in {@code config/firstone-framework/appearance/icons/}
 * and select one from the config screen</p>
 */
public class AppearanceFeature implements Feature {

    private static final String CONFIG_FILE = "appearance.json";
    private static AppearanceConfig config = new AppearanceConfig();

    @Override
    public String getId() {
        return "appearance";
    }

    /**
     * Initializes the Appearance feature on the client
     *
     * <p>Loads the config, creates the icons folder and registers the Fabric event
     * that applies the icon (the window title is handled by {@code WindowTitleMixin})</p>
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, AppearanceConfig.class, new AppearanceConfig());

        try {
            Files.createDirectories(WindowIcon.getIconsDir());
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to create icons directory: {}", e.getMessage());
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(WindowIcon::applyIcon);
        QuitConfirmation.register();
    }


    /**
     * Returns the current Appearance config
     *
     * @return the current config, never null
     */
    public static AppearanceConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to its JSON file
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to the config file
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new AppearanceConfig();
        saveConfig();
    }
}