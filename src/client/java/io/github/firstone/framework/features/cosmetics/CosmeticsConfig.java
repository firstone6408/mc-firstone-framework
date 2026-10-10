package io.github.firstone.framework.features.cosmetics;

/**
 * Holds the settings of the Cosmetics feature
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

    /** Replace the death effect (particles when a body disappears) with the custom files */
    public boolean deathEffect = true;

    /** Use the custom death effect for players */
    public boolean deathEffectPlayers = true;

    /** Use the custom death effect for mobs */
    public boolean deathEffectMobs = true;

    /** Number of death particles (the game uses 20) */
    public int deathParticleCount = 20;

    /** Size of the death particles, 1 = the game's size */
    public float deathParticleSize = 1.0F;

    /** How long the death particles live, 1 = the game's duration */
    public float deathParticleDuration = 1.0F;

    /** How fast the death particles spread, 1 = the game's speed */
    public float deathParticleSpeed = 1.0F;

    /** Volume of the death sound, from 0 to 1 */
    public float deathSoundVolume = 1.0F;

    /** Replace the effect of a breaking item (tool, weapon or armor) with the custom files */
    public boolean itemBreak = true;

    /** Use the custom item break effect for players */
    public boolean itemBreakPlayers = true;

    /** Use the custom item break effect for mobs */
    public boolean itemBreakMobs = true;

    /** Number of item break particles (the game uses 5) */
    public int itemBreakParticleCount = 5;

    /** Size of the item break particles, 1 = the game's size */
    public float itemBreakParticleSize = 1.0F;

    /** How long the item break particles live, 1 = the game's duration */
    public float itemBreakParticleDuration = 1.0F;

    /** Volume of the item break sound, from 0 to 1 */
    public float itemBreakSoundVolume = 1.0F;

    /** Play the custom songs of the music discs that have a file */
    public boolean musicDiscs = true;
}
