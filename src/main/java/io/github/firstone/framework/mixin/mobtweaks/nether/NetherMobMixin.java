package io.github.firstone.framework.mixin.mobtweaks.nether;

import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Nether rules' hooks in piglins and hoglins ({@link NetherMobs})
 *
 * <p>When one spawns, whether a structure (a bastion) placed it is noted, so the rules leave it as in 1.21.1. After
 * its AI ran each tick, Attack Only in the Dark checks its target ({@link NetherMobs#afterAiStep}).</p>
 */
@Mixin({Piglin.class, Hoglin.class})
public class NetherMobMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mobtweaks$noteSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                     @Nullable SpawnGroupData data, CallbackInfoReturnable<SpawnGroupData> cir) {
        NetherMobs.noteSpawn((Mob) (Object) this, spawnType);
    }

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void mobtweaks$afterAiStep(CallbackInfo ci) {
        NetherMobs.afterAiStep((Mob) (Object) this);
    }
}
