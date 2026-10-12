package io.github.firstone.framework.mixin.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the items every entity drops the 1.7.10 height ({@link LegacyItemDrops})
 *
 * <p>{@code Entity.spawnAtLocation} is where entities drop their loot, equipment, eggs, wool and everything else
 * (1.7.10 {@code entityDropItem}). The height of the new item is changed before it is created. Runs on the server (also
 * the integrated one).</p>
 */
@Mixin(Entity.class)
public class LegacyEntityDropMixin {

    @ModifyArg(
        method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
        index = 2)
    private double legacymechanics$legacyDropY(double y) {
        return LegacyItemDrops.dropY((Entity) (Object) this, y);
    }
}
