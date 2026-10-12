package io.github.firstone.framework.mixin.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the items players drop and throw the 1.7.10 height ({@link LegacyItemDrops})
 *
 * <p>{@code Player.drop} is used for items thrown with the drop key or out of the inventory, and for everything a
 * player drops on death (1.7.10 {@code EntityPlayer.func_146097_a}). Runs on the server (also the integrated one).</p>
 */
@Mixin(Player.class)
public class LegacyPlayerDropMixin {

    @ModifyArg(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
        index = 2)
    private double legacymechanics$legacyDropY(double y) {
        return LegacyItemDrops.dropY((Player) (Object) this, y);
    }
}
