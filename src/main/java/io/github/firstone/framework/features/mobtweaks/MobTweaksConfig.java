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
}
