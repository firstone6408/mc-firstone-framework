package io.github.firstone.framework.features.packmanager.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.packmanager.PackManagerConfig;
import io.github.firstone.framework.features.packmanager.PackManagerFeature;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen of the Pack Manager feature
 *
 * <ul>
 *   <li><b>Resource Packs Screen</b> — preset button, dragging, search box</li>
 *   <li><b>Presets</b> — asking before leaving the preset in use; opens the Resource Packs screen with the presets
 *       on top</li>
 * </ul>
 *
 * <p>Changes apply the next time a pack screen opens. Reset restores the options; the presets are kept.</p>
 */
public class PackManagerConfigScreen extends ConfigScreen {

    /**
     * Creates the Pack Manager settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public PackManagerConfigScreen(Screen parent) {
        super(parent, "firstone-framework.pack_manager.", PackManagerFeature::saveConfig,
            PackManagerFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        PackManagerConfig config = PackManagerFeature.getConfig();

        addSection("category.screen");
        addToggle("presets", config.presets, value -> config.presets = value);
        addToggle("drag_to_reorder", config.dragToReorder, value -> config.dragToReorder = value);
        addToggle("pack_search", config.packSearch, value -> config.packSearch = value);

        addSection("category.presets");
        addToggle("ask_before_leaving_preset", config.askBeforeLeavingPreset,
            value -> config.askBeforeLeavingPreset = value).enabledWhen(() -> PackManagerFeature.getConfig().presets);
        addButton("open_presets", this::openPresets).enabledWhen(() -> PackManagerFeature.getConfig().presets);
    }

    /**
     * Opens the Resource Packs screen as the Options screen does (Done saves the packs and comes back here), with
     * the presets on top of it
     */
    private void openPresets() {
        PackSelectionScreen packScreen = new PackSelectionScreen(this.minecraft.getResourcePackRepository(),
            repository -> {
                this.minecraft.options.updateResourcePacks(repository);
                this.minecraft.setScreen(this);
            }, this.minecraft.getResourcePackDirectory(), Component.translatable("resourcePack.title"));
        this.minecraft.setScreen(packScreen);
        this.minecraft.setScreen(new PresetListScreen(packScreen));
    }
}
