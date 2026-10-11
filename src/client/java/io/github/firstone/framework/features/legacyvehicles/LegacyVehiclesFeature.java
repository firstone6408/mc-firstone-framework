package io.github.firstone.framework.features.legacyvehicles;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.boat.BoatPushPayload;
import io.github.firstone.framework.common.config.ConfigManager;
import io.github.firstone.framework.features.legacyvehicles.boat.LegacyBoatModel;
import io.github.firstone.framework.features.legacyvehicles.boat.LegacyBoats;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Legacy Vehicles — makes vehicles drive, seat and look like Minecraft 1.7.10 (client only)
 *
 * <p>Client-only feature: registered in {@code FirstOneFrameworkClient}, all setup happens in
 * {@link #initializeClient()}. Each vehicle has its own folder ({@code boat/}), here and in
 * {@code client/mixin/legacyvehicles/}; the mixins read the settings through {@link #getConfig()} every time they run,
 * so changes apply immediately.</p>
 *
 * <ul>
 *   <li>{@link LegacyBoats} — which boats are changed, 1.7.10 handling, riding and splash</li>
 *   <li>{@link LegacyBoatModel} — the 1.7.10 boat model</li>
 *   <li>{@code LegacyBoatHands} — hands up and clicks working while paddling</li>
 * </ul>
 *
 * <p>Boats that break when they crash are a server rule: see the Legacy Mechanics feature.</p>
 */
public class LegacyVehiclesFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "legacy_vehicles.json";

    /** Current config; starts with the defaults until {@link #initializeClient()} loads the file */
    private static LegacyVehiclesConfig config = new LegacyVehiclesConfig();

    @Override
    public String getId() {
        return "legacy_vehicles";
    }

    /**
     * Loads {@code legacy_vehicles.json}, lets the boat model forget its resource pack checks on every resource
     * reload, and receives the pushes the server sends for the boat the player drives ({@link BoatPushPayload})
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, LegacyVehiclesConfig.class, new LegacyVehiclesConfig());
        LegacyBoatModel.registerReloadListener();
        ClientPlayNetworking.registerGlobalReceiver(BoatPushPayload.TYPE,
            (payload, context) -> LegacyBoats.pushFromServer(context.client(), payload));
    }

    /**
     * Returns the current Legacy Vehicles config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static LegacyVehiclesConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to {@code legacy_vehicles.json}
     *
     * <p>Called by the config screen after a setting changes.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to {@code legacy_vehicles.json}
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new LegacyVehiclesConfig();
        saveConfig();
    }
}
