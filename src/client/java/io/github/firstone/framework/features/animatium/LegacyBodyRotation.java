package io.github.firstone.framework.features.animatium;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;

/**
 * 1.7.10 body rotation rules, used on the client to turn bodies like 1.7.10 did
 *
 * <p>1.7.10 had two algorithms:</p>
 * <ul>
 *   <li>{@link #turnTowardsMovement} — {@code EntityLivingBase.func_110146_f}, used by players and the mobs that still
 *       used the old AI in 1.7.10 ({@link #usesOldAi}). The body turns towards the movement direction, but never more
 *       than 75° away from the head. There is no special case for walking backwards, so the body turns sideways.</li>
 *   <li>{@link #followHead} — {@code EntityBodyHelper.updateRenderAngles}, used by mobs with the new AI. A moving mob
 *       faces where it walks; a standing mob keeps its body within the head limit, and after 10 still ticks the body
 *       slowly lines up with the head.</li>
 * </ul>
 */
public final class LegacyBodyRotation {

    /** Maximum angle between head and body for players and old-AI mobs (1.7.10 {@code 75.0F}) */
    private static final float MAX_HEAD_BODY_ANGLE = 75.0F;

    /** Movement below this squared distance per tick counts as standing still (same value in both versions) */
    private static final float MOVING_THRESHOLD_SQR = 0.0025000002F;

    /** Movement threshold used by {@code EntityBodyHelper} (same value in both versions) */
    private static final double HELPER_MOVING_THRESHOLD_SQR = 2.5000003E-7F;

    private LegacyBodyRotation() {}

    /**
     * Per-mob state of {@link #followHead} (1.7.10 {@code EntityBodyHelper} fields)
     */
    public static final class HelperState {
        int stillTicks;
        float lastStableHeadYaw;
    }

    /**
     * Tells whether this mob used the old AI in 1.7.10 (body follows movement like a player)
     *
     * <p>From 1.7.10 {@code isAIEnabled()}: spiders (and cave spiders), endermen, slimes (and magma cubes), ghasts,
     * blazes, silverfish, zombie pigmen, squids and giants. Their 1.21.1 subclasses (glow squid) behave the same.</p>
     *
     * @param mob any mob
     * @return true for old-AI mobs
     */
    public static boolean usesOldAi(Mob mob) {
        return mob instanceof Spider || mob instanceof EnderMan || mob instanceof Slime || mob instanceof Ghast
            || mob instanceof Blaze || mob instanceof Silverfish || mob instanceof ZombifiedPiglin
            || mob instanceof Squid || mob instanceof Giant;
    }

    /**
     * 1.7.10 {@code func_110146_f}: turns the body towards the movement direction, limited to 75° from the head
     *
     * @param entity     the entity to turn
     * @param walkAmount the walk value passed through by the caller
     * @return the walk value, negated when the entity moves more than 90° away from where it looks (as in 1.7.10)
     */
    public static float turnTowardsMovement(LivingEntity entity, float walkAmount) {
        float target = entity.yBodyRot;
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        if (dx * dx + dz * dz > MOVING_THRESHOLD_SQR) {
            target = (float) Math.atan2(dz, dx) * 180.0F / (float) Math.PI - 90.0F;
        }
        if (entity.attackAnim > 0.0F) {
            target = entity.getYRot();
        }

        entity.yBodyRot += Mth.wrapDegrees(target - entity.yBodyRot) * 0.3F;
        float headToBody = Mth.wrapDegrees(entity.getYRot() - entity.yBodyRot);
        boolean movingBackwards = headToBody < -90.0F || headToBody >= 90.0F;
        if (headToBody < -MAX_HEAD_BODY_ANGLE) {
            headToBody = -MAX_HEAD_BODY_ANGLE;
        }
        if (headToBody >= MAX_HEAD_BODY_ANGLE) {
            headToBody = MAX_HEAD_BODY_ANGLE;
        }
        entity.yBodyRot = entity.getYRot() - headToBody;
        if (headToBody * headToBody > 2500.0F) {
            entity.yBodyRot += headToBody * 0.2F;
        }
        return movingBackwards ? -walkAmount : walkAmount;
    }

    /**
     * 1.7.10 {@code EntityBodyHelper.updateRenderAngles}, with the mob's own head limit
     *
     * <p>1.7.10 used 75° for every mob; mobs added later keep their 1.21.1 limit
     * ({@link Mob#getMaxHeadYRot()}, 75 by default), so their special behavior (a goat that barely turns its head…)
     * stays while the timing is the 1.7.10 one.</p>
     *
     * @param mob   the mob to turn
     * @param state the mob's helper state
     */
    public static void followHead(Mob mob, HelperState state) {
        float limit = mob.getMaxHeadYRot();
        double dx = mob.getX() - mob.xo;
        double dz = mob.getZ() - mob.zo;
        if (dx * dx + dz * dz > HELPER_MOVING_THRESHOLD_SQR) {
            mob.yBodyRot = mob.getYRot();
            mob.yHeadRot = boundAngle(mob.yBodyRot, mob.yHeadRot, limit);
            state.lastStableHeadYaw = mob.yHeadRot;
            state.stillTicks = 0;
            return;
        }

        float bodyLimit = limit;
        if (Math.abs(mob.yHeadRot - state.lastStableHeadYaw) > 15.0F) {
            state.stillTicks = 0;
            state.lastStableHeadYaw = mob.yHeadRot;
        } else {
            state.stillTicks++;
            if (state.stillTicks > 10) {
                bodyLimit = Math.max(1.0F - (state.stillTicks - 10) / 10.0F, 0.0F) * limit;
            }
        }
        mob.yBodyRot = boundAngle(mob.yHeadRot, mob.yBodyRot, bodyLimit);
    }

    /**
     * 1.7.10 {@code computeAngleWithBound}: moves {@code angle} so it is at most {@code bound} degrees from {@code reference}
     */
    private static float boundAngle(float reference, float angle, float bound) {
        float difference = Mth.wrapDegrees(reference - angle);
        if (difference < -bound) {
            difference = -bound;
        }
        if (difference >= bound) {
            difference = bound;
        }
        return reference - difference;
    }
}
