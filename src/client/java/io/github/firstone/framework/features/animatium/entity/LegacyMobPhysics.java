package io.github.firstone.framework.features.animatium.entity;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.world.entity.Entity;
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
 *
 * <p>Moving the mobs also makes the client see them land ({@code Entity.checkFallDamage}), which a 1.21.1 client
 * never does. The landing's effects ({@code Block.fallOn}: fall damage, sounds, a creeper's swell) belong to the
 * server, which still has them all, so the client leaves them out ({@link #skipsLandingEffects}). A creeper's swell is
 * the one that showed: 1.7.10 also swelled it on the client, but drew a small swell as a barely visible white
 * (0.2 × swell), while 1.21.1 draws every odd step at least half white, so the creeper flashed white after a fall.</p>
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

    /**
     * Tells whether the client leaves out the effects of an entity landing, because the client only moves it for
     * these physics
     *
     * <p>Called on both sides when an entity lands; the effects stay on the server and for everything the client
     * controls itself (the player, a horse the player rides), which a 1.21.1 client also lands.</p>
     *
     * @param entity the entity that landed
     * @return true for a mob the client moves only because of these physics
     */
    public static boolean skipsLandingEffects(Entity entity) {
        return entity instanceof LivingEntity living && !living.isControlledByLocalInstance() && appliesTo(living);
    }
}
