package io.github.firstone.framework.features.legacymechanics.experience;

import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import io.github.firstone.framework.features.legacymechanics.physics.PushOutOfBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 1.7.10 experience orbs: when a dying mob drops them, where they appear and how they move
 *
 * <p>The amount of experience stays 1.21.1's; the rest follows 1.7.10:</p>
 * <ul>
 *   <li>A killed mob (or player) drops its orbs when its body disappears with the death smoke, 20 ticks after it
 *       died ({@code EntityLivingBase.onDeathUpdate}); 1.21.1 drops them at the moment of death.</li>
 *   <li>The position of a 1.7.10 {@code EntityXPOrb} was the middle of its box, so orbs appear 0.25 lower: orbs of a
 *       killed mob start half inside the ground and are kicked out of it ({@link PushOutOfBlocks}), which
 *       scatters them.</li>
 *   <li>Orbs move like 1.7.10 {@code EntityXPOrb.onUpdate}: they sink in water (1.21.1 floats them up), lava throws
 *       them up when their middle is in it, with a fizz, and they fly to the player's eyes (1.21.1: to the middle of
 *       the player), looking for a player within 8 blocks every tick or, for some orbs, after a short wait (1.21.1:
 *       once a second).</li>
 *   <li>Orbs never merge (1.21.1 merges orbs of the same value into one that is picked up several times).</li>
 *   <li>Experience from a furnace appears half a block above and south of the player, as in 1.7.10.</li>
 * </ul>
 */
public final class LegacyExperience {

    /** Half the height of an orb: how much lower a 1.7.10 orb appeared (its position was the middle of its box) */
    private static final double ORB_HALF_HEIGHT = EntityType.EXPERIENCE_ORB.getHeight() / 2.0;

    /** Gravity of an orb per tick (1.7.10: {@code motionY -= 0.03F}) */
    private static final float GRAVITY = 0.03F;

    /** Air friction of an orb per tick */
    private static final float AIR_FRICTION = 0.98F;

    /** How much of its falling speed an orb keeps, upwards, when it lands */
    private static final float BOUNCE = -0.9F;

    /** How far an orb looks for a player to fly to */
    private static final double FOLLOW_DISTANCE = 8.0;

    /** Where a furnace's experience appears, from the player's feet (1.7.10 {@code SlotFurnace}) */
    private static final Vec3 FURNACE_OFFSET = new Vec3(0.0, 0.5, 0.5);

    private LegacyExperience() {}

    private static boolean enabled() {
        return LegacyMechanicsFeature.getConfig().legacyExperience;
    }

    /**
     * Tells whether orbs move like 1.7.10, in place of 1.21.1's {@code ExperienceOrb.tick}
     *
     * @return true when "Legacy Experience" is on
     */
    public static boolean movesLikeLegacy() {
        return enabled();
    }

    /**
     * Returns the height a new orb appears at
     *
     * <p>Called when an orb is created with a position. Only on the server: the client creates orbs at the
     * position the server sends.</p>
     *
     * @param level the orb's level
     * @param y     the height asked for, which 1.21.1 uses as the bottom of the orb
     * @return 0.25 lower like 1.7.10 (the middle of the orb) when "Legacy Experience" is on, otherwise {@code y}
     */
    public static double spawnY(Level level, double y) {
        return enabled() && !level.isClientSide() ? y - ORB_HALF_HEIGHT : y;
    }

    /**
     * Tells whether a new orb may be merged into an orb already there (1.21.1 {@code tryMergeToExisting})
     *
     * @return false when "Legacy Experience" is on (1.7.10 never merged orbs)
     */
    public static boolean mayMerge() {
        return !enabled();
    }

    /**
     * Tells whether a dying entity keeps its experience until its body disappears
     *
     * <p>Called on the server when the entity drops its experience at the moment of death.</p>
     *
     * @param entity the entity
     * @return true when "Legacy Experience" is on and the entity is dying and still in the world
     */
    public static boolean delaysDeathExperience(LivingEntity entity) {
        return enabled() && entity.isDeadOrDying() && !entity.isRemoved();
    }

    /**
     * Returns where the experience of smelted items appears when the player takes them
     *
     * @param playerPosition the player's feet, which 1.21.1 uses
     * @return half a block higher and to the south like 1.7.10 when "Legacy Experience" is on, otherwise
     *         {@code playerPosition}
     */
    public static Vec3 furnaceExperiencePosition(Vec3 playerPosition) {
        return enabled() ? playerPosition.add(FURNACE_OFFSET) : playerPosition;
    }

    /**
     * The 1.7.10 state of one orb that 1.21.1 does not have: when it last looked for a player, and which player
     *
     * <p>Kept by each orb ({@code LegacyExperienceOrbMixin}) and only used by {@link LegacyExperience#tick}.</p>
     */
    public static final class OrbState {

