package io.github.firstone.framework.features.cosmetics;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds the settings of the Cosmetics feature, one group per category (each category has its own screen)
 *
 * <p>Every value in this class is saved to {@code cosmetics.json} and loaded when the game starts. The defaults
 * match the game's own effects (20 death particles, 5 item break particles, normal size, duration and speed).</p>
 *
 * <p>The images and sounds themselves are files in {@code config/firstone-framework/cosmetics/}
 * (see {@link CosmeticsFiles}). An effect without files keeps the game's own effect.</p>
 *
 * <p>This feature works on the client only</p>
 */
public class CosmeticsConfig {

    /** Death effect settings */
    public Death death = new Death();

    /** Item break settings */
    public ItemBreak itemBreak = new ItemBreak();

    /** Music disc settings */
    public MusicDiscs musicDiscs = new MusicDiscs();

    /**
     * Replaces missing groups (e.g. {@code "death": null} written by hand) with their defaults
     *
     * @return this config
     */
    public CosmeticsConfig fillMissing() {
        if (this.death == null) {
            this.death = new Death();
        }
        if (this.itemBreak == null) {
            this.itemBreak = new ItemBreak();
        }
        if (this.musicDiscs == null) {
            this.musicDiscs = new MusicDiscs();
        }
        if (this.musicDiscs.songs == null) {
            this.musicDiscs.songs = new HashMap<>();
        }
        return this;
    }

    /** Settings of the death effect (particles and sound when a dead body disappears) */
    public static class Death {

        /** Replace the death effect with the custom files */
        public boolean enabled = true;

        /** Use it for players */
        public boolean players = true;

        /** Use it for mobs */
        public boolean mobs = true;

        /** Number of particles (the game uses 20) */
        public int particleCount = 20;

        /** Particle size, 1 = the game's size */
        public float particleSize = 1.0F;

        /** How long the particles live, 1 = the game's duration */
        public float particleDuration = 1.0F;

        /** How fast the particles spread, 1 = the game's speed */
        public float particleSpeed = 1.0F;

        /** Sound volume, from 0 to 1 */
        public float soundVolume = 1.0F;

        /** Play the sound as a mono 3D sound, so its direction and distance can be heard */
        public boolean sound3d = true;

        /** Start the sound this many seconds before the body disappears (0 to 0.95, in 0.05 steps = ticks) */
        public float soundLead = 0.0F;
    }

    /** Settings of the item break effect (a tool, weapon or armor piece breaks) */
    public static class ItemBreak {

        /** Replace the item break effect with the custom files */
        public boolean enabled = true;

        /** Use it for players */
        public boolean players = true;

        /** Use it for mobs */
        public boolean mobs = true;

        /** Number of particles (the game uses 5) */
        public int particleCount = 5;

        /** Particle size, 1 = the game's size */
        public float particleSize = 1.0F;

        /** How long the particles live, 1 = the game's duration */
        public float particleDuration = 1.0F;

        /** Sound volume, from 0 to 1 */
        public float soundVolume = 1.0F;

        /** Play the sound as a mono 3D sound, so its direction and distance can be heard */
        public boolean sound3d = true;

        /** Skip this many seconds at the start of the sound (0 to 10), so its loudest part lands on the break */
        public float soundSkip = 0.0F;
    }

    /** Settings of the music discs */
    public static class MusicDiscs {

        /** Play the chosen songs */
        public boolean enabled = true;

        /** Play the songs as mono 3D sounds, heard from the jukebox like the game's discs */
        public boolean sound3d = true;

        /**
         * Song chosen for each disc: jukebox song id (e.g. {@code "minecraft:cat"}) → file name in
         * {@code music_discs/} (e.g. {@code "my song.ogg"}); a disc without an entry plays its own song
         */
        public Map<String, String> songs = new HashMap<>();
    }
}
