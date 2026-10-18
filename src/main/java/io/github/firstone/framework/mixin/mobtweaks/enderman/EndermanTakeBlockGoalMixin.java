package io.github.firstone.framework.mixin.mobtweaks.enderman;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.mobtweaks.enderman.EndermanBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Enderman Blocks Stay: an enderman picking up a block leaves it in the world ({@link EndermanBlocks})
 *
 * <p>The goal still chooses the block and gives it to the enderman to hold as in 1.21.1; only taking it out of the
 * world (and the game event of that) is left out, and the enderman is marked as holding it for show.</p>
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public class EndermanTakeBlockGoalMixin {

    @Shadow
    @Final
    private EnderMan enderman;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean mobtweaks$keepBlock(Level level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        if (EndermanBlocks.pickUpForShow()) {
            EndermanBlocks.markShownOnly(this.enderman);
            return true;
        }
        EndermanBlocks.clearShownOnly(this.enderman); // a real block: never treated as one for show
        return original.call(level, pos, moving);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V"))
    private void mobtweaks$noTakeEvent(Level level, Holder<GameEvent> event, BlockPos pos, GameEvent.Context context,
                                       Operation<Void> original) {
        if (!EndermanBlocks.isShownOnly(this.enderman)) {
            original.call(level, event, pos, context);
        }
    }
}
