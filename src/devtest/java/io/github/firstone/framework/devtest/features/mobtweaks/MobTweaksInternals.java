package io.github.firstone.framework.devtest.features.mobtweaks;

import io.github.firstone.framework.features.mobtweaks.enderman.EndermanBlocks;
import net.minecraft.world.entity.monster.EnderMan;

/**
 * The only place where the Mob Tweaks tests reach into the feature's inner classes
 *
 * <p>The tests read the world, the mobs' own state and their drops; whether an enderman's block is only shown has no
 * other way in. After a refactor only this file changes.</p>
 */
final class MobTweaksInternals {

    private MobTweaksInternals() {}

    /**
     * Tells whether an enderman holds its block only for show ({@code EndermanBlocks.isShownOnly}); call on the
     * server thread
     *
     * @param enderman the server's enderman
     * @return true if its block must not be placed or dropped
     */
    static boolean isShownOnly(EnderMan enderman) {
        return EndermanBlocks.isShownOnly(enderman);
    }
}
