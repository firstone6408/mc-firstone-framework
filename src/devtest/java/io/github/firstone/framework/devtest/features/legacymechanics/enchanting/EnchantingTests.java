package io.github.firstone.framework.devtest.features.legacymechanics.enchanting;

import com.mojang.authlib.GameProfile;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.features.legacymechanics.LegacyMechanicsInternals;
import io.github.firstone.framework.devtest.mixin.PlayerAccessor;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tests of Legacy Enchanting (cost, rolls and paid clues of the enchanting table), part of the Legacy Mechanics tests
 *
 * <p>The table stands among 15 bookshelves (so line 3 always costs 30), or fewer where the number matters. Items
 * are put into its slots on the server, as if the player had placed them, or by the player's own clicks where the
 * way matters; the lines and the Show Clues button are pressed from the client with the game's own button packet,
 * and the "?" button through its own press. "Same as 1.21.1" is proven by A/B: the seed the feature drew is given to
 * 1.21.1 as the player's seed, and the levels, clues and the enchanted item must be the same.</p>
 */
public final class EnchantingTests {

    /** Where the table stands (site coordinates); bookshelves stand around it, 2 blocks away, 2 high */
    private static final int TABLE_X = 4;
    private static final int TABLE_Z = 4;

    /** Where the player stands, outside the bookshelves, facing the table (north) */
    private static final double PLAYER_X = 4.5;
    private static final double PLAYER_Z = 8.5;

    /** The first hotbar slot of the enchanting screen (after the table's 2 slots and the 27 inventory slots) */
    private static final int HOTBAR = 29;

    /** The game's number of the Show Clues button and the default price of a look at the clues */
    private static final int CLUE_BUTTON = 3;
    private static final int CLUE_LAPIS = 3;
    private static final int CLUE_LEVELS = 3;

    /** Bookshelves the game counts at most, and the levels of line 3 with them */
    private static final int SHELVES = 15;
    private static final int TOP_COST = 30;

    /** Items enchanted in the A/B test: a tool, a weapon, a book (which loses one enchantment) */
    private static final Item[] ITEMS = {Items.DIAMOND_PICKAXE, Items.IRON_SWORD, Items.BOOK};

    private EnchantingTests() {}

