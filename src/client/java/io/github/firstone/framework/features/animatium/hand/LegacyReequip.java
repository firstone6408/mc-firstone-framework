package io.github.firstone.framework.features.animatium.hand;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Decides whether the main-hand item can stay in the hand without a re-equip animation
 *
 * <p>Based on 1.7.10 {@code ItemRenderer.updateEquippedItem}: a new stack of the same item with the same
 * damage replaces the rendered one without animation; anything else lowers the old item and raises the new one.</p>
 *
 * <p>Differences from the 1.7.10 code, chosen on purpose:</p>
 * <ul>
 *   <li>Enchantments, custom name and other components must also match (user decision), so switching
 *       between two swords with different enchantments plays the animation.</li>
 *   <li>Durability is only compared when the selected slot changed. In 1.7.10 the client damaged the held
 *       item itself, so hitting never re-equipped; in 1.21.1 the server sends a new stack after every hit,
 *       which would otherwise dip the hand on every attack.</li>
 * </ul>
 *
 * <p>A held stack that runs out in place (picked up onto the cursor, thrown with Ctrl+Q, moved out with a
 * shift-click, the last food eaten…) also plays the animation: 1.7.10 put {@code null} into the slot, so the
 * renderer saw a change, lowered the old item and raised the empty hand. 1.21.1 shrinks the same stack object to 0,
 * which the renderer still holds, so the item just vanished ({@link #emptiedInPlace}).</p>
 */
public final class LegacyReequip {

    /** The held stack the drop key is emptying: its animation belongs to "Old Item Drop", or null */
    @Nullable
    private static ItemStack dropKeyStack;

    private LegacyReequip() {}

    /**
     * Remembers the held stack the drop key is about to empty, so {@link #emptiedInPlace} leaves it to "Old Item
     * Drop" (the drop key keeps its own rule, also when that option is off)
     *
     * <p>Called on the client when the drop key drops the held item.</p>
     *
     * @param stack the selected stack
     */
    public static void dropKeyEmpties(ItemStack stack) {
        dropKeyStack = stack;
    }

    /**
     * Tells whether the hand renderer's stack ran out in place and should be shown as it was, so it lowers before
     * the empty hand rises
     *
     * <p>Called on the client by the hand renderer every tick and frame while "Legacy Re-equip" is on.</p>
     *
     * @param rendered     the stack the renderer shows now
     * @param lastRendered the stack it showed at the end of the last tick
     * @param lastLook     a copy of that stack as it looked then
     * @return true when {@code rendered} is the same object, now empty, not emptied by the drop key
     */
    public static boolean emptiedInPlace(ItemStack rendered, ItemStack lastRendered, ItemStack lastLook) {
        return rendered == lastRendered && rendered.isEmpty() && !lastLook.isEmpty() && rendered != dropKeyStack;
    }

    /**
     * Checks whether {@code current} can replace the rendered stack without a re-equip animation
     *
     * @param rendered    the stack the hand renderer is showing
     * @param current     the stack now in the selected slot
     * @param slotChanged true if the player selected another hotbar slot since {@code rendered} was taken
     * @return true to keep the hand up, false to play the re-equip animation
     */
    public static boolean keepsItem(ItemStack rendered, ItemStack current, boolean slotChanged) {
        if (rendered.isEmpty() || current.isEmpty()) {
            return rendered.isEmpty() && current.isEmpty();
        }
        if (!ItemStack.isSameItem(rendered, current)) {
            return false;
        }
        if (slotChanged && rendered.getDamageValue() != current.getDamageValue()) {
            return false;
        }
        return withoutDamage(rendered).equals(withoutDamage(current));
    }

    /**
     * Returns the stack's component changes without the damage (durability) component
     *
     * @param stack a non-empty stack
     * @return its component patch, ignoring damage
     */
    private static DataComponentPatch withoutDamage(ItemStack stack) {
        return stack.getComponentsPatch().forget(type -> type == DataComponents.DAMAGE);
    }
}
