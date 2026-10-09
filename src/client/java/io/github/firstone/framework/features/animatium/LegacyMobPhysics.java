package io.github.firstone.framework.features.animatium;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 1.7.10 client-side physics for mobs controlled by the server
 *
 * <p>1.7.10 {@code EntityLivingBase.onLivingUpdate} called {@code moveEntityWithHeading} on the client for every mob:
 * besides moving towards the positions sent by the server, the client also moved each mob with its own velocity
 * (gravity, friction, collisions). After a hit, the knockback velocity sent by the server made the mob jump right away
 * on the client, then the next position updates pulled it back to where the server had it. That tug-of-war is the
 * jerky knockback, sliding and pushing of 1.7.10. Other players were not simulated ({@code EntityOtherPlayerMP}).</p>
 *
 * <p>1.21.1 skips this for anything the client does not control ({@code isControlledByLocalInstance()} in
 * {@code travel}), so remote mobs only glide between server positions, which looks smooth.</p>
 */
public final class LegacyMobPhysics {

    /** Velocity components below this are set to zero each tick (1.7.10 value; 1.21.1 uses 0.003) */
    public static final double MIN_VELOCITY = 0.005;

    private LegacyMobPhysics() {}

    /**
     * Tells whether this entity should get 1.7.10 client-side physics
     *
     * @param entity any living entity
     * @return true for mobs controlled by the server, on the client, when the option is on
     */
    public static boolean appliesTo(LivingEntity entity) {
        return !(entity instanceof Player)
            && entity.level().isClientSide()
            && AnimatiumFeature.getConfig().legacyMobPhysics;
    }
}
