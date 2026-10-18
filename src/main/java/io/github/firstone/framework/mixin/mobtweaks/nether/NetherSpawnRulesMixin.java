package io.github.firstone.framework.mixin.mobtweaks.nether;

import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spawn Only in the Dark: refuses a natural spawn of a piglin or hoglin in bright light ({@link NetherMobs})
 *
 * <p>{@code SpawnPlacements.checkSpawnRules} is where the game asks every mob type's own spawn rules (the natural
 * spawner, chunk generation, spawners); only a spawn the game already allowed can be refused here, and only for
 * piglins and hoglins spawning naturally.</p>
 */
@Mixin(SpawnPlacements.class)
public class NetherSpawnRulesMixin {

    @Inject(method = "checkSpawnRules", at = @At("RETURN"), cancellable = true)
    private static <T extends Entity> void mobtweaks$onlyInDark(EntityType<T> type, ServerLevelAccessor level,
                                                               MobSpawnType spawnType, BlockPos pos,
                                                               RandomSource random,
                                                               CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && NetherMobs.blocksSpawn(type, level, spawnType, pos)) {
            cir.setReturnValue(false);
        }
    }
}
