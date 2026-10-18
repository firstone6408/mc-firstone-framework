package io.github.firstone.framework.mixin.mobtweaks.nether;

import io.github.firstone.framework.features.mobtweaks.nether.NetherMobs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.hoglin.HoglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Function;

/**
 * Attack Only in the Dark for hoglins: they do not start attacking a player standing in the light unless they are
 * angry at them ({@link NetherMobs})
 *
 * <p>The function given to {@code StartAttacking} is wrapped (hoglins use it only to start); {@code wasHurtBy} is
 * where a hoglin learns it was hit, which is noted as anger for it and the hoglins around it;
 * {@code broadcastAttackTarget} is where it calls the hoglins it sees, which take on its anger.</p>
 */
@Mixin(HoglinAi.class)
public class HoglinAiMixin {

    @ModifyArg(method = "initIdleActivity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/ai/behavior/StartAttacking;create(Ljava/util/function/Function;)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"))
    private static Function<Hoglin, Optional<? extends LivingEntity>> mobtweaks$startOnlyInDark(
        Function<Hoglin, Optional<? extends LivingEntity>> find) {
        return NetherMobs.startOnlyInDark(find);
    }

    @Inject(method = "wasHurtBy", at = @At("HEAD"))
    private static void mobtweaks$noteAnger(Hoglin hoglin, LivingEntity attacker, CallbackInfo ci) {
        NetherMobs.noteHoglinHurt(hoglin, attacker);
    }

    @Inject(method = "broadcastAttackTarget", at = @At("HEAD"))
    private static void mobtweaks$shareAnger(Hoglin hoglin, LivingEntity target, CallbackInfo ci) {
        NetherMobs.shareHoglinAnger(hoglin, target);
    }
}
