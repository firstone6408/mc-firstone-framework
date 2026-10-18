package io.github.firstone.framework.features.mobtweaks.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.mobtweaks.MobTweaksConfig;
import io.github.firstone.framework.features.mobtweaks.MobTweaksFeature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen of the Mob Tweaks feature
 *
 * <p>These are server rules: changes apply to singleplayer right away; a dedicated server uses its own
 * {@code mob_tweaks.json}, which the notice at the top explains.</p>
 */
public class MobTweaksConfigScreen extends ConfigScreen {

    /**
     * Creates the Mob Tweaks settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public MobTweaksConfigScreen(Screen parent) {
        super(parent, "firstone-framework.mob_tweaks.", MobTweaksFeature::saveConfig, MobTweaksFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        MobTweaksConfig config = MobTweaksFeature.getConfig();
        addServerNotice("mob_tweaks.json");

        addSection("category.enderman");
        addToggle("enderman_blocks_stay", config.endermanBlocksStay, value -> config.endermanBlocksStay = value);
    }
}
