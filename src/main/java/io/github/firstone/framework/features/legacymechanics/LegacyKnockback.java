package io.github.firstone.framework.features.legacymechanics;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.10 knockback formulas, used on the server
 */
public final class LegacyKnockback {

    /** Knockback strength of a hit (1.7.10 {@code knockBack}: {@code var8 = 0.4F}) */
    private static final double STRENGTH = 0.4;

    /** Upward push of the attacker's extra knockback (1.7.10 {@code addVelocity(…, 0.1, …)}) */
    private static final double EXTRA_UP = 0.1;

    private LegacyKnockback() {}

    /**
     * Returns the entity 1.7.10 pushed a victim away from
     *
     * <ul>
     *   <li>Explosions: the entity that exploded (creeper, TNT) — 1.7.10 {@code DamageSource.setExplosionSource}.</li>
     *   <li>Everything else: the responsible entity (for arrows and potions, the shooter/thrower); if there is none,
     *       the projectile itself (1.7.10 {@code causeArrowDamage(arrow, arrow)} for arrows without a shooter).</li>
     * </ul>
     *
     * @param source the damage source
     * @return the entity to push away from, or null for no knockback (fall, fire, cactus…)
     */
    public static Entity attackerOf(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
        }
        return source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
    }

    /**
     * Rolls knockback resistance like 1.7.10: passes with probability {@code 1 − resistance}
     *
     * @param victim the entity that was hit
     * @return true if the knockback (or the velocity update) happens
     */
    public static boolean passesResistanceRoll(LivingEntity victim) {
        return victim.getRandom().nextDouble() >= victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
    }

    /**
     * Knocks back an entity that was hit, like 1.7.10 {@code EntityLivingBase.attackEntityFrom} + {@code knockBack}
     *
     * <ul>
     *   <li>No attacker entity → no knockback.</li>
     *   <li>Direction: away from the attacker ({@link #attackerOf}; for arrows, the shooter).</li>
     *   <li>Knockback resistance is a chance to ignore the knockback entirely.</li>
     *   <li>Velocity is halved, then pushed 0.4 away and 0.4 up, with the upward velocity capped at 0.4 — also in the air.</li>
     *   <li>A hit player gets the new velocity right away, before the server runs its movement (see {@code sendNow}).</li>
     * </ul>
     *
     * @param victim   the entity that was hit
     * @param attacker the entity responsible for the damage, or null
     */
    public static void knockBack(LivingEntity victim, Entity attacker) {
        if (attacker == null) {
            return;
        }
        double dx = attacker.getX() - victim.getX();
        double dz = attacker.getZ() - victim.getZ();
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        if (!passesResistanceRoll(victim)) {
            return;
        }
        double distance = Math.sqrt(dx * dx + dz * dz);
        Vec3 motion = victim.getDeltaMovement();
        double y = Math.min(STRENGTH, motion.y / 2.0 + STRENGTH);
        victim.setDeltaMovement(motion.x / 2.0 - dx / distance * STRENGTH, y, motion.z / 2.0 - dz / distance * STRENGTH);
        victim.hasImpulse = true;
        if (victim.hurtMarked) {
            sendNow(victim);
        }
    }

    /**
     * Applies the attacker's extra knockback like 1.7.10 {@code attackTargetEntityWithCurrentItem}
     *
     * <p>{@code addVelocity(-sin(yaw)·k·0.5, 0.1, cos(yaw)·k·0.5)}: a plain push, not reduced by knockback resistance.</p>
     *
     * @param target   the entity that was hit
     * @param strength {@code k · 0.5}, as passed by 1.21.1
     * @param sinYaw   {@code sin(attacker yaw)}
     * @param negCosYaw {@code -cos(attacker yaw)}
     */
    public static void attackerPush(LivingEntity target, double strength, double sinYaw, double negCosYaw) {
        target.push(-sinYaw * strength, EXTRA_UP, -negCosYaw * strength);
        sendNow(target);
    }

    /**
     * Sends a player's velocity to itself and to everyone tracking it right away, like 1.7.10
     *
     * <p>1.7.10 sent the knockback velocity ({@code velocityChanged}) from the entity tracker in the same tick, before
     * the player's own movement was processed. 1.21.1 sends it at the start of the next tick, after the server has
     * already run one tick of the player's physics on it (ground friction ×0.546, gravity), so a hit player only gets
     * about half of the knockback. Only players are affected: other entities are moved by the server itself.</p>
     *
     * @param entity the entity whose velocity changed
     */
    private static void sendNow(LivingEntity entity) {
        if (entity instanceof ServerPlayer && entity.level() instanceof ServerLevel level) {
            level.getChunkSource().broadcastAndSend(entity, new ClientboundSetEntityMotionPacket(entity));
            entity.hurtMarked = false;
        }
    }
}
