package io.github.firstone.framework.mixin.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives a fishing catch the 1.7.10 height ({@link LegacyItemDrops})
 *
 * <p>{@code FishingHook.retrieve} creates the caught item at the hook and throws it towards the player; only where it
 * starts changes. Runs on the server (also the integrated one).</p>
 */
@Mixin(FishingHook.class)
public class LegacyFishingDropMixin {

    @ModifyArg(method = "retrieve",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
        index = 2)
    private double legacymechanics$legacyDropY(double y) {
        return LegacyItemDrops.dropY((FishingHook) (Object) this, y);
    }
}
