package io.github.firstone.framework.features.cosmetics.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.cosmetics.CosmeticEffects;
import io.github.firstone.framework.features.cosmetics.CosmeticsConfig;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Settings screen of the Cosmetics feature
 *
 * <ul>
 *   <li><b>Files</b> — open the files folder, reload the files</li>
 *   <li><b>Death Effect</b> / <b>Item Break</b> — on/off, players, mobs, particle count / size / duration
 *       (/ speed), sound volume</li>
 *   <li><b>Music Discs</b> — on/off</li>
 * </ul>
 *
 * <p>Each section shows which files are in its folder. Settings apply immediately; changed files apply after
 * Reload.</p>
 */
public class CosmeticsConfigScreen extends ConfigScreen {

    /** Color of the file status lines (gray) */
    private static final int STATUS_COLOR = 0xFFA0A0A0;

    /**
     * Creates the Cosmetics settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public CosmeticsConfigScreen(Screen parent) {
        super(parent, "firstone-framework.cosmetics.", CosmeticsFeature::saveConfig, CosmeticsFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        CosmeticsConfig config = CosmeticsFeature.getConfig();

        addSection("category.files");
        addButton("folder", () -> Util.getPlatform().openPath(CosmeticsFiles.root()));
        addButton("reload", () -> this.minecraft.reloadResourcePacks());

        addSection("category.death");
        addStatus(CosmeticsFiles.DEATH);
        addToggle("death_effect", config.deathEffect, value -> config.deathEffect = value);
        BooleanSupplier death = () -> config.deathEffect;
        addToggle("death_players", config.deathEffectPlayers, value -> config.deathEffectPlayers = value)
            .enabledWhen(death);
        addToggle("death_mobs", config.deathEffectMobs, value -> config.deathEffectMobs = value)
            .enabledWhen(death);
        addSlider("particle_count", 1, CosmeticEffects.MAX_DEATH_PARTICLES, 1, config.deathParticleCount,
            CosmeticsConfigScreen::count, value -> config.deathParticleCount = (int) value).enabledWhen(death);
        addSlider("particle_size", CosmeticEffects.MIN_SCALE, CosmeticEffects.MAX_SCALE, 0.05,
            config.deathParticleSize, CosmeticsConfigScreen::times, value -> config.deathParticleSize = (float) value)
            .enabledWhen(death);
        addSlider("particle_duration", CosmeticEffects.MIN_SCALE, CosmeticEffects.MAX_SCALE, 0.05,
            config.deathParticleDuration, CosmeticsConfigScreen::times,
            value -> config.deathParticleDuration = (float) value).enabledWhen(death);
        addSlider("particle_speed", 0, CosmeticEffects.MAX_SCALE, 0.05, config.deathParticleSpeed,
            CosmeticsConfigScreen::times, value -> config.deathParticleSpeed = (float) value).enabledWhen(death);
        addSlider("sound_volume", 0, 1, 0.05, config.deathSoundVolume, CosmeticsConfigScreen::percent,
            value -> config.deathSoundVolume = (float) value).enabledWhen(death);

        addSection("category.item_break");
        addStatus(CosmeticsFiles.ITEM_BREAK);
        addToggle("item_break", config.itemBreak, value -> config.itemBreak = value);
        BooleanSupplier itemBreak = () -> config.itemBreak;
        addToggle("item_break_players", config.itemBreakPlayers, value -> config.itemBreakPlayers = value)
            .enabledWhen(itemBreak);
        addToggle("item_break_mobs", config.itemBreakMobs, value -> config.itemBreakMobs = value)
            .enabledWhen(itemBreak);
        addSlider("particle_count", 1, CosmeticEffects.MAX_ITEM_BREAK_PARTICLES, 1, config.itemBreakParticleCount,
            CosmeticsConfigScreen::count, value -> config.itemBreakParticleCount = (int) value).enabledWhen(itemBreak);
        addSlider("particle_size", CosmeticEffects.MIN_SCALE, CosmeticEffects.MAX_SCALE, 0.05,
            config.itemBreakParticleSize, CosmeticsConfigScreen::times,
            value -> config.itemBreakParticleSize = (float) value).enabledWhen(itemBreak);
        addSlider("particle_duration", CosmeticEffects.MIN_SCALE, CosmeticEffects.MAX_SCALE, 0.05,
            config.itemBreakParticleDuration, CosmeticsConfigScreen::times,
            value -> config.itemBreakParticleDuration = (float) value).enabledWhen(itemBreak);
        addSlider("sound_volume", 0, 1, 0.05, config.itemBreakSoundVolume, CosmeticsConfigScreen::percent,
            value -> config.itemBreakSoundVolume = (float) value).enabledWhen(itemBreak);

        addSection("category.music_discs");
        int songs = CosmeticsFiles.musicDiscs().size();
        addStatusRow(songs > 0 ? translated("status.songs", songs) : null);
        addToggle("music_discs", config.musicDiscs, value -> config.musicDiscs = value);
    }

    /** Adds the line that tells which files of an effect are in its folder */
    private void addStatus(String effect) {
        List<Component> parts = new ArrayList<>();
        int frames = CosmeticsFiles.frames(effect).size();
        if (frames > 0) {
            parts.add(translated("status.frames", frames));
        }
        if (Files.isRegularFile(CosmeticsFiles.sound(effect))) {
            parts.add(translated("status.sound"));
        }
        addStatusRow(parts.isEmpty() ? null : Component.empty().append(parts.get(0))
            .append(parts.size() > 1 ? Component.literal(" + ").append(parts.get(1)) : Component.empty()));
    }

    /** Adds a gray status line: "Files: …", or "No files" when {@code files} is null */
    private void addStatusRow(@Nullable Component files) {
        Component label = files != null ? translated("status", files) : translated("status.none");
        addRow(new ConfigList.OptionRow(label, STATUS_COLOR, translated("status.tooltip"), null));
    }

    /** Returns a translated text of this screen with arguments */
    private static Component translated(String name, Object... args) {
        return Component.translatable("firstone-framework.cosmetics." + name, args);
    }

    /** Slider text of a count, e.g. "20" */
    private static Component count(double value) {
        return Component.literal(Integer.toString((int) Math.round(value)));
    }

    /** Slider text of a multiplier, e.g. "1.00x" */
    private static Component times(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.2fx", value));
    }

    /** Slider text of a volume, e.g. "100%" */
    private static Component percent(double value) {
        return Component.literal(Math.round(value * 100) + "%");
    }
}
