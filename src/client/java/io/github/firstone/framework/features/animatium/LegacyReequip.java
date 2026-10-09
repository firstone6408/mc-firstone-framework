package io.github.firstone.framework.features.animatium;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

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
 */
public final class LegacyReequip {

    private LegacyReequip() {}

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
