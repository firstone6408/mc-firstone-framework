package io.github.firstone.framework.mixin.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives an eye of ender that drops after its flight the 1.7.10 height ({@link LegacyItemDrops})
 *
 * <p>{@code EyeOfEnder.tick} creates the item where the eye ends its flight, when it does not break. Runs on the server
 * (also the integrated one).</p>
 */
@Mixin(EyeOfEnder.class)
public class LegacyEnderEyeDropMixin {

    @ModifyArg(method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"),
        index = 2)
    private double legacymechanics$legacyDropY(double y) {
        return LegacyItemDrops.dropY((EyeOfEnder) (Object) this, y);
    }
}
