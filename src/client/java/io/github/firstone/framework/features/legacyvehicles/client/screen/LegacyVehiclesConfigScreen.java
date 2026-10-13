package io.github.firstone.framework.features.legacyvehicles.client.screen;

import io.github.firstone.framework.client.screen.CategoryMenuScreen;
import io.github.firstone.framework.client.screen.ConfigCategory;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesConfig;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

import java.util.List;

import static io.github.firstone.framework.client.screen.ConfigCategory.group;
import static io.github.firstone.framework.client.screen.ConfigCategory.toggle;

/**
 * Settings screen of the Legacy Vehicles feature: its options in categories, one per kind of vehicle
 *
 * <p>Boats are the only vehicle so far; their screen shows the options in three groups:</p>
 * <ul>
 *   <li><b>Handling</b> — how a boat steers and slides on ice</li>
 *   <li><b>Riding</b> — looking around, the seat, mobs turning, hands while paddling</li>
 *   <li><b>Look &amp; Sound</b> — the boat model, resource pack boats, paddles, water splash</li>
 * </ul>
 *
 * <p>A new vehicle gets its own category here. Changes apply immediately.</p>
 */
public class LegacyVehiclesConfigScreen extends CategoryMenuScreen<LegacyVehiclesConfig> {

    /** The categories and their options, in the order they are shown */
    private static final List<ConfigCategory<LegacyVehiclesConfig>> CATEGORIES = List.of(
        ConfigCategory.grouped("boats", Items.OAK_BOAT, List.of(
            group("handling", List.of(
                toggle("legacy_boat_handling", c -> c.legacyBoatHandling, (c, v) -> c.legacyBoatHandling = v),
                toggle("ice_boating", c -> c.iceBoating, (c, v) -> c.iceBoating = v, c -> c.legacyBoatHandling))),
            group("riding", List.of(
                toggle("legacy_boat_riding", c -> c.legacyBoatRiding, (c, v) -> c.legacyBoatRiding = v),
                toggle("mobs_turn_in_boats", c -> c.mobsTurnInBoats, (c, v) -> c.mobsTurnInBoats = v),
                toggle("legacy_boat_hands", c -> c.legacyBoatHands, (c, v) -> c.legacyBoatHands = v))),
            group("look", List.of(
                toggle("legacy_boat_model", c -> c.legacyBoatModel, (c, v) -> c.legacyBoatModel = v),
                toggle("resource_pack_boats", c -> c.resourcePackBoats, (c, v) -> c.resourcePackBoats = v,
                    c -> c.legacyBoatModel),
                toggle("hide_paddles", c -> c.hidePaddles, (c, v) -> c.hidePaddles = v),
                toggle("boat_splash", c -> c.boatSplash, (c, v) -> c.boatSplash = v))))));

    /**
     * Creates the Legacy Vehicles settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public LegacyVehiclesConfigScreen(Screen parent) {
        super(parent, "firstone-framework.legacy_vehicles.", LegacyVehiclesFeature::getConfig,
            LegacyVehiclesConfig::new, LegacyVehiclesFeature::saveConfig, LegacyVehiclesFeature::resetConfig, null,
            CATEGORIES);
    }
}
