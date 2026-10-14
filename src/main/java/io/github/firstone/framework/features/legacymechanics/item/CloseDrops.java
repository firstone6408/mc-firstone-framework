package io.github.firstone.framework.features.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 1.7.10 closing of screens: the items a screen only holds while it is open are thrown out, not put back
 *
 * <p>When a 1.7.10 screen closed ({@code Container.onContainerClosed}), the item on the cursor and the items left in
 * the crafting grid (inventory and crafting table), the enchanting table, the anvil and a villager's trade slots were
 * thrown in front of the player with {@code dropPlayerItemWithRandomChoice}: the throw of the drop key, without the
 * thrower's name. 1.21.1 puts them back into the inventory (and throws only what does not fit). The newer work blocks
 * (stonecutter, loom, grindstone, cartography and smithing tables) and the enchanting table's lapis slot close the
 * same way, so they throw their items too.</p>
 *
 * <p>Nothing else of the game changes: the item is thrown with the call 1.21.1 itself uses when it cannot give items
 * back (a dead or disconnected player: {@code player.drop(stack, false)}), the same stack that would have gone back
 * (count, damage, name, enchantments), already taken out of its slot, so it can never be doubled. Screens that keep
 * their items (chests, furnaces…) are not changed.</p>
 */
public final class CloseDrops {

    private CloseDrops() {}

    /**
     * Puts an item a closing screen gives back into the inventory, or throws it like 1.7.10
     *
     * <p>Called on the server in place of {@code Inventory.placeItemBackInInventory} when a screen closes: for the
     * cursor item, the temporary slots of a work block and a villager's trade slots.</p>
     *
     * @param inventory the player's inventory
     * @param stack     the item (may be empty)
     * @return true when the item was thrown ("Drop Items on Close" is on), false when it still has to be put back
     */
    public static boolean dropInstead(Inventory inventory, ItemStack stack) {
        if (!LegacyMechanicsFeature.getConfig().dropItemsOnClose
            || !(inventory.player instanceof ServerPlayer player)) {
            return false;
        }
        // what 1.21.1 does when it cannot give the item back (and 1.7.10 dropPlayerItemWithRandomChoice): thrown ahead,
        // no thrower; an empty stack throws nothing
        player.drop(stack, false);
        return true;
    }
}
