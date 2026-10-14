package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads where a particle is and how it moves, so a test can check particles exactly; part of the dev test mod only
 */
@Mixin(Particle.class)
public interface ParticleAccessor {

    /**
     * Returns x
     *
     * @return {@code x}
     */
    @Accessor("x")
    double devtest$getX();

    /**
     * Returns y
     *
     * @return {@code y}
     */
    @Accessor("y")
    double devtest$getY();

    /**
     * Returns z
     *
     * @return {@code z}
     */
    @Accessor("z")
    double devtest$getZ();

    /**
     * Returns the motion along x
     *
     * @return {@code xd}
     */
    @Accessor("xd")
    double devtest$getXd();

    /**
     * Returns the motion along y
     *
     * @return {@code yd}
     */
    @Accessor("yd")
    double devtest$getYd();

    /**
     * Returns the motion along z
     *
     * @return {@code zd}
     */
    @Accessor("zd")
    double devtest$getZd();

    /**
     * Tells whether the particle stood on something after its last move
     *
     * @return {@code onGround}
     */
    @Accessor("onGround")
    boolean devtest$isOnGround();
}
