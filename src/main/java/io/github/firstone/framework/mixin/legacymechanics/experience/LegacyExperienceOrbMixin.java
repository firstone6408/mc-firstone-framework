package io.github.firstone.framework.mixin.legacymechanics.experience;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.firstone.framework.features.legacymechanics.experience.LegacyExperience;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes experience orbs appear and move like 1.7.10 ({@link LegacyExperience})
 *
 * <ul>
 *   <li>The constructor with a position places a new orb 0.25 lower (on the server only; the client creates its
 *       orbs at the position the server sends).</li>
 *   <li>{@code award}, used for nearly all experience, does not merge new orbs into orbs already there.</li>
 *   <li>{@code tick} is replaced by the 1.7.10 movement, on both sides; the orb's age still counts and removes it
 *       after 5 minutes as in 1.21.1. Without {@code tick}, orbs do not merge with each other either.</li>
 * </ul>
 */
@Mixin(ExperienceOrb.class)
public class LegacyExperienceOrbMixin {

    /** Ticks before an orb disappears (both versions) */
    @Unique
    private static final int LIFETIME = 6000;

    @Shadow
    private int age;

    /** The orb's 1.7.10 state (when it last looked for a player, and which player) */
    @Unique
    private final LegacyExperience.OrbState legacymechanics$state = new LegacyExperience.OrbState();

    @ModifyArg(method = "<init>(Lnet/minecraft/world/level/Level;DDDI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;setPos(DDD)V"), index = 1)
    private static double legacymechanics$legacySpawnY(double y, @Local(argsOnly = true) Level level) {
        return LegacyExperience.spawnY(level, y);
    }

    @WrapOperation(method = "award", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;"
        + "tryMergeToExisting(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)Z"))
    private static boolean legacymechanics$noMerging(ServerLevel level, Vec3 position, int value,
                                                     Operation<Boolean> original) {
        // the merge itself adds the orb to the one already there, so it must not run at all
        return LegacyExperience.mayMerge() && original.call(level, position, value);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void legacymechanics$legacyTick(CallbackInfo ci) {
        if (!LegacyExperience.movesLikeLegacy()) {
            return;
        }
        ExperienceOrb orb = (ExperienceOrb) (Object) this;
        LegacyExperience.tick(orb, this.legacymechanics$state);
        this.age++;
        if (this.age >= LIFETIME) {
            orb.discard();
        }
        ci.cancel();
    }
}
