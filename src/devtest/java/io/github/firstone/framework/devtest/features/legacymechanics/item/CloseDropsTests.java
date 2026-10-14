package io.github.firstone.framework.devtest.features.legacymechanics.item;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.mixin.ItemEntityAccessor;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests of the 1.7.10 closing of screens, part of the Legacy Mechanics tests
 *
 * <p>Items are put into a screen's slots on the server, as if the player had placed them, and the screen is closed
 * from the client (Escape), so the server closes it from the player's packet. With "Drop Items on Close" on, every
 * item of the temporary slots and the cursor is thrown out, nothing is put back into the inventory (1.7.10
 * {@code onContainerClosed}); off, everything goes back into the inventory (1.21.1). The player stands facing south,
 * away from the block, so the thrown items fly into open space.</p>
 */
public final class CloseDropsTests {

    /** Where the player stands (site coordinates), facing south (+z) */
    private static final double PLAYER_X = 4.5;
    private static final double PLAYER_Z = 6.5;

    /** Where the work block stands, behind the player (within reach, 8 blocks) */
    private static final int BLOCK_X = 4;
    private static final int BLOCK_Z = 4;

    /** How far around the player thrown items are looked for */
    private static final double NEAR = 6.0;

    /** Speed of a throw (1.7.10 {@code func_146097_a} and 1.21.1 {@code Player.drop}) and its random part */
    private static final double THROW = 0.3;
    private static final double THROW_RANDOM = 0.02;

    /** Upward speed of a throw, ± its random part */
    private static final double THROW_UP = 0.1;
    private static final double THROW_UP_RANDOM = 0.1;

    /** How far below the eyes a thrown item appears, and how much lower "Legacy Item Drops" puts it */
    private static final double BELOW_EYES = 0.3;
    private static final double ITEM_HALF = 0.125;

    /** Ticks a thrown item waits before it can be picked up (1.7.10 {@code delayBeforeCanPickup = 40}) */
    private static final int PICKUP_DELAY = 40;

    /** Allowed difference of values computed the same way */
    private static final double EXACT = 1.0E-6;

    private CloseDropsTests() {}

    /**
     * Returns the close drop tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Close drops: the inventory's crafting grid and the cursor item are thrown out; off: back "
                + "into the inventory (1.21.1)", CloseDropsTests::inventory),
            TestCase.of("Close drops: crafting, enchanting and smithing tables, anvil, grindstone, stonecutter, loom "
                + "and cartography table throw their items; off: back into the inventory", CloseDropsTests::workBlocks),
            TestCase.of("Close drops: a trader's payment slots are thrown out; off: back into the inventory",
                CloseDropsTests::trading),
            TestCase.of("Close drops: a chest keeps its items, its cursor item is thrown; off: put back",
                CloseDropsTests::chest),
            TestCase.of("Close drops: thrown ahead like the drop key, 0.3 below the eyes, no thrower, 2 s before "
                + "pickup, not counted as dropped (as 1.21.1)", CloseDropsTests::throwLikeLegacy),
            TestCase.of("Close drops: the thrown items are exactly the items put in (count, damage, name, "
                + "enchantments), none doubled or lost; off: the same items back", CloseDropsTests::sameItems),
            TestCase.of("Close drops: the client sees the thrown items and an empty grid when it opens the inventory "
                + "again", CloseDropsTests::clientSees)
        );
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** A screen to test: what opens it and which of its slots get which items */
    private record Screen(String name, Opener opener, int[] slots, Item[] items) {}

    /** Opens a screen for the player */
    @FunctionalInterface
    private interface Opener {
        void open(TestContext t);
    }

    /** Where the items ended up after the screen closed: thrown (lying near the player) or in the inventory */
    private record Result(int thrown, int inInventory, int given) {}

    /** How many of an item go into slot number {@code index}: 1, 2, 3… as far as the item stacks */
    private static int count(Item item, int index) {
        return Math.min(index + 1, item.getDefaultMaxStackSize());
    }

    /** Opens the screen of a work block placed behind the player */
    private static Opener block(String block) {
        return t -> {
            t.site().fill(BLOCK_X, 0, BLOCK_Z, BLOCK_X, 0, BLOCK_Z, block);
            t.withServerPlayer(player -> {
                ServerLevel level = player.serverLevel();
                BlockPos pos = TestSite.ORIGIN.offset(BLOCK_X, 0, BLOCK_Z);
                MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
                t.require(provider != null, block + " has no screen");
                player.openMenu(provider);
            });
        };
    }

