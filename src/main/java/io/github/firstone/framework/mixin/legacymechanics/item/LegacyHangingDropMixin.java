package io.github.firstone.framework.mixin.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.decoration.HangingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the items paintings, item frames and leash knots drop the 1.7.10 height ({@link LegacyItemDrops})
 *
 * <p>{@code HangingEntity} has its own {@code spawnAtLocation}, which moves the item a little away from the wall; its
 * height is changed like for every other entity. Runs on the server (also the integrated one).</p>
 */
@Mixin(HangingEntity.class)
public class LegacyHangingDropMixin {

    @ModifyArg(method = "spawnAtLocation",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
        index = 2)
    private double legacymechanics$legacyDropY(double y) {
        return LegacyItemDrops.dropY((HangingEntity) (Object) this, y);
    }
}
