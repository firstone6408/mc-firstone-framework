package io.github.firstone.framework.client.mixin.animatium.combat;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the combat particles added after 1.7.10: sweep attack and damage indicator (dark hearts)
 *
 * <p>Both particles are spawned by the server and reach the client through
 * {@code ClientPacketListener.handleParticleEvent} → {@code ClientLevel.addParticle}, in singleplayer and
 * on any multiplayer server. Both {@code addParticle} overloads are filtered.</p>
 */
@Mixin(ClientLevel.class)
public class LegacyCombatParticlesMixin {

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void animatium$filterParticle(ParticleOptions particle, double x, double y, double z,
                                          double dx, double dy, double dz, CallbackInfo ci) {
        if (animatium$isHidden(particle)) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void animatium$filterForcedParticle(ParticleOptions particle, boolean force, double x, double y, double z,
                                                double dx, double dy, double dz, CallbackInfo ci) {
        if (animatium$isHidden(particle)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean animatium$isHidden(ParticleOptions particle) {
        AnimatiumConfig config = AnimatiumFeature.getConfig();
        return (config.noSweepEffect && particle.getType() == ParticleTypes.SWEEP_ATTACK)
            || (config.noDamageIndicator && particle.getType() == ParticleTypes.DAMAGE_INDICATOR);
    }
}
