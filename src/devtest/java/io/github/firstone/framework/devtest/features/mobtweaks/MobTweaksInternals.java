package io.github.firstone.framework.devtest.features.mobtweaks;

import io.github.firstone.framework.features.mobtweaks.enderman.EndermanBlocks;
import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;

/**
 * The only place where the Mob Tweaks tests reach into the feature's inner classes
 *
 * <p>The tests read the world, the mobs' own state and their drops; whether an enderman's block is only shown, and
 * the per-tick check of the Nether rules (timed for its cost), have no other way in. After a refactor only this file
 * changes.</p>
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

    /**
     * Runs the check a piglin or hoglin gets after its AI each tick ({@code NetherMobs.afterAiStep}); call on the
     * server thread
     *
     * @param mob the piglin or hoglin
     */
    static void afterAiStep(Mob mob) {
        NetherMobs.afterAiStep(mob);
    }

    /**
     * Tells whether a piglin or hoglin is angry at a player ({@code NetherMobs.isAngryAt}); call on the server thread
     *
     * @param mob    the piglin or hoglin
     * @param player the player
     * @return true while it is angry at the player
     */
    static boolean isAngryAt(Mob mob, Player player) {
        return NetherMobs.isAngryAt(mob, player);
    }

    /**
     * Tells whether the Nether rules noted or saved anything on a mob ({@code NetherMobs}' attachments); call on the
     * server thread
     *
     * @param mob the mob
     * @return true if any of them is there
     */
    static boolean hasNetherData(Mob mob) {
        return mob.hasAttached(NetherMobs.STRUCTURE_MOB) || mob.hasAttached(NetherMobs.IN_BASTION)
            || mob.hasAttached(NetherMobs.ANGER);
    }
}
