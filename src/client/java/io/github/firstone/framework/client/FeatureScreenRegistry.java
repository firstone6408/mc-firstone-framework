package io.github.firstone.framework.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry of the config screen of each feature, with what the main screen shows for it
 *
 * <p>{@link io.github.firstone.framework.client.screen.MainConfigScreen} shows one tile per registered feature,
 * grouped by {@link Side}, with the given item as icon; clicking the tile opens the feature's screen. A feature
 * without an entry here is not shown.</p>
 *
 * <p>Usage (in {@code FirstOneFrameworkClient}):</p>
 * <pre>{@code
 * FeatureScreenRegistry.register("falling_tree", Items.IRON_AXE, Side.SERVER, FallingTreeConfigScreen::new);
 * }</pre>
 */
public final class FeatureScreenRegistry {

    /** Where a feature works, shown as the group the feature is listed in */
    public enum Side {
        /** Changes only the player's own game; works on any server */
        CLIENT,
        /** Changes game rules run by the server (singleplayer, or a server that has the mod) */
        SERVER
    }

    /**
     * What is registered for one feature
     *
     * @param icon    item drawn on the feature's tile
     * @param side    group the feature is listed in
     * @param factory creates the config screen from the parent screen
     */
    public record Entry(Item icon, Side side, Function<Screen, Screen> factory) {}

    private static final Map<String, Entry> ENTRIES = new HashMap<>();

    private FeatureScreenRegistry() {}

    /**
     * Registers the config screen of a feature
     *
     * <p>Must be called from {@code ClientModInitializer} before the player opens the config screen.</p>
     *
     * @param featureId feature ID, e.g. "animatium"
     * @param icon      item drawn on the feature's tile, e.g. {@code Items.ARMOR_STAND}
     * @param side      where the feature works (the group it is listed in)
     * @param factory   creates the config screen from the parent screen, e.g. {@code AnimatiumConfigScreen::new}
     */
    public static void register(String featureId, Item icon, Side side, Function<Screen, Screen> factory) {
        ENTRIES.put(featureId, new Entry(icon, side, factory));
    }

    /**
     * Returns what is registered for a feature
     *
     * @param featureId feature ID
     * @return the entry, or {@code null} if the feature has no config screen
     */
    @Nullable
    public static Entry get(String featureId) {
        return ENTRIES.get(featureId);
    }

    /**
     * Creates the config screen for the given feature
     *
     * @param featureId feature ID
     * @param parent    screen to return to when the config screen is closed
     * @return the feature's config screen, or {@code null} if none is registered
     */
    @Nullable
    public static Screen createScreen(String featureId, Screen parent) {
        Entry entry = ENTRIES.get(featureId);
        return entry != null ? entry.factory().apply(parent) : null;
    }
}