    /** Puts the player in place, opens the screen, fills its slots and the cursor, closes it with Escape */
    private static Result close(TestContext t, Screen screen, @Nullable ItemStack cursor) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(PLAYER_X, 0.0, PLAYER_Z, 0.0F, 0.0F);
        t.ticks(2);
        screen.opener().open(t);
        t.waitUntil("the screen opens", () -> t.onClientThread(() -> t.mc().screen instanceof AbstractContainerScreen),
            20);
        t.withServerPlayer(player -> {
            AbstractContainerMenu menu = player.containerMenu;
            for (int i = 0; i < screen.slots().length; i++) {
                menu.getSlot(screen.slots()[i]).set(new ItemStack(screen.items()[i], count(screen.items()[i], i)));
            }
            if (cursor != null) {
                menu.setCarried(cursor.copy());
            }
            menu.broadcastChanges();
        });
        t.ticks(2);
        t.closeScreen();
        t.ticks(2);
        int given = cursor != null ? cursor.getCount() : 0;
        for (int i = 0; i < screen.items().length; i++) {
            given += count(screen.items()[i], i);
        }
        List<Item> items = new ArrayList<>(List.of(screen.items()));
        if (cursor != null) {
            items.add(cursor.getItem());
        }
        int total = given;
        return t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            int thrown = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(NEAR))) {
                thrown += items.contains(item.getItem().getItem()) ? item.getItem().getCount() : 0;
            }
            int kept = 0;
            for (Item item : items) {
                kept += player.getInventory().countItem(item);
            }
            return new Result(thrown, kept, total);
        });
    }

    /** Closes a screen with the option on, then off, and checks where the items went */
    private static void checkBoth(TestContext t, Screen screen, @Nullable ItemStack cursor) {
        Result on = close(t, screen, cursor);
        t.check(screen.name() + ": items thrown out (all " + on.given() + ")", on.thrown()).near(on.given(), 0);
        t.check(screen.name() + ": items put back into the inventory", on.inInventory()).near(0, 0);
        t.closeScreen();
        t.removeEntities();
        t.withServerPlayer(player -> player.getInventory().clearContent());
        config().dropItemsOnClose = false;
        Result off = close(t, screen, cursor);
        t.check(screen.name() + ", off: items thrown out", off.thrown()).near(0, 0);
        t.check(screen.name() + ", off: items put back into the inventory (all " + off.given() + ")",
            off.inInventory()).near(off.given(), 0);
        config().dropItemsOnClose = true;
        t.removeEntities();
        t.withServerPlayer(player -> player.getInventory().clearContent());
    }

    /** The player's own inventory: its 2 × 2 crafting grid is slots 1 to 4 */
    private static Screen inventoryScreen() {
        return new Screen("inventory", t -> t.openScreen(() -> new InventoryScreen(t.player())),
            new int[] {1, 2, 3, 4}, new Item[] {Items.OAK_PLANKS, Items.STICK, Items.COBBLESTONE, Items.FLINT});
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private static void inventory(TestContext t) {
        checkBoth(t, inventoryScreen(), new ItemStack(Items.APPLE, 5));
    }

    private static void workBlocks(TestContext t) {
        Screen[] screens = {
            new Screen("crafting table", block("crafting_table"), new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9},
                new Item[] {Items.OAK_PLANKS, Items.STICK, Items.COBBLESTONE, Items.IRON_INGOT, Items.GOLD_INGOT,
                    Items.DIAMOND, Items.REDSTONE, Items.COAL, Items.FLINT}),
            new Screen("enchanting table (item and lapis)", block("enchanting_table"), new int[] {0, 1},
                new Item[] {Items.IRON_SWORD, Items.LAPIS_LAZULI}),
            new Screen("anvil", block("anvil"), new int[] {0, 1}, new Item[] {Items.IRON_SWORD, Items.IRON_INGOT}),
            new Screen("smithing table", block("smithing_table"), new int[] {0, 1, 2},
                new Item[] {Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.DIAMOND_SWORD, Items.NETHERITE_INGOT}),
            new Screen("grindstone", block("grindstone"), new int[] {0, 1},
                new Item[] {Items.IRON_SWORD, Items.GOLDEN_SWORD}),
            new Screen("stonecutter", block("stonecutter"), new int[] {0}, new Item[] {Items.STONE}),
            new Screen("loom", block("loom"), new int[] {0, 1, 2},
                new Item[] {Items.WHITE_BANNER, Items.RED_DYE, Items.FLOWER_BANNER_PATTERN}),
            new Screen("cartography table", block("cartography_table"), new int[] {0, 1},
                new Item[] {Items.FILLED_MAP, Items.PAPER})};
        for (Screen screen : screens) {
            checkBoth(t, screen, new ItemStack(Items.APPLE, 3));
        }
    }

    private static void trading(TestContext t) {
        // a new trader for each closing (the thrown items are removed together with every other entity)
        Opener opener = c -> {
            Spawned<WanderingTrader> trader = c.spawn(EntityType.WANDERING_TRADER, BLOCK_X + 0.5, 0.0, BLOCK_Z + 0.5,
                0.0F, entity -> entity.setNoAi(true));
            c.withServerPlayer(player -> {
                if (player.serverLevel().getEntity(trader.id()) instanceof WanderingTrader entity) {
                    entity.mobInteract(player, InteractionHand.MAIN_HAND);
                }
            });
        };
        checkBoth(t, new Screen("trader", opener, new int[] {0, 1}, new Item[] {Items.EMERALD, Items.DIAMOND}),
            new ItemStack(Items.APPLE, 2));
    }

    private static void chest(TestContext t) {
        for (boolean on : new boolean[] {true, false}) {
            config().dropItemsOnClose = on;
            String name = on ? "chest" : "chest, off";
            Result result = close(t, new Screen(name, block("chest"), new int[] {0}, new Item[] {Items.DIAMOND}),
                new ItemStack(Items.APPLE, 4));
            BlockPos pos = TestSite.ORIGIN.offset(BLOCK_X, 0, BLOCK_Z);
            int inChest = t.onServer(level -> {
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                    int count = chest.getItem(0).getCount();
                    // emptied, so the diamond does not drop when the site is cleared
                    chest.clearContent();
                    return count;
                }
                return -1;
            });
            t.check(name + ": the diamond stays in the chest", inChest).near(1, 0);
            t.check(name + ": the cursor's 4 apples thrown out (1.7.10); off: none", result.thrown())
                .near(on ? 4 : 0, 0);
            t.check(name + ": put back into the inventory; off: the 4 apples", result.inInventory())
                .near(on ? 0 : 4, 0);
            t.closeScreen();
            t.removeEntities();
            t.withServerPlayer(player -> player.getInventory().clearContent());
        }
    }

    /** What a close threw, measured before the items moved: {count, y below the eyes, speeds…} */
    private record Throw(int items, double belowEyes, double forward, double sideways, double up, boolean noThrower,
                         int pickupDelay, int dropStat) {}

    /** Closes the inventory on the server with one grid item and a cursor item, and measures the throws */
    private static Throw closeAndMeasure(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(PLAYER_X, 0.0, PLAYER_Z, 0.0F, 0.0F);
        t.ticks(2);
        return t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            int stat = player.getStats().getValue(Stats.CUSTOM.get(Stats.DROP));
            player.inventoryMenu.getSlot(1).set(new ItemStack(Items.STICK, 2));
            player.inventoryMenu.setCarried(new ItemStack(Items.APPLE, 3));
            // what the player's close packet does
            player.doCloseContainer();
            int items = 0;
            double below = 0.0;
            double forward = Double.MAX_VALUE;
            double sideways = 0.0;
            double up = 0.0;
            boolean noThrower = true;
            int delay = Integer.MAX_VALUE;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(NEAR))) {
                // only this test's items (not one left by an earlier test)
                if (!item.getItem().is(Items.STICK) && !item.getItem().is(Items.APPLE)) {
                    continue;
                }
                items++;
                below = Math.max(below, player.getEyeY() - item.getY());
                forward = Math.min(forward, item.getDeltaMovement().z);
                sideways = Math.max(sideways, Math.abs(item.getDeltaMovement().x));
                up = Math.max(up, Math.abs(item.getDeltaMovement().y - THROW_UP));
                noThrower &= item.getOwner() == null;
                delay = Math.min(delay, ((ItemEntityAccessor) item).devtest$getPickupDelay());
                item.discard();
            }
            int dropped = player.getStats().getValue(Stats.CUSTOM.get(Stats.DROP)) - stat;
            player.getInventory().clearContent();
            return new Throw(items, below, forward, sideways, up, noThrower, delay, dropped);
        });
    }

    private static void throwLikeLegacy(TestContext t) {
        Throw on = closeAndMeasure(t);
        t.check("items thrown (grid and cursor)", on.items()).near(2, 0);
        t.check("they appear 0.3 below the eyes, 0.125 lower with Legacy Item Drops", on.belowEyes())
            .near(BELOW_EYES + ITEM_HALF, EXACT);
        t.check("slowest forward speed (0.3 ahead, the way the player looks)", on.forward())
            .between(THROW - THROW_RANDOM, THROW + THROW_RANDOM);
        t.check("largest sideways speed (only the random part)", on.sideways()).atMost(THROW_RANDOM);
        t.check("upward speed 0.1, at most this far from it (random part)", on.up()).atMost(THROW_UP_RANDOM);
        t.check("no thrower (1.7.10 set it only for the drop key)", on.noThrower()).isTrue();
        t.check("ticks before they can be picked up", on.pickupDelay()).near(PICKUP_DELAY, 0);
        t.check("not counted as dropped items (1.21.1 counts only the drop key)", on.dropStat()).near(0, 0);
        config().dropItemsOnClose = false;
        Throw off = closeAndMeasure(t);
        t.check("off: items thrown", off.items()).near(0, 0);
        t.check("off: counted as dropped items", off.dropStat()).near(0, 0);
    }

    /** A named, damaged, enchanted sword: every part of it must come out unchanged */
    private static ItemStack namedSword(TestContext t) {
        ItemStack sword = t.enchanted(Items.IRON_SWORD, Enchantments.SHARPNESS, 3);
        sword.setDamageValue(17);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Test Sword"));
        return sword;
    }

    /**
     * Puts the sword into a crafting table and 37 apples onto the cursor, closes it and returns {sword found the same,
     * apples found, swords found} among the thrown items and the inventory
     */
    private static int[] closeWithSword(TestContext t, ItemStack sword) {
        Screen table = new Screen("crafting table", block("crafting_table"), new int[0], new Item[0]);
        close(t, table, null);
        // the screen is closed by now: open it again with the items in it
        table.opener().open(t);
        t.waitUntil("the screen opens", () -> t.onClientThread(() -> t.mc().screen instanceof AbstractContainerScreen),
            20);
        t.withServerPlayer(player -> {
            player.containerMenu.getSlot(5).set(sword.copy());
            player.containerMenu.setCarried(new ItemStack(Items.APPLE, 37));
            player.containerMenu.broadcastChanges();
        });
        t.ticks(2);
        t.closeScreen();
        t.ticks(2);
        return t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            int same = 0;
            int apples = 0;
            int swords = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(NEAR))) {
                same += ItemStack.matches(item.getItem(), sword) ? 1 : 0;
                apples += item.getItem().is(Items.APPLE) ? item.getItem().getCount() : 0;
                swords += item.getItem().is(Items.IRON_SWORD) ? 1 : 0;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                same += ItemStack.matches(stack, sword) ? 1 : 0;
                apples += stack.is(Items.APPLE) ? stack.getCount() : 0;
                swords += stack.is(Items.IRON_SWORD) ? 1 : 0;
            }
            apples += player.containerMenu.getCarried().getCount();
            return new int[] {same, apples, swords};
        });
    }

    private static void sameItems(TestContext t) {
        ItemStack sword = namedSword(t);
        for (boolean on : new boolean[] {true, false}) {
            config().dropItemsOnClose = on;
            String name = on ? "" : "off: ";
            int[] found = closeWithSword(t, sword);
            t.check(name + "the sword found unchanged (count, damage, name, enchantments)", found[0]).near(1, 0);
            t.check(name + "swords in all (none doubled)", found[2]).near(1, 0);
            t.check(name + "apples in all (37, none doubled or lost)", found[1]).near(37, 0);
            t.closeScreen();
            t.removeEntities();
            t.withServerPlayer(player -> player.getInventory().clearContent());
        }
    }

    private static void clientSees(TestContext t) {
        close(t, inventoryScreen(), new ItemStack(Items.APPLE, 5));
        t.ticks(5);
        int seen = t.onClientThread(() -> {
            AABB around = t.player().getBoundingBox().inflate(NEAR);
            int count = 0;
            for (ItemEntity item : t.level().getEntitiesOfClass(ItemEntity.class, around)) {
                count += item.getItem().getCount();
            }
            return count;
        });
        t.check("client: thrown items it sees (1 + 2 + 3 + 4 in the grid, 5 apples)", seen).near(15, 0);
        t.openScreen(() -> new InventoryScreen(t.player()));
        t.ticks(2);
        int grid = t.onClientThread(() -> {
            int count = 0;
            for (int slot = 1; slot <= 4; slot++) {
                count += t.player().inventoryMenu.getSlot(slot).getItem().getCount();
            }
            return count + t.player().inventoryMenu.getCarried().getCount();
        });
        t.check("client: items left in the grid and on the cursor", grid).near(0, 0);
        t.closeScreen();
    }
}
