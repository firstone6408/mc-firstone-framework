package io.github.firstone.framework.mixin.mobtweaks.enderman;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.mobtweaks.enderman.EndermanBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enderman Blocks Stay: an enderman putting down a block it holds for show places nothing ({@link EndermanBlocks})
 *
 * <p>The goal still runs when and where 1.21.1 runs it; for a block held for show it only empties the enderman's
 * hands (no block, no game event), so it can pick up another one, and the mark is forgotten. A real block is placed
 * as in 1.21.1, whatever the option says.</p>
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public class EndermanLeaveBlockGoalMixin {

    @Shadow
    @Final
    private EnderMan enderman;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean mobtweaks$placeNothing(Level level, BlockPos pos, BlockState state, int flags,
                                           Operation<Boolean> original) {
        return EndermanBlocks.isShownOnly(this.enderman) || original.call(level, pos, state, flags);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"))
    private void mobtweaks$noPlaceEvent(Level level, Holder<GameEvent> event, BlockPos pos, GameEvent.Context context,
                                        Operation<Void> original) {
        if (!EndermanBlocks.isShownOnly(this.enderman)) {
            original.call(level, event, pos, context);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/monster/EnderMan;setCarriedBlock(Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void mobtweaks$forgetMark(EnderMan enderman, BlockState state, Operation<Void> original) {
        original.call(enderman, state);
        EndermanBlocks.clearShownOnly(enderman);
    }
}
