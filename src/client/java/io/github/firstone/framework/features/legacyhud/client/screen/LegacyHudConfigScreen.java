package io.github.firstone.framework.features.legacyhud.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.legacyhud.LegacyHudConfig;
import io.github.firstone.framework.features.legacyhud.LegacyHudFeature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen of the Legacy HUD feature
 *
 * <ul>
 *   <li><b>Status Effects</b> — hide the effect icons in the top-right corner, list the effects on the left of
 *       the inventory</li>
 * </ul>
 *
 * <p>Changes apply immediately.</p>
 */
public class LegacyHudConfigScreen extends ConfigScreen {

    /**
     * Creates the Legacy HUD settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public LegacyHudConfigScreen(Screen parent) {
        super(parent, "firstone-framework.legacy_hud.", LegacyHudFeature::saveConfig, LegacyHudFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        LegacyHudConfig config = LegacyHudFeature.getConfig();

        addSection("category.effects");
        addToggle("hide_effect_icons", config.hideEffectIcons, value -> config.hideEffectIcons = value);
        addToggle("legacy_inventory_effects", config.legacyInventoryEffects,
            value -> config.legacyInventoryEffects = value);
    }
}
