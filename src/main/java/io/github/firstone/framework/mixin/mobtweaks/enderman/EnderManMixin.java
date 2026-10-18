package io.github.firstone.framework.mixin.mobtweaks.enderman;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.mobtweaks.enderman.EndermanBlocks;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enderman Blocks Stay: a block held for show does not drop on death and does not keep the enderman from despawning
 * ({@link EndermanBlocks})
 *
 * <p>Both places ask 1.21.1's {@code getCarriedBlock()}; for a block held for show they get "nothing held", so no
 * block drops (as in 1.7.10) and the enderman despawns like any other. A real block works as in 1.21.1.</p>
 */
@Mixin(EnderMan.class)
public class EnderManMixin {

    @WrapOperation(method = {"dropCustomDeathLoot", "requiresCustomPersistence"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/monster/EnderMan;getCarriedBlock()Lnet/minecraft/world/level/block/state/BlockState;"))
    @Nullable
    private BlockState mobtweaks$realBlockOnly(EnderMan enderman, Operation<BlockState> original) {
        return EndermanBlocks.isShownOnly(enderman) ? null : original.call(enderman);
    }
}
