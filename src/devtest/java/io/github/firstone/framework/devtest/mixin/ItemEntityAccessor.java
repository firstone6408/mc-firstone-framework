package io.github.firstone.framework.devtest.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads how long a dropped item waits before it can be picked up; part of the dev test mod only
 */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {

    /**
     * Returns the ticks left before the item can be picked up
     *
     * @return {@code pickupDelay}
     */
    @Accessor("pickupDelay")
    int devtest$getPickupDelay();
}
