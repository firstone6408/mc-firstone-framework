package io.github.firstone.framework.features.packmanager;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;
import io.github.firstone.framework.features.packmanager.drag.PackDrag;
import io.github.firstone.framework.features.packmanager.preset.PresetStore;

/**
 * Pack Manager — presets, dragging and search for the resource pack screens (client only)
 *
 * <p>Client-only feature: registered in {@code FirstOneFrameworkClient}, all setup happens in
 * {@link #initializeClient()}. It only arranges the pack lists of the vanilla screens through the screens' own
 * functions (select, unselect, move up, move down), like a player clicking; the game itself saves the choice and
 * loads the resources when Done is pressed.</p>
 *
 * <ul>
 *   <li>{@code preset/} — saved pack lists ({@link PresetStore}), their status, file checks and the question before
 *       leaving the preset in use; screens in {@code client/screen/}</li>
 *   <li>{@code drag/} — dragging packs ({@link PackDrag}, {@code PackListDragMixin})</li>
 *   <li>{@link PackLists} — reads, arranges and filters (search) the pack screens' lists through their own
 *       functions</li>
 *   <li>mixins: {@code PackScreenMixin} (search box, preset button, Cancel of the preset's pack screen, the
 *       screen's parts), {@code PackEntryMixin} (no moving while searching, the question before leaving a preset)</li>
 * </ul>
 */
public class PackManagerFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "pack_manager.json";

    /** Current config; starts with the defaults until {@link #initializeClient()} loads the file */
    private static PackManagerConfig config = new PackManagerConfig();

    @Override
    public String getId() {
        return "pack_manager";
    }

    /**
     * Loads {@code pack_manager.json} and the presets, and registers the screen events of dragging
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, PackManagerConfig.class, new PackManagerConfig());
        PresetStore.load();
        PackDrag.register();
    }

    /**
     * Returns the current Pack Manager config
     *
     * <p>Used by the mixins, the screens and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static PackManagerConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to {@code pack_manager.json}
     *
     * <p>Called by the config screen after a setting changes.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to {@code pack_manager.json}; the presets are kept
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new PackManagerConfig();
        saveConfig();
    }
}
