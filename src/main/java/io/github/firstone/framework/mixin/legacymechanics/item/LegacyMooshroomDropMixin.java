package io.github.firstone.framework.mixin.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.animal.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives the mushrooms of a sheared mooshroom the 1.7.10 height ({@link LegacyItemDrops})
 *
 * <p>{@code MushroomCow.shear} creates its mushrooms itself, on top of the mooshroom. Runs on the server (also the
 * integrated one).</p>
 */
@Mixin(MushroomCow.class)
public class LegacyMooshroomDropMixin {

    @ModifyArg(method = "shear",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
        index = 2)
    private double legacymechanics$legacyDropY(double y) {
        return LegacyItemDrops.dropY((MushroomCow) (Object) this, y);
    }
}
