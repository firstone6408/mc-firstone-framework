package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

/**
 * Reads the particles made since the last tick, before they move; part of the dev test mod only
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {

    /**
     * Returns the particles waiting to join the others at the next tick
     *
     * @return {@code particlesToAdd}
     */
    @Accessor("particlesToAdd")
    Queue<Particle> devtest$getParticlesToAdd();
}
