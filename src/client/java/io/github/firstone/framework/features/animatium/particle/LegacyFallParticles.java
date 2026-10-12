package io.github.firstone.framework.features.animatium.particle;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The 1.7.10 dust ring of an entity landing from a fall ({@code RenderGlobal.playAuxSFX}, effect 2006)
 *
 * <p>Both versions throw up {@code 150 × d} particles of the block an entity lands on after falling more than 3
 * blocks, with {@code d = min(0.2 + ceil(fall - 3) / 15, 2.5)}. 1.21.1 ({@code LivingEntity.checkFallDamage}) throws
 * them from the entity's feet in random directions, a puff around the feet. 1.7.10 threw them from the middle of the
 * block's top, outwards in a flat ring: each at a random angle, at {@code 0.2 × s² × (d + 0.2)} sideways
 * ({@code s} from 0.75 to 1) and {@code 0.2 + d / 100} up ({@link LegacyBlockDustParticle}).</p>
 *
 * <p>Only the client changes, so it works on any server: the server's particle packet of a landing is recognized
 * (block particles, all from one point, speed 0.15, a count of that formula; no other packet of the game looks like
 * this) and drawn the 1.7.10 way instead. As in 1.7.10, the block is the one under the middle of the entity
 * ({@code floor(y - 0.2)}), nothing is thrown when that is air, and particles are only made within 16 blocks of the
 * viewer, a third of them left out with "Decreased" particles and all of them with "Minimal".</p>
 */
public final class LegacyFallParticles {

    /** Speed the 1.21.1 server gives the particles of a landing */
    private static final float LANDING_SPEED = 0.15F;

    /** The largest {@code d} (1.7.10 and 1.21.1) */
    private static final double MAX_STRENGTH = 2.5;

    /** How far down from the feet the block under an entity is looked for (1.7.10 {@code posY - 0.2F}) */
    private static final float BLOCK_BELOW = 0.2F;

    /** How far from the viewer 1.7.10 made particles */
    private static final double PARTICLE_DISTANCE = 16.0;

    private LegacyFallParticles() {}

    /**
     * Draws the server's particles of a landing the 1.7.10 way, if the packet is one
     *
     * <p>Called on the client thread when a particle packet arrives ({@code handleParticleEvent}); the 1.21.1 particles
     * are left out when this returns true.</p>
     *
     * @param packet the particle packet
     * @return true if it was a landing and was handled here
     */
    public static boolean handle(ClientboundLevelParticlesPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (!AnimatiumFeature.getConfig().legacyFallParticles || level == null) {
            return false;
        }
        double strength = landingStrength(packet);
        if (strength < 0.0) {
            return false;
        }
        int y = Mth.floor(packet.getY() - BLOCK_BELOW);
        int x = column(level, packet.getX(), y, Mth.floor(packet.getZ()), true);
        int z = column(level, packet.getZ(), y, x, false);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.is(Blocks.MOVING_PISTON) && state.shouldSpawnTerrainParticles()) {
            throwRing(minecraft, level, pos, state, strength, packet.getCount());
        }
        return true;
    }

    /**
     * Returns the {@code d} of a landing from its particle packet
     *
     * @param packet a particle packet
     * @return {@code d}, or -1 if the packet is not the particles of a landing
     */
    static double landingStrength(ClientboundLevelParticlesPacket packet) {
        if (!(packet.getParticle() instanceof BlockParticleOption block) || block.getType() != ParticleTypes.BLOCK
            || packet.getCount() <= 0 || packet.getXDist() != 0.0F || packet.getYDist() != 0.0F
            || packet.getZDist() != 0.0F || packet.getMaxSpeed() != LANDING_SPEED || packet.isOverrideLimiter()) {
            return -1.0;
        }
        // the same sum as LivingEntity.checkFallDamage, for every whole number of blocks fallen past the safe distance
        for (int blocks = 1; ; blocks++) {
            float fallen = blocks;
            double strength = Math.min(0.2F + fallen / 15.0F, MAX_STRENGTH);
            if ((int) (150.0 * strength) == packet.getCount()) {
                return strength;
            }
            if (strength >= MAX_STRENGTH) {
                return -1.0;
            }
        }
    }

    /**
     * Returns the block column under the middle of the landed entity, along x or z
     *
     * <p>1.21.1 moves the point to the edge of the block an entity stands on when its middle is over another column; a
     * point exactly on a block border is that edge, and the entity's own column is the side with no block, where
     * 1.7.10 threw nothing.</p>
     */
    private static int column(ClientLevel level, double coordinate, int y, int other, boolean alongX) {
        int floor = Mth.floor(coordinate);
        if (coordinate != floor) {
            return floor;
        }
        BlockPos before = alongX ? new BlockPos(floor - 1, y, other) : new BlockPos(other, y, floor - 1);
        return level.getBlockState(before).isAir() ? floor - 1 : floor;
    }

    /** Throws the 1.7.10 ring of dust from the middle of the block's top */
    private static void throwRing(Minecraft minecraft, ClientLevel level, BlockPos pos, BlockState state,
                                  double strength, int count) {
        double x = pos.getX() + 0.5F;
        double y = pos.getY() + 1.0F;
        double z = pos.getZ() + 0.5F;
        boolean nearViewer = nearViewer(minecraft, x, y, z);
        ParticleStatus particles = minecraft.options.particles().get();
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            float angle = randomBetween(random, 0.0F, Mth.TWO_PI);
            double speed = randomBetween(random, 0.75F, 1.0F);
            double up = 0.2F + strength / 100.0;
            double sideways = 0.2F * speed * speed * (strength + 0.2);
            // 1.7.10 doSpawnParticle: "Decreased" leaves out a third, "Minimal" all, and nothing beyond 16 blocks
            if (particles == ParticleStatus.DECREASED && random.nextInt(3) == 0
                || particles == ParticleStatus.MINIMAL || !nearViewer) {
                continue;
            }
            minecraft.particleEngine.add(new LegacyBlockDustParticle(level, x, y, z, Mth.cos(angle) * sideways, up,
                Mth.sin(angle) * sideways, state, pos));
        }
    }

    /** 1.7.10 {@code MathHelper.randomFloatClamp} */
    private static float randomBetween(RandomSource random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    /** True within 16 blocks of the viewer (1.7.10: from the viewing entity's position, a player's eyes) */
    private static boolean nearViewer(Minecraft minecraft, double x, double y, double z) {
        Entity viewer = minecraft.getCameraEntity();
        if (viewer == null) {
            return false;
        }
        double viewerY = viewer instanceof Player ? viewer.getEyeY() : viewer.getY();
        double dx = viewer.getX() - x;
        double dy = viewerY - y;
        double dz = viewer.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= PARTICLE_DISTANCE * PARTICLE_DISTANCE;
    }
}
