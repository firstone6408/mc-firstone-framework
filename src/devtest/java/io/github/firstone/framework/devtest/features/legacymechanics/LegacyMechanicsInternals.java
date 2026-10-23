package io.github.firstone.framework.devtest.features.legacymechanics;

import io.github.firstone.framework.features.legacymechanics.enchanting.EnchantingClues;
import io.github.firstone.framework.features.legacymechanics.enchanting.LegacyEnchanting;
import io.github.firstone.framework.features.legacymechanics.sword.SwordBlockingSupport;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.jetbrains.annotations.Nullable;

/**
 * The only place where the Legacy Mechanics tests reach into the mod's inner classes
 *
 * <p>The tests check what players do and take and only change the feature config; what the client was told about
 * sword blocking, the seed of an enchanting table's roll and the text of the levels an enchantment takes have no
 * other way in. After a refactor only this file changes. Public: the tests of the feature's
 * parts live in their own packages.</p>
 */
public final class LegacyMechanicsInternals {

    private LegacyMechanicsInternals() {}

    /**
     * Tells what the client was told about sword blocking ({@code SwordBlockingSupport.serverAllows})
     *
     * @return true if the server said swords block
     */
    public static boolean clientAllowsSwordBlocking() {
        return SwordBlockingSupport.serverAllows();
    }

    /**
     * Changes what the client believes about sword blocking, as if the server had said so
     *
     * @param allowed whether swords block
     */
    public static void setClientAllowsSwordBlocking(boolean allowed) {
        SwordBlockingSupport.setServerAllows(allowed);
    }

    /**
     * Returns the seed of the roll of an open enchanting table on the server ({@code LegacyEnchanting.Roll})
     *
     * @param menu the server's enchanting table screen
     * @return the seed, or null if the table has no roll (Legacy Enchant Reroll off)
     */
    @Nullable
    public static Integer enchantingRollSeed(EnchantmentMenu menu) {
        LegacyEnchanting.Roll roll = ((LegacyEnchanting.Holder) menu).legacymechanics$roll();
        return roll != null ? roll.seed() : null;
    }

    /**
     * Returns the text the enchanting table's tooltip shows for the levels a line takes
     * ({@code EnchantingClues.levelsText})
     *
     * @param menu the client's enchanting table screen
     * @param line the line, 0 to 2
     * @return the text, e.g. "30 Enchantment Levels"
     */
    public static String enchantLevelsText(EnchantmentMenu menu, int line) {
        String key = line == 0 ? "container.enchant.level.one" : "container.enchant.level.many";
        Object[] args = line == 0 ? new Object[0] : new Object[] {line + 1};
        return EnchantingClues.levelsText(key, args, Component.translatable(key, args), menu).getString();
    }
}
