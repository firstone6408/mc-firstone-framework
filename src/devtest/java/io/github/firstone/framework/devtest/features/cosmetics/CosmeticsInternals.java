package io.github.firstone.framework.devtest.features.cosmetics;

import io.github.firstone.framework.features.cosmetics.CosmeticAudio;
import io.github.firstone.framework.features.cosmetics.CosmeticParticle;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import io.github.firstone.framework.features.cosmetics.CosmeticsPack;
import io.github.firstone.framework.features.cosmetics.client.screen.CosmeticsDeathScreen;
import io.github.firstone.framework.features.cosmetics.client.screen.CosmeticsItemBreakScreen;
import io.github.firstone.framework.features.cosmetics.client.screen.CosmeticsMusicDiscsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.ResourceLocation;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The only place where the Cosmetics tests reach into the feature's inner classes
 *
 * <p>The tests check what the player sees and hears (particles, played sounds) and only change the feature config;
 * the files folder, the custom particle class, the song event names, the category screens and the sound processing
 * have no other way in, so they go through here. After a refactor only this file changes.</p>
 */
final class CosmeticsInternals {

    private CosmeticsInternals() {}

    /**
     * Returns the folder the feature reads its files from ({@code CosmeticsFiles.root})
     *
     * @return the cosmetics folder
     */
    static Path filesRoot() {
        return CosmeticsFiles.root();
    }

    /**
     * Returns the class of the custom death and item break particles
     *
     * @return {@code CosmeticParticle}
     */
    static Class<? extends Particle> particleClass() {
        return CosmeticParticle.class;
    }

    /**
     * Returns the sound event a song file is played with ({@code CosmeticsPack.songEvent})
     *
     * @param fileName the song's file name in {@code music_discs/}
     * @return the sound event id
     */
    static String songEvent(String fileName) {
        return CosmeticsPack.songEvent(fileName).toString();
    }

    /**
     * Returns the category screens: death, item break, music discs
     *
     * @return makers of the screens, taking the parent screen
     */
    static List<Function<Screen, Screen>> categoryScreens() {
        return List.of(CosmeticsDeathScreen::new, CosmeticsItemBreakScreen::new, CosmeticsMusicDiscsScreen::new);
    }

    /**
     * Processes samples loaded at once like a sound buffer the game builds ({@code CosmeticAudio.buffer})
     *
     * @param location the loaded file
     * @param samples  16-bit samples
     * @param format   their format
     * @return what the game's buffer would be made from: the samples and their format
     */
    static Processed processBuffer(ResourceLocation location, ByteBuffer samples, AudioFormat format) {
        Processed[] result = new Processed[1];
        CosmeticAudio.buffer(location, samples, format, (processed, processedFormat) -> {
            result[0] = new Processed(processed, processedFormat);
            return null;
        });
        return result[0];
    }

    /**
     * Processes a stream like one the game opens ({@code CosmeticAudio.stream})
     *
     * @param location the opened file
     * @param stream   the stream
     * @return the stream the game would read
     */
    static AudioStream processStream(ResourceLocation location, AudioStream stream) {
        return CosmeticAudio.stream(location, stream);
    }

    /**
     * Returns the file the game opens for a sound path ({@code CosmeticAudio.file})
     *
     * @param location a sound path
     * @return the file's path
     */
    static ResourceLocation fileOf(ResourceLocation location) {
        return CosmeticAudio.file(location);
    }

    /**
     * Samples and format a sound buffer is made from
     *
     * @param samples the samples
     * @param format  their format
     */
    record Processed(ByteBuffer samples, AudioFormat format) {}
}