    /**
     * Returns the enchanting tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Enchanting: with the seed the feature drew, levels, clues and enchantments are 1.21.1's "
                + "(pickaxe, sword, book; 0, 1, 7 and 15 bookshelves)", EnchantingTests::sameAsVanilla),
            TestCase.of("Enchant reroll: every item put in gets a new roll and runes, more lapis keeps it, the "
                + "player's seed stays; off: the same roll every time (1.21.1)", EnchantingTests::reroll),
            TestCase.of("Enchant reroll: the client gets no clues; off: the clues of 1.21.1", EnchantingTests::noClues),
            TestCase.of("Enchant clues: the button needs 6 lapis and 3 levels, takes 3 and 3 and shows the clues of "
                + "the same roll; pressed again, a new roll for the same price", EnchantingTests::paidClues),
            TestCase.of("Enchant clues: the item gets the enchantment the clue shows, on every line",
                EnchantingTests::clueComesTrue),
            TestCase.of("Enchant clues: taking the item out or closing the table hides the clues again; creative pays "
                + "nothing", EnchantingTests::cluesGone),
            TestCase.of("Enchant clues: off (or reroll off) no button; hand-edited prices are kept in their range",
                EnchantingTests::buttonRules),
            TestCase.of("Enchant cost: line 3 (30) takes 30 levels and 3 lapis and its tooltip says 30; the level "
                + "needed stays; off: 3 levels (1.21.1)", EnchantingTests::fullCost),
            TestCase.of("Enchanting: nothing new in the player's saved data; time of a slot change (on and off)",
                EnchantingTests::nothingSaved),
            TestCase.of("Enchanting: all options off is 1.21.1 itself (player's seed and runes, clues, 3 levels, new "
                + "seed after enchanting, no button)", EnchantingTests::allOff),
            TestCase.of("Enchant reroll: items put in by every kind of click (click, right-click, shift-click, cursor "
                + "swap, number keys, the same stack back) get a new roll; lapis by shift-click keeps it; nothing "
                + "is taken", EnchantingTests::realClicks),
            TestCase.of("Enchanting: options changed while the table is open take effect on its next change",
                EnchantingTests::changedWhileOpen),
            TestCase.of("Enchant clues: items that cannot be enchanted, a wrong screen number and quick double "
                + "presses take nothing wrongly", EnchantingTests::oddPresses),
            TestCase.of("Enchant clues: exactly enough lapis and levels pays; extreme prices; lapis taken out after "
                + "paying keeps the clues without paying again", EnchantingTests::priceEdges),
            TestCase.of("Enchant cost: exactly 30 levels go to 0 and the level progress stays (also off: 3 levels)",
                EnchantingTests::exactLevels),
            TestCase.of("Enchant clues: two players at two tables roll and pay on their own",
                EnchantingTests::twoPlayers),
            TestCase.of("Enchanting: five players at one table at once: own rolls, own clues, own payments, an "
                + "enchanting by one changes nobody else's; 20 rolls in one tick all differ", EnchantingTests::crowd),
            TestCase.of("Enchanting: the table broken or left while open closes it, nothing is lost or paid, a later "
                + "press does nothing; a spectator's press does nothing", EnchantingTests::closedByWorld),
            TestCase.of("Enchant clues: absurd hand-edited prices (largest and smallest numbers) are kept in range "
                + "on the server and the client", EnchantingTests::absurdPrices),
            TestCase.of("Enchant reroll: the seeds cannot be worked out from the runes a client sees (cracking them "
                + "like java.util.Random finds no seed; the same attack does crack one shared source)",
                EnchantingTests::seedsSecret));
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    /** Turns all three options on or off */
    private static void options(boolean on) {
        config().legacyEnchantCost = on;
        config().legacyEnchantReroll = on;
        config().paidEnchantClues = on;
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private static void sameAsVanilla(TestContext t) {
        int rolls = 0;
        int sameCosts = 0;
        int sameClues = 0;
        int sameItems = 0;
        int enchanted = 0;
        List<String> different = new ArrayList<>();
        for (int shelves : new int[] {0, 1, 7, SHELVES}) {
            place(t, shelves);
            for (Item item : ITEMS) {
                for (int round = 0; round < 2; round++) {
                    options(true);
                    open(t);
                    setLevels(t, 100);
                    put(t, new ItemStack(item), 64);
                    Roll on = serverRoll(t);
                    t.require(on.seed() != null, "The table has no roll");
                    press(t, CLUE_BUTTON);
                    Roll onClues = serverRoll(t);
                    int line = line(on.costs(), round);
                    press(t, line);
                    ItemStack onItem = slotItem(t);
                    close(t);

                    options(false);
                    int seed = on.seed();
                    t.withServerPlayer(player -> ((PlayerAccessor) player).devtest$setEnchantmentSeed(
                        seed));
                    open(t);
                    setLevels(t, 100);
                    put(t, new ItemStack(item), 64);
                    Roll off = serverRoll(t);
                    press(t, line);
                    ItemStack offItem = slotItem(t);
                    close(t);

                    rolls++;
                    sameCosts += Arrays.equals(on.costs(), off.costs()) ? 1 : 0;
                    sameClues += Arrays.equals(onClues.clues(), off.clues())
                        && Arrays.equals(onClues.clueLevels(), off.clueLevels()) ? 1 : 0;
                    boolean sameItem = ItemStack.matches(onItem, offItem);
                    sameItems += sameItem ? 1 : 0;
                    enchanted += EnchantmentHelper.getEnchantmentsForCrafting(onItem).isEmpty() ? 0 : 1;
                    if (!sameItem) {
                        different.add(onItem + " / " + offItem);
                    }
                }
            }
        }
        t.note("rolls compared", rolls);
        t.check("the levels of the three lines are 1.21.1's", sameCosts).near(rolls, 0);
        t.check("the clues of the three lines are 1.21.1's", sameClues).near(rolls, 0);
        t.check("every item was enchanted", enchanted).near(rolls, 0);
        t.check("the enchanted items are 1.21.1's " + different, sameItems).near(rolls, 0);
    }

    private static void reroll(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 100);
        int playerSeed = t.onServer(level -> player(t, level).getEnchantmentSeed());
        Set<Integer> seeds = new HashSet<>();
        Set<String> costs = new HashSet<>();
        Set<Integer> runes = new HashSet<>();
        int rolls = 10;
        for (int i = 0; i < rolls; i++) {
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
            Roll roll = serverRoll(t);
            seeds.add(roll.seed());
            costs.add(Arrays.toString(roll.costs()));
            runes.add(t.onClientThread(() -> clientMenu(t).getEnchantmentSeed()));
            takeOut(t);
        }
        t.check("every item put in gets a new seed", seeds.size()).near(rolls, 0);
        t.check("the levels of the lines change between rolls", costs.size()).atLeast(2);
        t.check("the runes change between rolls (client)", runes.size()).atLeast(rolls / 2.0);

        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
        Roll before = serverRoll(t);
        setLapis(t, 20);
        Roll after = serverRoll(t);
        t.check("more lapis keeps the roll", after.seed() != null && after.seed().equals(before.seed())
            && Arrays.equals(after.costs(), before.costs())).isTrue();

        ItemStack same = new ItemStack(Items.DIAMOND_PICKAXE);
        putStack(t, same);
        Roll first = serverRoll(t);
        takeOut(t);
        putStack(t, same);
        t.check("the same stack taken out and put back gets a new roll",
            !first.seed().equals(serverRoll(t).seed())).isTrue();
        int seedAfter = t.onServer(level -> player(t, level).getEnchantmentSeed());
        t.check("the player's own seed (saved as XpSeed) is unchanged", seedAfter).near(playerSeed, 0);
        close(t);

        config().legacyEnchantReroll = false;
        open(t);
        Set<String> vanilla = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
            Roll roll = serverRoll(t);
            t.require(roll.seed() == null, "Reroll off still made a roll");
            vanilla.add(Arrays.toString(roll.costs()));
            takeOut(t);
        }
        t.check("off: the same levels every time (1.21.1)", vanilla.size()).near(1, 0);
        close(t);
    }

    private static void noClues(TestContext t) {
        place(t, SHELVES);
        for (boolean on : new boolean[] {true, false}) {
            config().legacyEnchantReroll = on;
            open(t);
            put(t, new ItemStack(Items.DIAMOND_SWORD), 3);
            int[] clues = t.onClientThread(() -> clientMenu(t).enchantClue.clone());
            int lines = linesWithCost(t.onClientThread(() -> clientMenu(t).costs.clone()));
            int shown = countShown(clues);
            if (on) {
                t.check("on: clues on the client", shown).near(0, 0);
                t.check("on: clues kept on the server", countShown(serverRoll(t).clues())).near(0, 0);
            } else {
                t.check("off: a clue for every line (1.21.1)", shown).near(lines, 0);
            }
            close(t);
        }
    }

    private static void paidClues(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 10);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 5);
        t.check("the \"?\" button is there", clueButton(t) != null).isTrue();
        t.check("5 lapis: the button is grayed out", buttonActive(t)).isFalse();
        press(t, CLUE_BUTTON);
        t.check("5 lapis: a packet anyway takes no lapis", lapis(t)).near(5, 0);
        t.check("5 lapis: a packet anyway takes no levels", levels(t)).near(10, 0);
        t.check("5 lapis: no clues on the client", countShown(clientClues(t))).near(0, 0);

        setLapis(t, 6);
        Roll before = serverRoll(t);
        t.check("6 lapis and 10 levels: the button can be pressed", buttonActive(t)).isTrue();
        pressClueButton(t);
        Roll after = serverRoll(t);
        int lines = linesWithCost(after.costs());
        t.check("pressed: 3 lapis taken", lapis(t)).near(6 - CLUE_LAPIS, 0);
        t.check("pressed: 3 levels taken", levels(t)).near(10 - CLUE_LEVELS, 0);
        t.check("pressed: a clue for every line on the client", countShown(clientClues(t))).near(lines, 0);
        t.check("pressed: the same roll (seed and levels)", after.seed().equals(before.seed())
            && Arrays.equals(after.costs(), before.costs())).isTrue();

        t.check("3 lapis left: the button is grayed out", buttonActive(t)).isFalse();
        press(t, CLUE_BUTTON);
        t.check("3 lapis left: a packet anyway takes nothing", lapis(t) == 3 && levels(t) == 7).isTrue();

        setLapis(t, 9);
        pressClueButton(t);
        Roll again = serverRoll(t);
        t.check("pressed again: a new roll", !again.seed().equals(after.seed())).isTrue();
        t.check("pressed again: 3 lapis and 3 levels taken again", lapis(t) == 6 && levels(t) == 4).isTrue();
        t.check("pressed again: the new roll's clues on the client", countShown(clientClues(t)))
            .near(linesWithCost(again.costs()), 0);

        setLevels(t, 2);
        setLapis(t, 9);
        t.check("2 levels: the button is grayed out", buttonActive(t)).isFalse();
        press(t, CLUE_BUTTON);
        t.check("2 levels: a packet anyway takes nothing", lapis(t) == 9 && levels(t) == 2).isTrue();
        t.saveScreenshot("enchanting_clue_button");
        close(t);
    }

    private static void clueComesTrue(TestContext t) {
        place(t, SHELVES);
        open(t);
        int tried = 0;
        int cameTrue = 0;
        List<String> wrong = new ArrayList<>();
        for (int round = 0; round < 9; round++) {
            Item item = ITEMS[round % ITEMS.length];
            int line = round % 3;
            setLevels(t, 100);
            put(t, new ItemStack(item), 64);
            press(t, CLUE_BUTTON);
            Roll roll = serverRoll(t);
            if (roll.costs()[line] <= 0 || roll.clues()[line] < 0) {
                takeOut(t);
                continue;
            }
            press(t, line);
            int clue = roll.clues()[line];
            int clueLevel = roll.clueLevels()[line];
            int got = t.onServer(server -> {
                IdMap<Holder<Enchantment>> ids = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .asHolderIdMap();
                Holder<Enchantment> enchantment = ids.byId(clue);
                ItemStack enchanted = menu(t, server).getSlot(0).getItem();
                return enchantment == null ? -1
                    : EnchantmentHelper.getEnchantmentsForCrafting(enchanted).getLevel(enchantment);
            });
            tried++;
            if (got == clueLevel) {
                cameTrue++;
            } else {
                wrong.add(item + " line " + (line + 1) + ": clue level " + clueLevel + ", got " + got);
            }
            takeOut(t);
        }
        close(t);
        t.check("lines tried", tried).atLeast(6);
        t.check("the item got the enchantment and level of the clue " + wrong, cameTrue).near(tried, 0);
    }

    private static void cluesGone(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        press(t, CLUE_BUTTON);
        t.check("clues shown after paying", countShown(clientClues(t))).atLeast(1);
        takeOut(t);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        t.check("item taken out and put back: no clues", countShown(clientClues(t))).near(0, 0);
        press(t, CLUE_BUTTON);
        t.check("paid again: clues shown", countShown(clientClues(t))).atLeast(1);
        close(t);
        open(t);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        t.check("table closed and opened again: no clues", countShown(clientClues(t))).near(0, 0);
        close(t);

        t.gameMode(GameType.CREATIVE);
        open(t);
        setLevels(t, 0);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 0);
        t.check("creative, no lapis, no levels: the button can be pressed", buttonActive(t)).isTrue();
        pressClueButton(t);
        t.check("creative: clues shown", countShown(clientClues(t))).atLeast(1);
        t.check("creative: no lapis or levels taken", lapis(t) == 0 && levels(t) == 0).isTrue();
        close(t);
    }

    private static void buttonRules(TestContext t) {
        place(t, SHELVES);
        config().paidEnchantClues = false;
        t.ticks(2);
        open(t);
        t.check("Paid Clues off: no button", clueButton(t) == null).isTrue();
        close(t);

        config().paidEnchantClues = true;
        config().legacyEnchantReroll = false;
        t.ticks(2);
        open(t);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
        t.check("Reroll off: no button", clueButton(t) == null).isTrue();
        t.check("Reroll off: the clues of 1.21.1", countShown(clientClues(t))).atLeast(1);
        close(t);

        config().legacyEnchantReroll = true;
        config().enchantClueLapis = 99;
        config().enchantClueLevels = -5;
        t.ticks(2);
        open(t);
        setLevels(t, 5);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 12);
        t.check("on again: the button is back", clueButton(t) != null).isTrue();
        config().paidEnchantClues = false;
        t.ticks(3);
        t.check("turned off while the table is open: the button hides", t.onClientThread(() -> {
            Button button = clueButtonNow(t);
            return button != null && !button.visible && !button.active;
        })).isTrue();
        config().paidEnchantClues = true;
        t.ticks(3);
        t.check("lapis price 99 kept at 10: 12 lapis are not enough", buttonActive(t)).isFalse();
        setLapis(t, 13);
        pressClueButton(t);
        t.check("lapis price 99 kept at 10: 10 taken", lapis(t)).near(3, 0);
        t.check("level price -5 kept at 0: no levels taken", levels(t)).near(5, 0);
        close(t);
    }

    private static void fullCost(TestContext t) {
        place(t, SHELVES);
        for (boolean on : new boolean[] {true, false}) {
            String name = on ? "on: " : "off: ";
            config().legacyEnchantCost = on;
            t.ticks(2);
            open(t);
            int stat = enchantStat(t);
            setLevels(t, 33);
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
            int[] costs = serverRoll(t).costs();
            t.check(name + "line 3 costs 30 with 15 bookshelves", costs[2]).near(TOP_COST, 0);
            String text = t.onClientThread(() -> LegacyMechanicsInternals.enchantLevelsText(clientMenu(t), 2));
            t.check(name + "the tooltip says the levels taken (" + text + ")",
                text.equals((on ? TOP_COST : 3) + " Enchantment Levels")).isTrue();
            press(t, 2);
            t.check(name + "levels left after line 3", levels(t)).near(on ? 33 - TOP_COST : 33 - 3, 0);
            t.check(name + "lapis used", lapis(t)).near(0, 0);
            t.check(name + "the item is enchanted", slotItem(t).isEnchanted()).isTrue();
            t.check(name + "the Items Enchanted stat counts it", enchantStat(t)).near(stat + 1, 0);

            setLevels(t, TOP_COST - 1);
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
            press(t, 2);
            t.check(name + "29 levels: line 3 cannot be chosen", levels(t) == TOP_COST - 1
                && !slotItem(t).isEnchanted() && lapis(t) == 3).isTrue();
            close(t);
        }
    }

    private static void nothingSaved(TestContext t) {
        place(t, SHELVES);
        Set<String> before = t.onServer(level -> savedKeys(player(t, level)));
        open(t);
        setLevels(t, 50);
        for (int i = 0; i < 5; i++) {
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
            press(t, CLUE_BUTTON);
            takeOut(t);
        }
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        press(t, 0);
        close(t);
        Set<String> after = t.onServer(level -> savedKeys(player(t, level)));
        t.check("the player's saved data has the same keys " + after, after.equals(before)).isTrue();

        // on and off in turns, so warming up and noise fall on both; the fastest round of each counts
        open(t);
        double on = Double.MAX_VALUE;
        double off = Double.MAX_VALUE;
        for (int round = 0; round < 4; round++) {
            options(false);
            off = Math.min(off, slotChangeNanos(t));
            options(true);
            on = Math.min(on, slotChangeNanos(t));
        }
        close(t);
        t.note("ns per item put in and taken out, options on", Math.round(on));
        t.note("ns per item put in and taken out, options off (1.21.1)", Math.round(off));
    }

    private static void allOff(TestContext t) {
        options(false);
        t.ticks(3);
        place(t, SHELVES);
        open(t);
        setLevels(t, 33);
        int playerSeed = t.onServer(level -> player(t, level).getEnchantmentSeed());
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        int[] expected = vanillaCosts(playerSeed, SHELVES, pickaxe);
        Set<String> costs = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            put(t, pickaxe.copy(), 3);
            Roll roll = serverRoll(t);
            t.require(roll.seed() == null, "All off still made a roll");
            costs.add(Arrays.toString(roll.costs()));
            t.check("the levels are 1.21.1's from the player's seed (" + Arrays.toString(roll.costs()) + ")",
                Arrays.equals(roll.costs(), expected)).isTrue();
            takeOut(t);
        }
        t.check("the same levels on every placement", costs.size()).near(1, 0);
        put(t, pickaxe.copy(), 3);
        int runes = t.onServer(level -> menu(t, level).getEnchantmentSeed());
        t.check("the runes come from the player's seed", runes).near(playerSeed, 0);
        t.check("a clue for every line on the client", countShown(clientClues(t)))
            .near(linesWithCost(serverRoll(t).costs()), 0);
        t.check("no button", clueButton(t) == null).isTrue();
        String text = t.onClientThread(() -> LegacyMechanicsInternals.enchantLevelsText(clientMenu(t), 2));
        t.check("the tooltip says 3 levels (" + text + ")", text.equals("3 Enchantment Levels")).isTrue();
        press(t, 2);
        t.check("line 3 takes 3 levels", levels(t)).near(30, 0);
        int newSeed = t.onServer(level -> player(t, level).getEnchantmentSeed());
        t.check("enchanting gives the player a new seed (1.21.1)", newSeed != playerSeed).isTrue();
        close(t);
    }

    private static void realClicks(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        t.withServerPlayer(player -> {
            player.getInventory().clearContent();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
            player.getInventory().setItem(1, new ItemStack(Items.LAPIS_LAZULI, 64));
            player.getInventory().setItem(2, new ItemStack(Items.IRON_SWORD));
            player.getInventory().setItem(3, new ItemStack(Items.BOOK, 5));
        });
        t.ticks(3);
        click(t, HOTBAR + 1, 0, ClickType.QUICK_MOVE);
        t.check("shift-click puts the lapis in", lapis(t)).near(64, 0);

        List<String> missed = new ArrayList<>();
        List<Integer> seeds = new ArrayList<>();
        click(t, HOTBAR, 0, ClickType.PICKUP);
        click(t, 0, 0, ClickType.PICKUP);
        newRoll(t, seeds, missed, "put in by a click");
        click(t, 0, 0, ClickType.PICKUP);
        click(t, 0, 0, ClickType.PICKUP);
        newRoll(t, seeds, missed, "taken out and put back by clicks");
        click(t, HOTBAR + 2, 0, ClickType.PICKUP);
        click(t, 0, 0, ClickType.PICKUP);
        newRoll(t, seeds, missed, "swapped with the item on the cursor");
        click(t, HOTBAR + 2, 0, ClickType.PICKUP);
        click(t, 0, 0, ClickType.QUICK_MOVE);
        click(t, slotOf(t, Items.IRON_SWORD), 0, ClickType.QUICK_MOVE);
        newRoll(t, seeds, missed, "taken out and put in by shift-clicks");
        click(t, 0, 4, ClickType.SWAP);
        click(t, 0, 4, ClickType.SWAP);
        newRoll(t, seeds, missed, "the same stack swapped out and back with a number key");
        click(t, 0, 4, ClickType.SWAP);
        click(t, HOTBAR + 3, 1, ClickType.PICKUP);
        click(t, 0, 1, ClickType.PICKUP);
        newRoll(t, seeds, missed, "one book of a stack by right-clicks");
        click(t, HOTBAR + 3, 0, ClickType.PICKUP);
        t.check("every placement got a new roll (missed: " + missed + ")", missed.size()).near(0, 0);
        t.check("all rolls differ", new HashSet<>(seeds).size()).near(seeds.size(), 0);
        t.check("no clues on the client", countShown(clientClues(t))).near(0, 0);
        t.check("no lapis or levels taken", lapis(t) == 64 && levels(t) == 50).isTrue();

        press(t, CLUE_BUTTON);
        Roll paid = serverRoll(t);
        t.withServerPlayer(player -> player.getInventory().setItem(5, new ItemStack(Items.LAPIS_LAZULI, 64)));
        t.ticks(3);
        click(t, HOTBAR + 5, 0, ClickType.QUICK_MOVE);
        Roll after = serverRoll(t);
        t.check("lapis shift-clicked in after paying: 64 again", lapis(t)).near(64, 0);
        t.check("lapis shift-clicked in after paying: same roll, clues kept, nothing more taken",
            paid.seed().equals(after.seed()) && countShown(clientClues(t)) > 0 && levels(t) == 50 - CLUE_LEVELS)
            .isTrue();
        close(t);
        t.withServerPlayer(player -> player.getInventory().clearContent());
    }

    private static void changedWhileOpen(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        int playerSeed = t.onServer(level -> player(t, level).getEnchantmentSeed());
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        put(t, pickaxe, 10);
        Roll rolled = serverRoll(t);
        config().legacyEnchantReroll = false;
        t.ticks(3);
        setLapis(t, 11);
        t.check("reroll turned off: the next change shows 1.21.1's levels from the player's seed",
            Arrays.equals(serverRoll(t).costs(), vanillaCosts(playerSeed, SHELVES, pickaxe))).isTrue();
        t.check("reroll turned off: the next change shows 1.21.1's clues", countShown(clientClues(t)))
            .near(linesWithCost(serverRoll(t).costs()), 0);
        int runes = t.onServer(level -> menu(t, level).getEnchantmentSeed());
        t.check("reroll turned off: the runes are the player's seed again", runes).near(playerSeed, 0);
        config().legacyEnchantReroll = true;
        t.ticks(3);
        setLapis(t, 12);
        Roll back = serverRoll(t);
        t.check("turned on again: a new roll, clues hidden", back.seed() != null
            && !back.seed().equals(rolled.seed()) && countShown(clientClues(t)) == 0).isTrue();
        config().legacyEnchantCost = false;
        t.ticks(3);
        int before = levels(t);
        press(t, 2);
        t.check("cost turned off while open: line 3 takes 3 levels", levels(t)).near(before - 3, 0);
        close(t);
    }

    private static void oddPresses(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        ItemStack[] odd = {new ItemStack(Items.DIRT), t.enchanted(Items.IRON_SWORD, Enchantments.SHARPNESS, 1),
            new ItemStack(Items.ENCHANTED_BOOK)};
        int taken = 0;
        int grayed = 0;
        for (ItemStack item : odd) {
            put(t, item.copy(), 64);
            grayed += buttonActive(t) ? 0 : 1;
            press(t, CLUE_BUTTON);
            taken += lapis(t) == 64 && levels(t) == 50 ? 0 : 1;
            takeOut(t);
        }
        t.check("dirt, an enchanted sword, an enchanted book: the button is grayed out", grayed).near(odd.length, 0);
        t.check("dirt, an enchanted sword, an enchanted book: nothing taken", taken).near(0, 0);

        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        t.onClientThread(() -> {
            AbstractContainerMenu menu = t.player().containerMenu;
            t.mc().gameMode.handleInventoryButtonClick(menu.containerId + 1, CLUE_BUTTON);
            return null;
        });
        t.ticks(3);
        t.check("a wrong screen number: nothing taken, no clues", lapis(t) == 64 && levels(t) == 50
            && countShown(clientClues(t)) == 0).isTrue();

        Integer first = serverRoll(t).seed();
        t.onClientThread(() -> {
            AbstractContainerMenu menu = t.player().containerMenu;
            t.mc().gameMode.handleInventoryButtonClick(menu.containerId, CLUE_BUTTON);
            t.mc().gameMode.handleInventoryButtonClick(menu.containerId, CLUE_BUTTON);
            return null;
        });
        t.ticks(3);
        t.check("two presses in one tick: two looks paid (6 lapis, 6 levels), the second a new roll",
            lapis(t) == 64 - 2 * CLUE_LAPIS && levels(t) == 50 - 2 * CLUE_LEVELS
                && !first.equals(serverRoll(t).seed()) && countShown(clientClues(t)) > 0).isTrue();
        close(t);
    }

    private static void priceEdges(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, CLUE_LEVELS);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), CLUE_LAPIS + 3);
        t.check("exactly 6 lapis and 3 levels: the button can be pressed", buttonActive(t)).isTrue();
        pressClueButton(t);
        t.check("exactly enough: 3 lapis and 0 levels left, clues shown", lapis(t) == 3 && levels(t) == 0
            && countShown(clientClues(t)) > 0).isTrue();
        setLapis(t, 0);
        Roll kept = serverRoll(t);
        t.check("all lapis taken out after paying: the clues stay", countShown(clientClues(t))).atLeast(1);
        t.check("all lapis taken out: the button is grayed out", buttonActive(t)).isFalse();
        setLevels(t, 40);
        press(t, 0);
        t.check("no lapis: no enchanting", slotItem(t).isEnchanted()).isFalse();
        setLapis(t, 3);
        t.check("lapis back: same roll, clues still shown, nothing paid again", kept.seed().equals(serverRoll(t).seed())
            && countShown(clientClues(t)) > 0 && levels(t) == 40 && lapis(t) == 3).isTrue();
        press(t, 0);
        t.check("lapis back: line 1 enchants", slotItem(t).isEnchanted()).isTrue();
        close(t);

        config().enchantClueLapis = 1;
        config().enchantClueLevels = 10;
        t.ticks(3);
        open(t);
        setLevels(t, 9);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 4);
        t.check("price 1 lapis and 10 levels, 9 levels: grayed out", buttonActive(t)).isFalse();
        setLevels(t, 10);
        t.check("price 1 lapis and 10 levels, 4 lapis and 10 levels: can be pressed", buttonActive(t)).isTrue();
        pressClueButton(t);
        t.check("price 1 lapis and 10 levels: 1 lapis and 10 levels taken", lapis(t) == 3 && levels(t) == 0)
            .isTrue();
        close(t);

        config().enchantClueLevels = 0;
        t.ticks(3);
        open(t);
        setLevels(t, 0);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 4);
        t.check("price 0 levels, 0 levels: can be pressed", buttonActive(t)).isTrue();
        pressClueButton(t);
        t.check("price 0 levels: only 1 lapis taken", lapis(t) == 3 && levels(t) == 0
            && countShown(clientClues(t)) > 0).isTrue();
        close(t);
    }

    private static void exactLevels(TestContext t) {
        place(t, SHELVES);
        for (boolean on : new boolean[] {true, false}) {
            config().legacyEnchantCost = on;
            open(t);
            t.withServerPlayer(player -> {
                player.setExperienceLevels(TOP_COST);
                player.experienceProgress = 0.5F;
            });
            t.ticks(2);
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), 3);
            press(t, 2);
            float progress = t.onServer(level -> player(t, level).experienceProgress);
            String name = on ? "on: " : "off: ";
            t.check(name + "levels after line 3 from exactly 30", levels(t)).near(on ? 0 : TOP_COST - 3, 0);
            t.check(name + "the level progress stays", progress).near(0.5, 1.0E-6);
            close(t);
        }
    }

    private static void twoPlayers(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        Roll mine = serverRoll(t);
        int[] other = t.onServer(level -> {
            FakePlayer fake = FakePlayer.get(level);
            fake.setExperienceLevels(50);
            BlockPos pos = TestSite.ORIGIN.offset(TABLE_X, 0, TABLE_Z);
            EnchantmentMenu table = new EnchantmentMenu(99, fake.getInventory(),
                ContainerLevelAccess.create(level, pos));
            table.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
            table.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
            Integer seed = LegacyMechanicsInternals.enchantingRollSeed(table);
            boolean paid = table.clickMenuButton(fake, CLUE_BUTTON);
            int shown = countShown(table.enchantClue);
            table.getSlot(0).set(ItemStack.EMPTY);
            table.getSlot(1).set(ItemStack.EMPTY);
            return new int[] {seed == null ? 0 : 1, paid ? 1 : 0, shown, fake.experienceLevel};
        });
        t.ticks(3);
        Roll still = serverRoll(t);
        t.check("the other player's table has its own roll and paid for its clues (3 levels)",
            other[0] == 1 && other[1] == 1 && other[2] > 0 && other[3] == 50 - CLUE_LEVELS).isTrue();
        t.check("my table keeps my roll, without clues", mine.seed().equals(still.seed())
            && Arrays.equals(mine.costs(), still.costs()) && countShown(clientClues(t)) == 0).isTrue();
        t.check("nothing taken from me", lapis(t) == 64 && levels(t) == 50).isTrue();
        close(t);
    }

    private static void crowd(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        Roll mine = serverRoll(t);
        int players = 4;
        String[] result = t.onServer(level -> {
            BlockPos pos = TestSite.ORIGIN.offset(TABLE_X, 0, TABLE_Z);
            List<FakePlayer> fakes = new ArrayList<>();
            List<EnchantmentMenu> tables = new ArrayList<>();
            Set<Integer> seeds = new HashSet<>();
            for (int i = 0; i < players; i++) {
                FakePlayer fake = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "enchanter" + i));
                fake.setExperienceLevels(50);
                EnchantmentMenu table = new EnchantmentMenu(100 + i, fake.getInventory(),
                    ContainerLevelAccess.create(level, pos));
                table.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
                table.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
                seeds.add(LegacyMechanicsInternals.enchantingRollSeed(table));
                fakes.add(fake);
                tables.add(table);
            }
            // every fake pays once, the first one twice
            int paid = 0;
            for (int i = 0; i < players; i++) {
                paid += tables.get(i).clickMenuButton(fakes.get(i), CLUE_BUTTON) ? 1 : 0;
            }
            paid += tables.get(0).clickMenuButton(fakes.get(0), CLUE_BUTTON) ? 1 : 0;
            List<String> levelsLeft = new ArrayList<>();
            for (FakePlayer fake : fakes) {
                levelsLeft.add(String.valueOf(fake.experienceLevel));
            }
            // the second fake enchants; the others keep their rolls
            List<Integer> before = new ArrayList<>();
            for (EnchantmentMenu table : tables) {
                before.add(LegacyMechanicsInternals.enchantingRollSeed(table));
            }
            boolean enchanted = tables.get(1).clickMenuButton(fakes.get(1), 0);
            int unchanged = 0;
            for (int i = 0; i < players; i++) {
                Integer now = LegacyMechanicsInternals.enchantingRollSeed(tables.get(i));
                unchanged += i != 1 && before.get(i).equals(now) ? 1 : 0;
            }
            // 20 rolls in one tick on one table
            Set<Integer> burst = new HashSet<>();
            EnchantmentMenu table = tables.get(2);
            for (int i = 0; i < 20; i++) {
                table.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
                burst.add(LegacyMechanicsInternals.enchantingRollSeed(table));
                table.getSlot(0).set(ItemStack.EMPTY);
            }
            for (EnchantmentMenu each : tables) {
                each.getSlot(0).set(ItemStack.EMPTY);
                each.getSlot(1).set(ItemStack.EMPTY);
            }
            return new String[] {String.valueOf(seeds.size()), String.valueOf(paid), String.join(",", levelsLeft),
                String.valueOf(enchanted), String.valueOf(unchanged), String.valueOf(burst.size())};
        });
        t.ticks(3);
        Roll still = serverRoll(t);
        t.check("4 other players at the same table: 4 different rolls", Integer.parseInt(result[0])).near(players, 0);
        t.check("every press paid (one player twice)", Integer.parseInt(result[1])).near(players + 1, 0);
        t.check("each paid only their own levels (44,47,47,47: " + result[2] + ")",
            result[2].equals("44,47,47,47")).isTrue();
        t.check("one of them enchants", Boolean.parseBoolean(result[3])).isTrue();
        t.check("the other tables keep their rolls", Integer.parseInt(result[4])).near(players - 1, 0);
        t.check("20 rolls in one tick all differ", Integer.parseInt(result[5])).near(20, 0);
        t.check("my table keeps my roll and shows no clues", mine.seed().equals(still.seed())
            && Arrays.equals(mine.costs(), still.costs()) && countShown(clientClues(t)) == 0).isTrue();
        t.check("nothing taken from me", lapis(t) == 64 && levels(t) == 50).isTrue();
        close(t);
    }

    private static void closedByWorld(TestContext t) {
        place(t, SHELVES);
        open(t);
        setLevels(t, 50);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        press(t, CLUE_BUTTON);
        int containerId = t.onClientThread(() -> t.player().containerMenu.containerId);
        t.site().set(TABLE_X, 0, TABLE_Z, "air");
        t.waitUntil("the screen closes", () -> t.onClientThread(() -> !(t.mc().screen instanceof EnchantmentScreen)),
            20);
        t.ticks(3);
        int[] found = t.onServer(level -> {
            int pickaxes = 0;
            int lapis = 0;
            AABB around = new AABB(TestSite.ORIGIN).inflate(16);
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around)) {
                pickaxes += item.getItem().is(Items.DIAMOND_PICKAXE) ? item.getItem().getCount() : 0;
                lapis += item.getItem().is(Items.LAPIS_LAZULI) ? item.getItem().getCount() : 0;
            }
            ServerPlayer player = player(t, level);
            pickaxes += player.getInventory().countItem(Items.DIAMOND_PICKAXE);
            lapis += player.getInventory().countItem(Items.LAPIS_LAZULI);
            return new int[] {pickaxes, lapis};
        });
        t.check("table broken: the screen closed, the pickaxe and the 61 lapis left are all there",
            found[0] == 1 && found[1] == 64 - CLUE_LAPIS).isTrue();
        t.onClientThread(() -> {
            t.mc().gameMode.handleInventoryButtonClick(containerId, CLUE_BUTTON);
            return null;
        });
        t.ticks(3);
        t.check("a press for the closed table takes nothing", levels(t)).near(50 - CLUE_LEVELS, 0);
        t.removeEntities(ItemEntity.class);
        t.withServerPlayer(player -> player.getInventory().clearContent());

        place(t, SHELVES);
        open(t);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        t.check("a new table: a new roll without clues", countShown(clientClues(t))).near(0, 0);
        int walkedId = t.onClientThread(() -> t.player().containerMenu.containerId);
        t.onServer(level -> {
            EnchantmentMenu menu = menu(t, level);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            return null;
        });
        t.teleport(PLAYER_X, 0.0, PLAYER_Z + 20.0, 180.0F, 30.0F);
        t.waitUntil("the screen closes when the player is far away",
            () -> t.onClientThread(() -> !(t.mc().screen instanceof EnchantmentScreen)), 40);
        int before = levels(t);
        t.onClientThread(() -> {
            t.mc().gameMode.handleInventoryButtonClick(walkedId, CLUE_BUTTON);
            return null;
        });
        t.ticks(3);
        t.check("walked away: a press takes nothing", levels(t)).near(before, 0);

        place(t, SHELVES);
        open(t);
        put(t, new ItemStack(Items.DIAMOND_PICKAXE), 64);
        t.withServerPlayer(player -> player.setGameMode(GameType.SPECTATOR));
        t.ticks(3);
        int spectatorLevels = levels(t);
        press(t, CLUE_BUTTON);
        t.check("a spectator's press takes nothing", levels(t) == spectatorLevels && lapis(t) == 64).isTrue();
        t.withServerPlayer(player -> player.setGameMode(GameType.SURVIVAL));
        t.ticks(3);
        close(t);
    }

    private static void absurdPrices(TestContext t) {
        place(t, SHELVES);
        int[][] prices = {{Integer.MAX_VALUE, Integer.MAX_VALUE}, {Integer.MIN_VALUE, Integer.MIN_VALUE}};
        int[][] kept = {{10, 10}, {1, 0}};
        for (int i = 0; i < prices.length; i++) {
            config().enchantClueLapis = prices[i][0];
            config().enchantClueLevels = prices[i][1];
            t.ticks(3);
            int lapisNeeded = kept[i][0] + 3;
            String name = "prices " + prices[i][0] + " and " + prices[i][1] + ": ";
            open(t);
            setLevels(t, kept[i][1]);
            put(t, new ItemStack(Items.DIAMOND_PICKAXE), lapisNeeded - 1);
            t.check(name + "the client asks for " + lapisNeeded + " lapis (one less: grayed out)", buttonActive(t))
                .isFalse();
            setLapis(t, lapisNeeded);
            t.check(name + "the client asks for " + lapisNeeded + " lapis and " + kept[i][1] + " levels (enough: "
                + "can be pressed)", buttonActive(t)).isTrue();
            pressClueButton(t);
            t.check(name + "the server takes " + kept[i][0] + " lapis and " + kept[i][1] + " levels",
                lapis(t) == 3 && levels(t) == 0 && countShown(clientClues(t)) > 0).isTrue();
            close(t);
        }
    }

    private static void seedsSecret(TestContext t) {
        place(t, SHELVES);
        int rolls = 8;
        int[][] seen = t.onServer(level -> {
            FakePlayer fake = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "cracker"));
            EnchantmentMenu table = new EnchantmentMenu(120, fake.getInventory(),
                ContainerLevelAccess.create(level, TestSite.ORIGIN.offset(TABLE_X, 0, TABLE_Z)));
            int[][] values = new int[rolls][2];
            for (int i = 0; i < rolls; i++) {
                table.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
                Integer seed = LegacyMechanicsInternals.enchantingRollSeed(table);
                values[i][0] = seed == null ? 0 : seed;
                // the full number on the server; a client only gets its low 16 bits
                values[i][1] = table.getEnchantmentSeed();
                table.getSlot(0).set(ItemStack.EMPTY);
            }
            return values;
        });
        int cracked = 0;
        for (int i = 0; i + 2 < rolls; i++) {
            cracked += cracks(seen[i][1], seen[i + 1][1], seen[i + 1][0], seen[i + 2][0]) ? 1 : 0;
        }
        t.check("pairs of runes from which the next seeds can be worked out", cracked).near(0, 0);

        // control: the same attack on one source drawing seed, runes, seed, runes... (how it was before)
        RandomSource shared = RandomSource.create();
        int[][] control = new int[rolls][2];
        for (int i = 0; i < rolls; i++) {
            control[i][0] = shared.nextInt();
            control[i][1] = shared.nextInt();
        }
        int controlCracked = 0;
        for (int i = 0; i + 2 < rolls; i++) {
            controlCracked += cracks(control[i][1], control[i + 1][1], control[i + 1][0], control[i + 2][0]) ? 1 : 0;
        }
        t.check("control: the attack does work out the seeds of one shared source", controlCracked)
            .near(rolls - 2, 0);
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** What the server's table shows: the levels, clues and seed of its roll */
    private record Roll(int[] costs, int[] clues, int[] clueLevels, @Nullable Integer seed) {}

    /**
     * Builds the table with this many bookshelves around it (at the first places the game counts them,
     * {@code EnchantingTableBlock.BOOKSHELF_OFFSETS}) and puts the player in front of it
     */
    private static void place(TestContext t, int shelves) {
        t.gameMode(GameType.SURVIVAL);
        TestSite site = t.site();
        site.fill(TABLE_X - 2, 0, TABLE_Z - 2, TABLE_X + 2, 1, TABLE_Z + 2, "air");
        for (int i = 0; i < shelves; i++) {
            BlockPos offset = EnchantingTableBlock.BOOKSHELF_OFFSETS.get(i);
            site.set(TABLE_X + offset.getX(), offset.getY(), TABLE_Z + offset.getZ(), "bookshelf");
        }
        site.set(TABLE_X, 0, TABLE_Z, "enchanting_table");
        t.teleport(PLAYER_X, 0.0, PLAYER_Z, 180.0F, 30.0F);
        t.ticks(2);
    }

    /** Opens the table's screen on the server and waits for the client's */
    private static void open(TestContext t) {
        t.withServerPlayer(player -> {
            ServerLevel level = player.serverLevel();
            BlockPos pos = TestSite.ORIGIN.offset(TABLE_X, 0, TABLE_Z);
            MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
            t.require(provider != null, "The enchanting table has no screen");
            player.openMenu(provider);
        });
        t.waitUntil("the enchanting screen opens", () -> t.onClientThread(
            () -> t.mc().screen instanceof EnchantmentScreen), 20);
        t.ticks(2);
    }

    /** Empties the table's slots (so nothing is thrown out) and closes it */
    private static void close(TestContext t) {
        t.onServer(level -> {
            EnchantmentMenu menu = menu(t, level);
            menu.getSlot(0).set(ItemStack.EMPTY);
            menu.getSlot(1).set(ItemStack.EMPTY);
            return null;
        });
        t.ticks(2);
        t.closeScreen();
        t.ticks(2);
    }

    /** Puts lapis lazuli and then a fresh item into the table, as a player would */
    private static void put(TestContext t, ItemStack item, int lapis) {
        setLapis(t, lapis);
        putStack(t, item);
    }

    /** Puts this very stack into the item slot */
    private static void putStack(TestContext t, ItemStack item) {
        t.onServer(level -> {
            menu(t, level).getSlot(0).set(item);
            return null;
        });
        t.ticks(2);
    }

    /** Takes the item out of the table */
    private static void takeOut(TestContext t) {
        putStack(t, ItemStack.EMPTY);
    }

    /** Sets the lapis lazuli in the table */
    private static void setLapis(TestContext t, int count) {
        t.onServer(level -> {
            menu(t, level).getSlot(1).set(count > 0 ? new ItemStack(Items.LAPIS_LAZULI, count) : ItemStack.EMPTY);
            return null;
        });
        t.ticks(2);
    }

    /** Sets the player's level */
    private static void setLevels(TestContext t, int levels) {
        t.withServerPlayer(player -> player.setExperienceLevels(levels));
        t.ticks(2);
    }

    /** Presses a line (0 to 2) or Show Clues (3) with the game's button packet, as the screen does */
    private static void press(TestContext t, int button) {
        t.onClientThread(() -> {
            AbstractContainerMenu menu = t.player().containerMenu;
            t.mc().gameMode.handleInventoryButtonClick(menu.containerId, button);
            return null;
        });
        t.ticks(3);
    }

    /** Clicks a slot of the open screen from the client, as the player does */
    private static void click(TestContext t, int slot, int button, ClickType type) {
        t.onClientThread(() -> {
            t.mc().gameMode.handleInventoryMouseClick(t.player().containerMenu.containerId, slot, button, type,
                t.player());
            return null;
        });
        t.ticks(3);
    }

    /** Notes the roll after a placement and whether it is new */
    private static void newRoll(TestContext t, List<Integer> seeds, List<String> missed, String how) {
        Integer seed = serverRoll(t).seed();
        if (seed == null || seeds.contains(seed)) {
            missed.add(how);
        }
        seeds.add(seed);
    }

    /** The slot of the open screen that holds an item, below the table's two */
    private static int slotOf(TestContext t, Item item) {
        return t.onServer(level -> {
            EnchantmentMenu menu = menu(t, level);
            for (int i = 2; i < menu.slots.size(); i++) {
                if (menu.getSlot(i).getItem().is(item)) {
                    return i;
                }
            }
            throw new IllegalStateException(item + " is not in the inventory");
        });
    }

    /** Multiplier, increment and size of {@code java.util.Random} (and the game's {@code LegacyRandomSource}) */
    private static final long LCG_MULTIPLIER = 0x5DEECE66DL;
    private static final long LCG_INCREMENT = 0xBL;
    private static final long LCG_MASK = (1L << 48) - 1;

    /**
     * Tries to work out seeds from two runes the way a changed client could, if seeds and runes came from one
     * {@code java.util.Random}-like source: the 32 bits of the first runes are 32 of its 48 bits, the other 16 are
     * tried; the second runes (1 or 2 draws later) confirm the guess; then the next draws are compared with the seeds
     *
     * @return true if one of the next draws is the next seed or the one after
     */
    private static boolean cracks(int runes, int nextRunes, int nextSeed, int seedAfter) {
        for (int gap = 1; gap <= 2; gap++) {
            for (long low = 0; low < (1 << 16); low++) {
                long state = (runes & 0xFFFFFFFFL) << 16 | low;
                for (int i = 0; i < gap; i++) {
                    state = state * LCG_MULTIPLIER + LCG_INCREMENT & LCG_MASK;
                }
                if ((int) (state >>> 16) != nextRunes) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    state = state * LCG_MULTIPLIER + LCG_INCREMENT & LCG_MASK;
                    int drawn = (int) (state >>> 16);
                    if (drawn == nextSeed || drawn == seedAfter) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The levels of the three lines 1.21.1 shows for a seed and a number of bookshelves */
    private static int[] vanillaCosts(int seed, int shelves, ItemStack item) {
        RandomSource random = RandomSource.create();
        random.setSeed(seed);
        int[] costs = new int[3];
        for (int line = 0; line < costs.length; line++) {
            costs[line] = EnchantmentHelper.getEnchantmentCost(random, line, shelves, item);
            if (costs[line] < line + 1) {
                costs[line] = 0;
            }
        }
        return costs;
    }

    /** Presses the "?" button of the screen, if it can be pressed */
    private static void pressClueButton(TestContext t) {
        t.onClientThread(() -> {
            Button button = clueButtonNow(t);
            t.require(button != null && button.active, "The Show Clues button cannot be pressed");
            button.onPress();
            return null;
        });
        t.ticks(3);
    }

    /** The "?" button of the open enchanting screen, or null */
    @Nullable
    private static Button clueButton(TestContext t) {
        return t.onClientThread(() -> clueButtonNow(t));
    }

    @Nullable
    private static Button clueButtonNow(TestContext t) {
        if (t.mc().screen instanceof EnchantmentScreen screen) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button && button.getMessage().getString().equals("?")) {
                    return button;
                }
            }
        }
        return null;
    }

    /** Whether the "?" button can be pressed (after the screen's tick updated it) */
    private static boolean buttonActive(TestContext t) {
        t.ticks(2);
        Button button = clueButton(t);
        return button != null && t.onClientThread(() -> button.active);
    }

    /** The server's player */
    private static ServerPlayer player(TestContext t, ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
        t.require(player != null, "The player is gone");
        return player;
    }

    /** The server's enchanting table screen */
    private static EnchantmentMenu menu(TestContext t, ServerLevel level) {
        AbstractContainerMenu menu = player(t, level).containerMenu;
        t.require(menu instanceof EnchantmentMenu, "No enchanting table is open on the server");
        return (EnchantmentMenu) menu;
    }

    /** The client's enchanting table screen (call on the client thread) */
    private static EnchantmentMenu clientMenu(TestContext t) {
        AbstractContainerMenu menu = t.player().containerMenu;
        t.require(menu instanceof EnchantmentMenu, "No enchanting table is open on the client");
        return (EnchantmentMenu) menu;
    }

    /** The server table's roll */
    private static Roll serverRoll(TestContext t) {
        return t.onServer(level -> {
            EnchantmentMenu menu = menu(t, level);
            return new Roll(menu.costs.clone(), menu.enchantClue.clone(), menu.levelClue.clone(),
                LegacyMechanicsInternals.enchantingRollSeed(menu));
        });
    }

    /** The clues the client has */
    private static int[] clientClues(TestContext t) {
        return t.onClientThread(() -> clientMenu(t).enchantClue.clone());
    }

    /** The item in the table (a copy) */
    private static ItemStack slotItem(TestContext t) {
        return t.onServer(level -> menu(t, level).getSlot(0).getItem().copy());
    }

    /** The lapis lazuli in the table */
    private static int lapis(TestContext t) {
        return t.onServer(level -> menu(t, level).getSlot(1).getItem().getCount());
    }

    /** The player's level on the server */
    private static int levels(TestContext t) {
        return t.onServer(level -> player(t, level).experienceLevel);
    }

    /** The player's Items Enchanted stat */
    private static int enchantStat(TestContext t) {
        return t.onServer(level -> player(t, level).getStats().getValue(Stats.CUSTOM.get(Stats.ENCHANT_ITEM)));
    }

    /** A line to enchant: the one given by the round when it has levels, else the most expensive one */
    private static int line(int[] costs, int round) {
        int wanted = round % 3;
        if (costs[wanted] > 0) {
            return wanted;
        }
        for (int i = costs.length - 1; i >= 0; i--) {
            if (costs[i] > 0) {
                return i;
            }
        }
        return 0;
    }

    private static int linesWithCost(int[] costs) {
        return (int) Arrays.stream(costs).filter(cost -> cost > 0).count();
    }

    private static int countShown(int[] clues) {
        return (int) Arrays.stream(clues).filter(clue -> clue >= 0).count();
    }

    /** The top-level keys of the player's saved data */
    private static Set<String> savedKeys(ServerPlayer player) {
        return new HashSet<>(player.saveWithoutId(new CompoundTag()).getAllKeys());
    }

    /** Average time of putting an item into the table and taking it out, on the server thread */
    private static double slotChangeNanos(TestContext t) {
        Function<ServerLevel, Double> measure = level -> {
            EnchantmentMenu menu = menu(t, level);
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            int runs = 2000;
            for (int i = 0; i < runs / 4; i++) {
                menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
                menu.getSlot(0).set(ItemStack.EMPTY);
            }
            long start = System.nanoTime();
            for (int i = 0; i < runs; i++) {
                menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
                menu.getSlot(0).set(ItemStack.EMPTY);
            }
            double nanos = (System.nanoTime() - start) / (double) runs;
            menu.getSlot(1).set(ItemStack.EMPTY);
            return nanos;
        };
        double nanos = t.onServer(measure);
        t.ticks(2);
        return nanos;
    }
}
