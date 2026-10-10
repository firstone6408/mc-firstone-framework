package io.github.firstone.framework.client.mixin.cosmetics.death;

import io.github.firstone.framework.features.cosmetics.death.DeathEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the death effect with the player's files ({@link DeathEffect})
 *
 * <ul>
 *   <li>{@code makePoofParticles} — runs on the client when a dead body disappears (entity event 60)</li>
 *   <li>{@code tickDeath} — each tick of a dying entity; starts the death sound early when asked (the local player
 *       and the ender dragon have their own {@code tickDeath}: see {@code CosmeticSelfDeathMixin})</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class CosmeticDeathMixin {

    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void cosmetics$death(CallbackInfo ci) {
        if (DeathEffect.playDeath((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickDeath", at = @At("TAIL"))
    private void cosmetics$deathTick(CallbackInfo ci) {
        DeathEffect.tickDeath((LivingEntity) (Object) this);
    }
}
