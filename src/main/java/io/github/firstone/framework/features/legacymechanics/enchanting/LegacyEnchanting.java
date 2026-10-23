package io.github.firstone.framework.features.legacymechanics.enchanting;

import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Legacy Enchanting — the enchanting table of 1.7.10 on top of the game's own enchanting
 *
 * <p>The game still decides everything: the levels of the three lines, the enchantments and their chances come from
 * its own code ({@code EnchantmentMenu}, {@code EnchantmentHelper}), from one number, the seed. Only where that seed
 * comes from, what is shown and how many levels are taken change:</p>
 * <ul>
 *   <li><b>Legacy Enchant Cost</b>: enchanting takes the levels on the line (up to 30), as in 1.7.10, where 1.21.1
 *       takes 1 to 3. The level needed, the lapis lazuli used, the stats and advancements stay as in 1.21.1.</li>
 *   <li><b>Legacy Enchant Reroll</b>: 1.21.1 takes the seed from the player and keeps it until they enchant, so the
 *       same item always gets the same roll. Here every item put into the table starts a new roll with a new random
 *       seed, as 1.7.10 rolled again on every item. The seed is drawn like 1.21.1 draws the player's (any whole
 *       number, all equally likely), so the chances are exactly 1.21.1's. The clues are not sent, as 1.7.10 had
 *       none, and the seed stays on the server: the client gets another random number, drawn from another source,
 *       for the enchanting runes, so even a changed client cannot work out the seeds and the enchantments.</li>
 *   <li><b>Paid Clues</b>: players with the mod can press Show Clues ({@link #CLUE_BUTTON}); for
 *       {@link #clueLapis()} lapis lazuli and {@link #clueLevels()} levels the game's own clues of the roll are
 *       sent, one per line as in 1.21.1. They come from the roll the item gets, so the item gets the shown
 *       enchantment. Pressed again, it starts a new roll and shows its clues, for the same price.</li>
 * </ul>
 *
 * <p>Server side: a player without the mod gets the 1.7.10 table (new roll on every item, no clues, full cost). Its
 * state lives in the open table only ({@link Roll}); nothing is saved. With the options off, nothing changes.</p>
 */
public final class LegacyEnchanting {

    /** Number of the Show Clues button in the game's button packet (0 to 2 are the three lines) */
    public static final int CLUE_BUTTON = 3;

    /** Lapis lazuli the slot keeps for the enchanting itself (the most a line uses) */
    public static final int LAPIS_FOR_ENCHANTING = 3;

    /** Range of the lapis lazuli one look at the clues takes */
    public static final int MIN_CLUE_LAPIS = 1;
    public static final int MAX_CLUE_LAPIS = 10;

    /** Range of the levels one look at the clues takes */
    public static final int MIN_CLUE_LEVELS = 0;
    public static final int MAX_CLUE_LEVELS = 10;

    /**
     * Draws the seeds of the rolls; nothing it draws is ever sent, and it cannot be worked out from what a client
     * sees (the runes come from another source, the levels of the lines tell only a few bits of one seed)
     */
    private static final SecureRandom SEEDS = new SecureRandom();

    /** The rules last sent to the clients ({@link #currentRules()}), to send them again only when they change */
    private static int sentRules = -1;

    private LegacyEnchanting() {}

    /**
     * The roll of one open enchanting table
     *
     * <p>Kept by the table's screen on the server while it is open ({@code LegacyEnchantingMenuMixin}), made the
     * first time an item is put in with Legacy Enchant Reroll on.</p>
     */
    public static final class Roll {

        /** Draws the runes of the rolls (a source of its own, so no other randomness of the game changes) */
        private final RandomSource runes = RandomSource.create();

        /** The item the roll is for (the very stack in the slot) */
        private ItemStack item = ItemStack.EMPTY;

        /** The seed the game rolls the levels and enchantments with */
        private int seed;

        /** Whether the clues of this roll were paid for */
        private boolean clueShown;

        /**
         * Returns the seed of the current roll
         *
         * @return the seed the game rolls the levels and enchantments with
         */
        public int seed() {
            return this.seed;
        }
    }

    /**
     * An enchanting table screen that keeps a roll: the game's {@code EnchantmentMenu}, through its mixin
     *
     * <p>Lets other code read the roll of an open table, for example the dev tests, which compare it with
     * 1.21.1.</p>
     */
    public interface Holder {

        /**
         * Returns the table's roll
         *
         * @return the roll, or null on the client's screen or before Legacy Enchant Reroll made one
         */
        @Nullable
        Roll legacymechanics$roll();
    }

    /** Whether enchanting takes the levels on the line */
    private static boolean fullCost() {
        return LegacyMechanicsFeature.getConfig().legacyEnchantCost;
    }

    /** Whether every item put in gets a new roll without clues */
    private static boolean reroll() {
        return LegacyMechanicsFeature.getConfig().legacyEnchantReroll;
    }

    /**
     * Tells whether the Show Clues button works
     *
     * @return true if Legacy Enchant Reroll and Paid Clues are on
     */
    public static boolean paidClues() {
        LegacyMechanicsConfig config = LegacyMechanicsFeature.getConfig();
        return config.legacyEnchantReroll && config.paidEnchantClues;
    }

    /**
     * Returns the lapis lazuli one look at the clues takes
     *
     * @return the setting, kept between {@link #MIN_CLUE_LAPIS} and {@link #MAX_CLUE_LAPIS}
     */
    public static int clueLapis() {
        return Mth.clamp(LegacyMechanicsFeature.getConfig().enchantClueLapis, MIN_CLUE_LAPIS, MAX_CLUE_LAPIS);
    }

    /**
     * Returns the levels one look at the clues takes
     *
     * @return the setting, kept between {@link #MIN_CLUE_LEVELS} and {@link #MAX_CLUE_LEVELS}
     */
    public static int clueLevels() {
        return Mth.clamp(LegacyMechanicsFeature.getConfig().enchantClueLevels, MIN_CLUE_LEVELS, MAX_CLUE_LEVELS);
    }

    /**
     * Returns the levels an enchantment takes
     *
     * <p>Called when the game takes the levels of an enchantment.</p>
     *
     * @param vanillaLevels the levels 1.21.1 takes (1 to 3, the number of the line)
     * @param cost          the levels shown on the line
     * @return {@code cost} with Legacy Enchant Cost on, else {@code vanillaLevels}
     */
    public static int levelsTaken(int vanillaLevels, int cost) {
        return fullCost() ? cost : vanillaLevels;
    }

    /**
     * Starts a new roll when another item is in the table's item slot
     *
     * <p>Called on the server whenever the table's slots change, before the game works out the roll. A change of the
     * lapis lazuli keeps the roll; an item taken out and put back gets a new roll (also the very same stack, as the
     * empty slot was seen in between).</p>
     *
     * <p>Legacy Enchant Reroll turned off while the table is open: the roll is dropped and the seed goes back to the
     * player's, which 1.21.1 always has there, so the table is 1.21.1's from this change on.</p>
     *
     * @param roll      the table's roll, or null if it has none yet
     * @param item      the stack in the item slot
     * @param runesSeed the seed 1.21.1 keeps in the table (the player's), also the client's runes
     * @param player    the player using the table
     * @return the table's roll (made on first use), or null when Legacy Enchant Reroll is off
     */
    @Nullable
    public static Roll itemChanged(@Nullable Roll roll, ItemStack item, DataSlot runesSeed, Player player) {
        if (!reroll()) {
            if (roll != null) {
                runesSeed.set(player.getEnchantmentSeed());
            }
            return null;
        }
        Roll current = roll != null ? roll : new Roll();
        if (item != current.item) {
            current.item = item;
            if (!item.isEmpty()) {
                newRoll(current, runesSeed);
            }
        }
        return current;
    }

    /** Draws the seed of a new roll and new runes for it, hiding the clues until they are paid for */
    private static void newRoll(Roll roll, DataSlot runesSeed) {
        roll.seed = SEEDS.nextInt();
        roll.clueShown = false;
        runesSeed.set(roll.runes.nextInt());
    }

    /**
     * Returns the seed the game rolls the levels and enchantments with
     *
     * <p>Called where the game reads the seed, both to show the roll and to enchant, so both use the same one.</p>
     *
     * @param roll        the table's roll, or null if it has none
     * @param vanillaSeed the seed 1.21.1 uses (the player's)
     * @return the roll's seed with Legacy Enchant Reroll on, else {@code vanillaSeed}
     */
    public static int seed(@Nullable Roll roll, int vanillaSeed) {
        return roll != null && reroll() ? roll.seed : vanillaSeed;
    }

    /**
     * Takes the clues out of a roll that was not paid for, before the game sends them to the client
     *
     * @param roll        the table's roll, or null if it has none
     * @param enchantClue the enchantment shown on each line
     * @param levelClue   the level of the enchantment shown on each line
     */
    public static void hideClues(@Nullable Roll roll, int[] enchantClue, int[] levelClue) {
        if (roll != null && reroll() && !roll.clueShown) {
            Arrays.fill(enchantClue, -1);
            Arrays.fill(levelClue, -1);
        }
    }

    /**
     * Shows the clues of the roll when the player pressed Show Clues and can pay for it
     *
     * <p>Called on the server for the button packet of {@link #CLUE_BUTTON}, which the game has already checked
     * (the player's open table, within reach). The player needs an item that has a roll, {@link #clueLapis()} lapis
     * lazuli more than {@link #LAPIS_FOR_ENCHANTING} and {@link #clueLevels()} levels; in creative mode it is free,
     * as lapis lazuli is for enchanting. The price is taken with the game's own functions, then the game works out
     * the same roll again, now with its clues. Pressed when the clues are shown already, it starts a new roll.</p>
     *
     * @param roll      the table's roll, or null if it has none
     * @param player    the player who pressed the button
     * @param slots     the table's item and lapis lazuli slots
     * @param costs     the levels on the three lines
     * @param runesSeed the number the client draws the enchanting runes with
     * @return true if the clues are shown (the game then sends the changes)
     */
    public static boolean showClues(@Nullable Roll roll, Player player, Container slots, int[] costs,
                                    DataSlot runesSeed) {
        ItemStack item = slots.getItem(0);
        ItemStack lapis = slots.getItem(1);
        if (roll == null || !paidClues() || item.isEmpty() || item != roll.item || !hasRoll(costs)
            || !canPay(player, lapis)) {
            return false;
        }
        if (roll.clueShown) {
            newRoll(roll, runesSeed);
        }
        roll.clueShown = true;
        lapis.consume(clueLapis(), player);
        if (!player.hasInfiniteMaterials()) {
            player.giveExperienceLevels(-clueLevels());
        }
        // the game works out the roll again from the same seed, now sending its clues
        slots.setChanged();
        return true;
    }

    /**
     * Tells whether the table shows a roll, i.e. any line can be enchanted
     *
     * <p>Used on the server before the clues are paid for, and on the client to gray out the Show Clues button.</p>
     *
     * @param costs the levels on the three lines (0: the line cannot be chosen)
     * @return true if any line has levels
     */
    public static boolean hasRoll(int[] costs) {
        for (int cost : costs) {
            if (cost > 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player has the price of the clues and keeps enough lapis lazuli to enchant */
    private static boolean canPay(Player player, ItemStack lapis) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        return lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() >= clueLapis() + LAPIS_FOR_ENCHANTING
            && player.experienceLevel >= clueLevels();
    }

    /**
     * Sends the rules to each player who joins and again to everyone when they change
     *
     * <p>Called once by {@link LegacyMechanicsFeature#initialize()}. Each server tick compares one number, so it
     * costs next to nothing.</p>
     */
    public static void registerSync() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // a client starts without rules, the same as all off: nothing to tell it then
            if (fullCost() || paidClues()) {
                send(handler.getPlayer());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(LegacyEnchanting::sendIfChanged);
    }

    private static void sendIfChanged(MinecraftServer server) {
        int rules = currentRules();
        if (rules != sentRules) {
            sentRules = rules;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player);
            }
        }
    }

    private static void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, EnchantingRulesPayload.TYPE)) {
            ServerPlayNetworking.send(player, new EnchantingRulesPayload(fullCost(), paidClues(), clueLapis(),
                clueLevels()));
        }
    }

    /** The current rules as one number: two bits for the options, four bits for each price (at most 15) */
    private static int currentRules() {
        return (fullCost() ? 1 : 0) | (paidClues() ? 2 : 0) | clueLapis() << 2 | clueLevels() << 6;
    }
}
