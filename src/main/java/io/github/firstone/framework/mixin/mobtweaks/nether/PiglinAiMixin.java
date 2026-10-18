package io.github.firstone.framework.mixin.mobtweaks.nether;

import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Function;

/**
 * Attack Only in the Dark for piglins: they do not start attacking a player standing in the light unless they are
 * angry at them ({@link NetherMobs})
 *
 * <p>Only the function given to {@code StartAttacking} (idle and celebrating) is wrapped; the same function also
 * decides when a piglin stops attacking, and that use is left as in 1.21.1 (the end of a chase in the light comes from
 * {@code NetherMobs.afterAiStep}). {@code setAngerTarget} is where 1.21.1 makes a piglin angry (hit, one of its kind
 * hit, a chest or gold), which is noted as anger; piglin brutes are left out.</p>
 */
@Mixin(PiglinAi.class)
public class PiglinAiMixin {

    @ModifyArg(method = {"initIdleActivity", "initCelebrateActivity"}, require = 2, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/behavior/StartAttacking;create(Ljava/util/function/Predicate;Ljava/util/function/Function;)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"),
        index = 1)
    private static Function<Piglin, Optional<? extends LivingEntity>> mobtweaks$startOnlyInDark(
        Function<Piglin, Optional<? extends LivingEntity>> find) {
        return NetherMobs.startOnlyInDark(find);
    }

    @Inject(method = "setAngerTarget", at = @At("HEAD"))
    private static void mobtweaks$noteAnger(AbstractPiglin piglin, LivingEntity target, CallbackInfo ci) {
        if (piglin instanceof Piglin) {
            NetherMobs.noteAnger(piglin, target);
        }
    }
}
