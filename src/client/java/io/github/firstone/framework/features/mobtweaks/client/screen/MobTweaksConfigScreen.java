package io.github.firstone.framework.features.mobtweaks.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.mobtweaks.MobTweaksConfig;
import io.github.firstone.framework.features.mobtweaks.MobTweaksFeature;
import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen of the Mob Tweaks feature
 *
 * <p>These are server rules: changes apply to singleplayer right away; a dedicated server uses its own
 * {@code mob_tweaks.json}, which the notice at the top explains. A light limit is grayed out while its rule is
 * off.</p>
 */
public class MobTweaksConfigScreen extends ConfigScreen {

    /** Range and step of the anger time slider, in seconds */
    private static final int MIN_ANGER_SECONDS = 5;
    private static final int MAX_ANGER_SECONDS = 120;
    private static final int ANGER_STEP = 5;

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

        addSection("category.nether");
        addToggle("nether_spawn_in_dark", config.netherSpawnInDark, value -> config.netherSpawnInDark = value);
        addSlider("nether_spawn_max_light", 0, NetherMobs.MAX_LIGHT, 1, config.netherSpawnMaxLight,
            MobTweaksConfigScreen::light, value -> config.netherSpawnMaxLight = (int) value)
            .enabledWhen(() -> config.netherSpawnInDark);
        addToggle("nether_attack_in_dark", config.netherAttackInDark, value -> config.netherAttackInDark = value);
        addSlider("nether_attack_max_light", 0, NetherMobs.MAX_LIGHT, 1, config.netherAttackMaxLight,
            MobTweaksConfigScreen::light, value -> config.netherAttackMaxLight = (int) value)
            .enabledWhen(() -> config.netherAttackInDark);
        addSlider("nether_anger_seconds", MIN_ANGER_SECONDS, MAX_ANGER_SECONDS, ANGER_STEP, config.netherAngerSeconds,
            MobTweaksConfigScreen::seconds, value -> config.netherAngerSeconds = (int) value)
            .enabledWhen(() -> config.netherAttackInDark);
    }

    /** The text of the anger time slider: "30 s" */
    private static Component seconds(double value) {
        return Component.translatable("firstone-framework.mob_tweaks.seconds_value", (int) value);
    }

    /** The text of a light limit slider: "Up to 10" */
    private static Component light(double value) {
        return Component.translatable("firstone-framework.mob_tweaks.light_value", (int) value);
    }
}
