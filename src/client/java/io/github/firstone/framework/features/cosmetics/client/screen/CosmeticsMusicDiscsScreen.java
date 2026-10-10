package io.github.firstone.framework.features.cosmetics.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.features.cosmetics.CosmeticsConfig;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Settings screen of the Cosmetics music discs: on/off, 3D sound, and the song file of every disc
 *
 * <p>Each disc cycles through "Default" (its own song) and every {@code .ogg} file of {@code music_discs/}; one
 * file can be chosen for several discs. The list has the game's discs, plus the discs of the current world's data
 * (e.g. from other mods) when opened in a world.</p>
 */
public class CosmeticsMusicDiscsScreen extends CosmeticsCategoryScreen {

    /** The game's discs, in the order of the creative inventory */
    private static final List<ResourceKey<JukeboxSong>> GAME_SONGS = List.of(
        JukeboxSongs.THIRTEEN, JukeboxSongs.CAT, JukeboxSongs.BLOCKS, JukeboxSongs.CHIRP, JukeboxSongs.FAR,
        JukeboxSongs.MALL, JukeboxSongs.MELLOHI, JukeboxSongs.STAL, JukeboxSongs.STRAD, JukeboxSongs.WARD,
        JukeboxSongs.ELEVEN, JukeboxSongs.WAIT, JukeboxSongs.PIGSTEP, JukeboxSongs.OTHERSIDE, JukeboxSongs.FIVE,
        JukeboxSongs.RELIC, JukeboxSongs.PRECIPICE, JukeboxSongs.CREATOR, JukeboxSongs.CREATOR_MUSIC_BOX);

    /**
     * Creates the music discs settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public CosmeticsMusicDiscsScreen(Screen parent) {
        super(parent, "music_discs", () -> CosmeticsFeature.getConfig().musicDiscs = new CosmeticsConfig.MusicDiscs());
    }

    @Override
    protected void addOptions() {
        CosmeticsConfig.MusicDiscs config = CosmeticsFeature.getConfig().musicDiscs;
        BooleanSupplier enabled = () -> config.enabled;

        List<String> files = new ArrayList<>();
        files.add("");
        for (Path song : CosmeticsFiles.songs()) {
            files.add(song.getFileName().toString());
        }
        addStatus(files.size() > 1 ? status("songs", files.size() - 1) : null);
        addToggle("enabled", config.enabled, value -> config.enabled = value);
        addToggle("sound_3d", config.sound3d, value -> config.sound3d = value).enabledWhen(enabled);

        addSection("category.discs");
        Component tooltip = text("disc.tooltip");
        songs().forEach((id, name) -> {
            String key = id.toString();
            // a chosen file that no longer exists shows as Default, which is what the disc plays then
            String current = files.contains(config.songs.get(key)) ? config.songs.get(key) : "";
            CycleButton<String> button = CycleButton.builder(this::songText)
                .withValues(files)
                .withInitialValue(current)
                .displayOnlyValue()
                .create(0, 0, ConfigList.WIDE_CONTROL_WIDTH, ConfigList.CONTROL_HEIGHT, name, (cycle, file) -> {
                    if (file.isEmpty()) {
                        config.songs.remove(key);
                    } else {
                        config.songs.put(key, file);
                    }
                    CosmeticsFeature.saveConfig();
                });
            addRow(new ConfigList.OptionRow(name, tooltip, button)).enabledWhen(enabled);
        });
    }

    /** Returns every disc to list: song id → name shown (e.g. "C418 - cat") */
    private Map<ResourceLocation, Component> songs() {
        Map<ResourceLocation, Component> songs = new LinkedHashMap<>();
        for (ResourceKey<JukeboxSong> song : GAME_SONGS) {
            songs.put(song.location(), Component.translatable(Util.makeDescriptionId("jukebox_song", song.location())));
        }
        if (this.minecraft.level != null) {
            this.minecraft.level.registryAccess().registry(Registries.JUKEBOX_SONG).ifPresent(registry ->
                registry.holders().forEach(holder ->
                    songs.putIfAbsent(holder.key().location(), holder.value().description())));
        }
        return songs;
    }

    /** Returns the text of a choice: "Default", or the file name without ".ogg" in yellow */
    private Component songText(String file) {
        if (file.isEmpty()) {
            return text("disc.default");
        }
        return Component.literal(file.substring(0, file.length() - ".ogg".length())).withStyle(ChatFormatting.YELLOW);
    }
}
