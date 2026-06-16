package io.github.firstone.framework.client.mixin.animatium.combat;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that filters out unwanted client-side particles
 *
 * <p>Particles such as {@code SWEEP_ATTACK} and {@code DAMAGE_INDICATOR} are sent by the server
 * in both singleplayer (through the integrated server) and multiplayer (over the network)
 * and arrive at {@link ClientLevel#addParticle(ParticleOptions, boolean, double, double, double, double, double, double)}</p>
 *
 * <p>This mixin intercepts that point to cancel unwanted particles
 * before they are passed on to {@code LevelRenderer}</p>
 *
 * <p>Target: {@link ClientLevel#addParticle(ParticleOptions, boolean, double, double, double, double, double, double)}</p>
 */
@Mixin(ClientLevel.class)
public class AnimatiumParticleFilterMixin {

    /**
     * Cancels particles sent by the server when the settings hide them
     *
     * <p>Checks the particle type against the config:</p>
     * <ul>
     *   <li>{@code noSweepEffect} — hides the {@code SWEEP_ATTACK} particle</li>
     *   <li>{@code noDamageIndicatorParticle} — hides the {@code DAMAGE_INDICATOR} particle</li>
     * </ul>
     *
     * @param particle    particle data to show
     * @param forceSpawn  force the particle to show even when far from the player
     * @param x           X position
     * @param y           Y position
     * @param z           Z position
     * @param dx          X velocity
     * @param dy          Y velocity
     * @param dz          Z velocity
     * @param ci          cancellable CallbackInfo
     */
    @Inject(
        method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddParticle(ParticleOptions particle, boolean forceSpawn,
                                double x, double y, double z,
                                double dx, double dy, double dz,
                                CallbackInfo ci) {
        AnimatiumConfig config = AnimatiumFeature.getConfig();
        if (config.noSweepEffect && particle == ParticleTypes.SWEEP_ATTACK) {
            ci.cancel();
            return;
        }
        if (config.noDamageIndicatorParticle && particle == ParticleTypes.DAMAGE_INDICATOR) {
            ci.cancel();
        }
    }
}
