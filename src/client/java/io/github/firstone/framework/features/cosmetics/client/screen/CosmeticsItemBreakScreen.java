package io.github.firstone.framework.features.cosmetics.client.screen;

import io.github.firstone.framework.features.cosmetics.CosmeticEffects;
import io.github.firstone.framework.features.cosmetics.CosmeticsConfig;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.BooleanSupplier;

/**
 * Settings screen of the Cosmetics item break effect: on/off, players, mobs, particles and sound
 */
public class CosmeticsItemBreakScreen extends CosmeticsCategoryScreen {

    /**
     * Creates the item break settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public CosmeticsItemBreakScreen(Screen parent) {
        super(parent, "item_break", () -> CosmeticsFeature.getConfig().itemBreak = new CosmeticsConfig.ItemBreak());
    }

    @Override
    protected void addOptions() {
        CosmeticsConfig.ItemBreak config = CosmeticsFeature.getConfig().itemBreak;
        BooleanSupplier enabled = () -> config.enabled;

        addEffectStatus(CosmeticsFiles.ITEM_BREAK);
        addToggle("enabled", config.enabled, value -> config.enabled = value);
        addToggle("players", config.players, value -> config.players = value).enabledWhen(enabled);
        addToggle("mobs", config.mobs, value -> config.mobs = value).enabledWhen(enabled);

        addSection("category.particles");
        addSlider("particle_count", 1, CosmeticEffects.MAX_ITEM_BREAK_PARTICLES, 1, config.particleCount,
            CosmeticsCategoryScreen::count, value -> config.particleCount = (int) value).enabledWhen(enabled);
        addSlider("particle_size", CosmeticEffects.MIN_SCALE, CosmeticEffects.MAX_SCALE, 0.05, config.particleSize,
            CosmeticsCategoryScreen::times, value -> config.particleSize = (float) value).enabledWhen(enabled);
        addSlider("particle_duration", CosmeticEffects.MIN_SCALE, CosmeticEffects.MAX_SCALE, 0.05,
            config.particleDuration, CosmeticsCategoryScreen::times, value -> config.particleDuration = (float) value)
            .enabledWhen(enabled);

        addSection("category.sound");
        addSlider("sound_volume", 0, 1, 0.05, config.soundVolume, CosmeticsCategoryScreen::percent,
            value -> config.soundVolume = (float) value).enabledWhen(enabled);
        addToggle("sound_3d", config.sound3d, value -> config.sound3d = value).enabledWhen(enabled);
        addSlider("sound_skip", 0, CosmeticEffects.MAX_SOUND_SKIP, 0.05, config.soundSkip,
            CosmeticsCategoryScreen::seconds, value -> config.soundSkip = (float) value).enabledWhen(enabled);
    }
}