        /** Ticks the orb has moved like 1.7.10 ({@code xpColor}) */
        private int ticks;

        /** {@link #ticks} when the orb last looked for a player ({@code xpTargetColor}) */
        private int lastSearch;

        /** The player the orb flies to ({@code closestPlayer}) */
        private @Nullable Player target;

        /**
         * Creates the state of a new orb (it has not looked for a player yet)
         */
        public OrbState() {}
    }

    /**
     * Moves an orb for one tick like 1.7.10 {@code EntityXPOrb.onUpdate}
     *
     * <p>Called on both sides in place of {@code ExperienceOrb.tick} while {@link #movesLikeLegacy()} is true; the
     * age of the orb and its removal after 5 minutes are left to the caller. Picking up stays 1.21.1's, and an orb
     * with the {@code NoGravity} tag (not in 1.7.10) does not fall, as in 1.21.1.</p>
     *
     * @param orb   the orb
     * @param state the orb's 1.7.10 state
     */
    public static void tick(ExperienceOrb orb, OrbState state) {
        orb.baseTick();
        orb.xo = orb.getX();
        orb.yo = orb.getY();
        orb.zo = orb.getZ();
        Vec3 motion = orb.getDeltaMovement();
        if (!orb.isNoGravity()) {
            orb.setDeltaMovement(motion.x, motion.y - GRAVITY, motion.z);
        }
        Level level = orb.level();
        double middleY = middleY(orb);
        if (level.getFluidState(BlockPos.containing(orb.getX(), middleY, orb.getZ())).is(FluidTags.LAVA)) {
            RandomSource random = orb.getRandom();
            orb.setDeltaMovement((random.nextFloat() - random.nextFloat()) * 0.2F, 0.2F,
                (random.nextFloat() - random.nextFloat()) * 0.2F);
            orb.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.4F, 2.0F + random.nextFloat() * 0.4F);
        }
        if (PushOutOfBlocks.isInBlock(orb)) {
            PushOutOfBlocks.push(orb, orb.getX(), middleY, orb.getZ());
        }
        flyToPlayer(orb, state, middleY);
        orb.move(MoverType.SELF, orb.getDeltaMovement());
        float friction = AIR_FRICTION;
        if (orb.onGround()) {
            // 1.7.10 takes the block under the orb's middle (1.21.1: the block the orb stands on)
            BlockPos below = BlockPos.containing(orb.getX(), Mth.floor(orb.getBoundingBox().minY) - 1, orb.getZ());
            friction = level.getBlockState(below).getBlock().getFriction() * AIR_FRICTION;
        }
        motion = orb.getDeltaMovement();
        orb.setDeltaMovement(motion.x * friction, motion.y * AIR_FRICTION, motion.z * friction);
        if (orb.onGround()) {
            motion = orb.getDeltaMovement();
            orb.setDeltaMovement(motion.x, motion.y * BOUNCE, motion.z);
        }
        state.ticks++;
    }

    /** The height of the middle of the orb's box: the position of a 1.7.10 orb */
    private static double middleY(ExperienceOrb orb) {
        AABB box = orb.getBoundingBox();
        return (box.minY + box.maxY) / 2.0;
    }

    /**
     * Looks for a player when it is time, then pulls the orb towards the player's eyes
     *
     * <p>1.7.10 looks again whenever {@code lastSearch < ticks - 20 + id % 100}: every tick for most orbs, after
     * up to 20 ticks for orbs whose id ends in 00 to 20. A player who is still within 8 blocks is kept.</p>
     */
    private static void flyToPlayer(ExperienceOrb orb, OrbState state, double middleY) {
        if (state.lastSearch < state.ticks - 20 + orb.getId() % 100) {
            if (state.target == null
                || state.target.distanceToSqr(orb.getX(), middleY, orb.getZ()) > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
                state.target = orb.level().getNearestPlayer(orb.getX(), middleY, orb.getZ(), FOLLOW_DISTANCE, false);
            }
            state.lastSearch = state.ticks;
        }
        Player target = state.target;
        // players who left or became spectators (not in 1.7.10) are not followed
        if (target != null && (target.isRemoved() || target.isSpectator())) {
            state.target = null;
            return;
        }
        if (target == null) {
            return;
        }
        double dx = (target.getX() - orb.getX()) / FOLLOW_DISTANCE;
        double dy = (target.getEyeY() - middleY) / FOLLOW_DISTANCE;
        double dz = (target.getZ() - orb.getZ()) / FOLLOW_DISTANCE;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double pull = 1.0 - distance;
        if (pull > 0.0 && distance > 0.0) {
            pull *= pull;
            orb.setDeltaMovement(orb.getDeltaMovement().add(dx / distance * pull * 0.1, dy / distance * pull * 0.1,
                dz / distance * pull * 0.1));
        }
    }
}
