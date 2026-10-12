package io.github.firstone.framework.client.mixin.animatium.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.animatium.entity.LegacyMobPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves out the effects of a landing the client only sees because of the 1.7.10 mob physics
 * ({@link LegacyMobPhysics#skipsLandingEffects})
 *
 * <p>In {@code Entity.checkFallDamage}, {@code Block.fallOn} (fall damage, sounds, a creeper's swell) is left out for
 * a mob the client moves with those physics; the server has the landing with all its effects. The fall distance is
 * still reset and the bounce of slime blocks and beds ({@code updateEntityAfterFallOn}) is untouched.</p>
 */
@Mixin(Entity.class)
public class LegacyMobLandingMixin {

    @WrapOperation(method = "checkFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/"
        + "Block;fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;"
        + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;F)V"))
    private void animatium$serverOnlyLanding(Block block, Level level, BlockState state, BlockPos pos, Entity entity,
                                             float fallDistance, Operation<Void> original) {
        if (!LegacyMobPhysics.skipsLandingEffects(entity)) {
            original.call(block, level, state, pos, entity, fallDistance);
        }
    }
}
