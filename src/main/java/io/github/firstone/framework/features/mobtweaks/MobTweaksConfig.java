package io.github.firstone.framework.features.mobtweaks;

/**
 * Holds the settings of the Mob Tweaks feature, saved in {@code mob_tweaks.json}
 *
 * <p>Server rules: a dedicated server uses its own file; in singleplayer the settings screen changes them at
 * once.</p>
 */
public class MobTweaksConfig {

    /**
     * Endermen pick up blocks only for show: the block stays where it was, nothing is placed when they put it down,
     * and nothing drops when they die
     *
     * <p>Off: the 1.21.1 behavior (the block is taken out of the world, placed elsewhere or dropped on death).</p>
     */
    public boolean endermanBlocksStay = true;

    /**
     * Piglins and hoglins spawn by themselves only where the block light is at most {@link #netherSpawnMaxLight}
     *
     * <p>Spawners, spawn eggs, structures (bastions) and breeding are not limited. Off: 1.21.1, where they spawn in any
     * light. Zombified piglins are never changed.</p>
     */
    public boolean netherSpawnInDark = true;

    /** The brightest block light (0 to 15) where piglins and hoglins still spawn by themselves */
    public int netherSpawnMaxLight = 10;

    /**
     * Piglins and hoglins start attacking a player they see only where the player stands in block light of at most
     * {@link #netherAttackMaxLight}
     *
     * <p>In the light they attack only while angry ({@link #netherAngerSeconds}); hitting one still calls its kind as
     * in 1.21.1. Mobs of a bastion, piglin brutes and zombified piglins are never changed. Off: 1.21.1, where they
     * attack on sight in any light.</p>
     */
    public boolean netherAttackInDark = true;

    /**
     * The brightest block light (0 to 15) at the player (the brighter of their feet and eyes) where piglins and
     * hoglins still attack on sight
     */
    public int netherAttackMaxLight = 10;

    /**
     * How long, in seconds, a piglin or hoglin stays angry at a player in the light: after the player hit it or one of
     * its kind, after a piglin saw them open a chest or mine gold, or after it last chased them in the dark
     *
     * <p>Only used with {@link #netherAttackInDark}; in the dark they attack on sight anyway.</p>
     */
    public int netherAngerSeconds = 30;
}
