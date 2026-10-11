package io.github.firstone.framework.features.legacyvehicles.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesConfig;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen of the Legacy Vehicles feature
 *
 * <ul>
 *   <li><b>Boats</b> — handling, ice boating, riding, splash, model, resource pack boats, paddles</li>
 * </ul>
 *
 * <p>Changes apply immediately.</p>
 */
public class LegacyVehiclesConfigScreen extends ConfigScreen {

    /**
     * Creates the Legacy Vehicles settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public LegacyVehiclesConfigScreen(Screen parent) {
        super(parent, "firstone-framework.legacy_vehicles.", LegacyVehiclesFeature::saveConfig,
            LegacyVehiclesFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        LegacyVehiclesConfig config = LegacyVehiclesFeature.getConfig();

        addSection("category.boats");
        addToggle("legacy_boat_handling", config.legacyBoatHandling, value -> config.legacyBoatHandling = value);
        addToggle("ice_boating", config.iceBoating, value -> config.iceBoating = value)
            .enabledWhen(() -> config.legacyBoatHandling);
        addToggle("legacy_boat_riding", config.legacyBoatRiding, value -> config.legacyBoatRiding = value);
        addToggle("legacy_boat_hands", config.legacyBoatHands, value -> config.legacyBoatHands = value);
        addToggle("boat_splash", config.boatSplash, value -> config.boatSplash = value);
        addToggle("legacy_boat_model", config.legacyBoatModel, value -> config.legacyBoatModel = value);
        addToggle("resource_pack_boats", config.resourcePackBoats, value -> config.resourcePackBoats = value)
            .enabledWhen(() -> config.legacyBoatModel);
        addToggle("hide_paddles", config.hidePaddles, value -> config.hidePaddles = value);
    }
}
