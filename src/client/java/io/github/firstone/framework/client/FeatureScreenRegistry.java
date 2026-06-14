package io.github.firstone.framework.client;

import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of config screens for each feature
 *
 * <p>Maps a feature ID to its config screen,
 * so that {@link io.github.firstone.framework.client.screen.MainConfigScreen}
 * can open each feature's settings screen</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Register a feature's config screen
 * FeatureScreenRegistry.register("my_feature", parent -> new MyFeatureConfigScreen(parent));
 * }</pre>
 */
public final class FeatureScreenRegistry {

    private static final Map<String, Function<Screen, Screen>> SCREENS = new HashMap<>();

    private FeatureScreenRegistry() {}

    /**
     * Registers the config screen of a feature
     *
     * <p>Must be called from {@code ClientModInitializer} before the player opens the config screen</p>
     *
     * @param featureId feature ID, e.g. "animatium"
     * @param factory   factory function that takes the parent screen and returns a new config screen
     */
    public static void register(String featureId, Function<Screen, Screen> factory) {
        SCREENS.put(featureId, factory);
    }

    /**
     * Creates the config screen for the given feature
     *
     * @param featureId feature ID
     * @param parent    screen to return to when the config screen is closed
     * @return the feature's config screen, or {@code null} if none is registered
     */
    public static Screen createScreen(String featureId, Screen parent) {
        Function<Screen, Screen> factory = SCREENS.get(featureId);
        return factory != null ? factory.apply(parent) : null;
    }
}
