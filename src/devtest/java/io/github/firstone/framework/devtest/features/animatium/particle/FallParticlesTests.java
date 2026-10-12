package io.github.firstone.framework.devtest.features.animatium.particle;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.mixin.ParticleAccessor;
import io.github.firstone.framework.devtest.mixin.ParticleEngineAccessor;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Tests of the 1.7.10 fall particles, part of the Animatium tests
 *
 * <p>A landing's particle packet, as the 1.21.1 server sends it ({@code LivingEntity.checkFallDamage}: block
 * particles from the entity's feet, no spread, speed 0.15, {@code 150 × d} of them), is passed to the client's own
 * packet handler, and the particles it makes are read before they first move, so the results are exact. They are
 * compared with 1.7.10 effect 2006 ({@code RenderGlobal.playAuxSFX}): from the middle of the top of the block under
 * the entity, sideways {@code 0.2 × s² × (d + 0.2)} ({@code s} from 0.75 to 1) at a random angle and
 * {@code 0.2 + d / 100} up. Real falls of players and a mob check the whole way from the server.</p>
 */
public final class FallParticlesTests {

    /** Where in the world the site's coordinates start */
    private static final BlockPos ORIGIN = TestSite.ORIGIN;

    /** Speed the 1.21.1 server gives the particles of a landing */
    private static final float LANDING_SPEED = 0.15F;

    /** Allowed difference of values computed the same way */
    private static final double EXACT = 1.0E-6;

    /** Share of pixels, in percent, that must differ between the 1.7.10 and the 1.21.1 view */
    private static final double VIEWS_DIFFER_PERCENT = 0.3;

    private FallParticlesTests() {}

    /**
     * Returns the fall particle tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Fall Particles: a landing throws 150 × d particles from the middle of the block's top, in a "
                + "flat ring at the 1.7.10 speeds", FallParticlesTests::ring),
            TestCase.of("Fall Particles: the block under the middle (1.7.10 y - 0.2): a slab's ring starts at the top "
                + "of its block; over air nothing", FallParticlesTests::whichBlock),
            TestCase.of("Fall Particles: off: the 1.21.1 particles from the feet", FallParticlesTests::off),
            TestCase.of("Fall Particles: other particle packets are left as they are",
                FallParticlesTests::otherPackets),
            TestCase.of("Fall Particles: 1.7.10 limits: within 16 blocks, Decreased leaves out a third, Minimal all",
                FallParticlesTests::limits),
            TestCase.of("Fall Particles: the dust rests on the ground (not sunk) and slides on; off: it stops (1.21.1)",
                FallParticlesTests::slides),
            TestCase.of("Fall Particles: real landings of a survival player, a creative player and a mob",
                FallParticlesTests::realLandings),
            TestCase.slow("Fall Particles: screenshots from above with Quake Pro FOV, on and off (to compare with "
                + "1.7.10)", FallParticlesTests::screenshots)
        );
    }

    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    // ---- packets ----------------------------------------------------------------------------------------------------

    /** 1.21.1 and 1.7.10 {@code d} after falling {@code blocks} past the safe distance */
    private static double strength(int blocks) {
        float fallen = blocks;
        return Math.min(0.2F + fallen / 15.0F, 2.5);
    }

    /** How many particles a landing makes ({@code 150 × d}) */
    private static int count(int blocks) {
        return (int) (150.0 * strength(blocks));
    }

    /** A landing's packet, as the 1.21.1 server sends it, at a site position */
    private static ClientboundLevelParticlesPacket landing(BlockState state, double x, double y, double z,
                                                           int count) {
        return packet(new BlockParticleOption(ParticleTypes.BLOCK, state), false, x, y, z, 0.0F, LANDING_SPEED, count);
    }

    private static ClientboundLevelParticlesPacket packet(ParticleOptions particle, boolean overrideLimiter, double x,
                                                          double y, double z, float spread, float speed, int count) {
        return new ClientboundLevelParticlesPacket(particle, overrideLimiter, ORIGIN.getX() + x, ORIGIN.getY() + y,
            ORIGIN.getZ() + z, spread, spread, spread, speed, count);
    }

    /** Passes a packet to the client's own handler; returns the particles it made, before they move */
    private static List<Particle> receive(TestContext t, ClientboundLevelParticlesPacket packet) {
        return t.onClientThread(() -> {
            Queue<Particle> made = ((ParticleEngineAccessor) t.mc().particleEngine).devtest$getParticlesToAdd();
            made.clear();
            ClientPacketListener connection = t.mc().getConnection();
            t.require(connection != null, "The client has no connection");
            connection.handleParticleEvent(packet);
            return new ArrayList<>(made);
        });
    }

    /** The 1.7.10 dust particles among some particles (the 1.21.1 block particle class is not one) */
    private static List<Particle> legacy(List<Particle> particles) {
        return particles.stream().filter(p -> p instanceof TerrainParticle && p.getClass() != TerrainParticle.class)
            .toList();
    }

    /** The 1.21.1 block particles among some particles */
    private static List<Particle> vanilla(List<Particle> particles) {
        return particles.stream().filter(p -> p.getClass() == TerrainParticle.class).toList();
    }

    private static ParticleAccessor read(Particle particle) {
        return (ParticleAccessor) particle;
    }

    /** Stands the player 3 blocks from the landing spot, well within 16 blocks */
    private static void standNear(TestContext t) {
        t.teleport(4.5, 0.0, 1.5, 0.0F, 30.0F);
        t.setFlying(true);
        t.ticks(2);
    }

    /** Runs a part of a test with a particle setting, then puts the setting back */
    private static void withParticles(TestContext t, ParticleStatus status, Runnable part) {
        ParticleStatus before = t.onClientThread(() -> t.mc().options.particles().get());
        t.onClientThread(() -> {
            t.mc().options.particles().set(status);
            return null;
        });
        try {
            part.run();
        } finally {
            t.onClientThread(() -> {
                t.mc().options.particles().set(before);
                return null;
            });
        }
    }

    // ---- the ring ---------------------------------------------------------------------------------------------------

    /** Checks a ring of dust thrown from the middle of a block's top (site coordinates) */
    private static void checkRing(TestContext t, String name, List<Particle> made, double x, double y, double z,
                                  int blocks) {
        double d = strength(blocks);
        List<Particle> dust = legacy(made);
        t.check(name + ": 1.7.10 dust particles (150 × d)", dust.size()).near(count(blocks), 0);
        t.check(name + ": 1.21.1 particles", vanilla(made).size()).near(0, 0);
        double farthest = 0.0;
        double lowestSideways = Double.MAX_VALUE;
        double highestSideways = 0.0;
        double upError = 0.0;
        int[] quarters = new int[4];
        for (Particle particle : dust) {
            ParticleAccessor p = read(particle);
            farthest = Math.max(farthest, Math.max(Math.abs(p.devtest$getX() - (ORIGIN.getX() + x)),
                Math.max(Math.abs(p.devtest$getY() - (ORIGIN.getY() + y)), Math.abs(p.devtest$getZ() - (ORIGIN.getZ()
                    + z)))));
            double sideways = Math.hypot(p.devtest$getXd(), p.devtest$getZd());
            lowestSideways = Math.min(lowestSideways, sideways);
            highestSideways = Math.max(highestSideways, sideways);
            upError = Math.max(upError, Math.abs(p.devtest$getYd() - (0.2F + d / 100.0)));
            quarters[(p.devtest$getXd() >= 0 ? 0 : 1) + (p.devtest$getZd() >= 0 ? 0 : 2)]++;
        }
        t.check(name + ": farthest start from the middle of the block's top", farthest).atMost(EXACT);
        t.check(name + ": speed up, from 0.2 + d / 100", upError).atMost(EXACT);
        t.check(name + ": lowest sideways speed (1.7.10: at least 0.2 × 0.75² × (d + 0.2))", lowestSideways)
            .atLeast(0.2F * 0.5625 * (d + 0.2) - EXACT);
        t.check(name + ": highest sideways speed (1.7.10: at most 0.2 × (d + 0.2))", highestSideways)
            .atMost(0.2F * (d + 0.2) + EXACT);
        int emptiest = Math.min(Math.min(quarters[0], quarters[1]), Math.min(quarters[2], quarters[3]));
        t.check(name + ": fewest particles thrown towards one quarter (all around)", emptiest).atLeast(1);
    }

    private static void ring(TestContext t) {
        standNear(t);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int blocks : new int[] {1, 2, 7, 40}) {
            List<Particle> made = receive(t, landing(stone, 4.3, 0.0, 4.7, count(blocks)));
            checkRing(t, "fell " + blocks + " past the safe distance", made, 4.5, 0.0, 4.5, blocks);
        }
    }

    private static void whichBlock(TestContext t) {
        standNear(t);
        // a slab: the feet are on its top (0.5), the block under them is the slab's, the ring starts at its top (1)
        t.site().set(4, 0, 4, "stone_slab[type=bottom]");
        t.site().set(8, 0, 4, "stone");
        t.ticks(3);
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState();
        checkRing(t, "on a slab", receive(t, landing(slab, 4.3, 0.5, 4.7, count(2))), 4.5, 1.0, 4.5, 2);

        // a pillar: 1.21.1 moves the point onto the edge of the pillar when the middle is over air beside it
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (double edge : new double[] {8.0, 9.0}) {
            List<Particle> made = receive(t, landing(stone, edge, 1.0, 4.5, count(2)));
            t.check("middle over air beside a pillar (x " + edge + "): 1.7.10 dust", legacy(made).size()).near(0, 0);
            t.check("middle over air beside a pillar (x " + edge + "): 1.21.1 particles", vanilla(made).size())
                .near(0, 0);
        }
        // exactly on a border of a flat floor: both sides have blocks, the ring is on the block at the point
        checkRing(t, "on a border of the floor", receive(t, landing(stone, 12.0, 0.0, 4.5, count(2))), 12.5, 0.0,
            4.5, 2);
    }

    private static void off(TestContext t) {
        standNear(t);
        config().legacyFallParticles = false;
        List<Particle> made = receive(t, landing(Blocks.STONE.defaultBlockState(), 4.3, 0.0, 4.7, count(7)));
        t.check("off: 1.7.10 dust", legacy(made).size()).near(0, 0);
        t.check("off: 1.21.1 particles (150 × d)", vanilla(made).size()).near(count(7), 0);
        double farthest = 0.0;
        for (Particle particle : vanilla(made)) {
            ParticleAccessor p = read(particle);
            farthest = Math.max(farthest, Math.hypot(p.devtest$getX() - (ORIGIN.getX() + 4.3),
                p.devtest$getZ() - (ORIGIN.getZ() + 4.7)));
        }
        t.check("off: farthest start from the feet (1.21.1: all at the feet)", farthest).atMost(EXACT);
    }

    private static void otherPackets(TestContext t) {
        standNear(t);
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        Object[][] packets = {
            {"spread 0.3 (the extra particles of a mace hit)", packet(stone, false, 4.3, 0.0, 4.7, 0.3F,
                LANDING_SPEED, 100)},
            {"a count no landing makes", packet(stone, false, 4.3, 0.0, 4.7, 0.0F, LANDING_SPEED, 37)},
            {"speed 0.05 (an armor stand breaking)", packet(stone, false, 4.3, 0.0, 4.7, 0.0F, 0.05F, 40)},
            {"forced to show", packet(stone, true, 4.3, 0.0, 4.7, 0.0F, LANDING_SPEED, 40)}
        };
        for (Object[] entry : packets) {
            ClientboundLevelParticlesPacket packet = (ClientboundLevelParticlesPacket) entry[1];
            List<Particle> made = receive(t, packet);
            t.check(entry[0] + ": 1.7.10 dust", legacy(made).size()).near(0, 0);
            t.check(entry[0] + ": 1.21.1 particles, as many as sent", vanilla(made).size()).near(packet.getCount(), 0);
        }
        List<Particle> clouds = receive(t, packet(ParticleTypes.CLOUD, false, 4.3, 0.0, 4.7, 0.0F, LANDING_SPEED, 40));
        t.check("cloud particles: as many as sent", clouds.size()).near(40, 0);
    }

    private static void limits(TestContext t) {
        standNear(t);
        BlockState stone = Blocks.STONE.defaultBlockState();
        int many = count(40);
        withParticles(t, ParticleStatus.DECREASED, () -> {
            int made = legacy(receive(t, landing(stone, 4.3, 0.0, 4.7, many))).size();
            // two thirds of 375 is 250; the random leaves it within a few dozen
            t.check("Decreased: dust particles (1.7.10: about two thirds)", made).between(210, 290);
        });
        withParticles(t, ParticleStatus.MINIMAL, () -> t.check("Minimal: dust particles (1.7.10: none)",
            legacy(receive(t, landing(stone, 4.3, 0.0, 4.7, many))).size()).near(0, 0));
        withParticles(t, ParticleStatus.ALL, () -> {
            // the floor block at z 20 is about 19 blocks from the player's eyes
            t.check("All, 19 blocks away: dust particles (1.7.10: none beyond 16)",
                legacy(receive(t, landing(stone, 4.3, 0.0, 20.7, many))).size()).near(0, 0);
            t.check("All, 3 blocks away: dust particles", legacy(receive(t, landing(stone, 4.3, 0.0, 4.7, many)))
                .size()).near(many, 0);
        });
    }

    // ---- moving on --------------------------------------------------------------------------------------------------

    /**
     * Lets landing particles move for 40 ticks; returns {particles seen landing, share still sliding from 2 to 5
     * ticks after landing, farthest rest height from 0.1 above the ground}
     */
    private static double[] afterLanding(TestContext t, List<Particle> made) {
        double ground = ORIGIN.getY();
        Map<Particle, Double> settledAt = new IdentityHashMap<>();
        Map<Particle, Integer> landedTick = new IdentityHashMap<>();
        int sliding = 0;
        int watched = 0;
        double restError = 0.0;
        for (int tick = 0; tick < 40; tick++) {
            t.tick();
            int now = tick;
            double[] result = t.onClientThread(() -> {
                double slid = 0;
                double seen = 0;
                double error = 0.0;
                for (Particle particle : made) {
                    if (!particle.isAlive()) {
                        continue;
                    }
                    ParticleAccessor p = read(particle);
                    double distance = Math.hypot(p.devtest$getX() - (ORIGIN.getX() + 4.5),
                        p.devtest$getZ() - (ORIGIN.getZ() + 4.5));
                    if (p.devtest$isOnGround() && !landedTick.containsKey(particle)) {
                        landedTick.put(particle, now);
                        error = Math.max(error, Math.abs(p.devtest$getY() - ground - 0.1));
                    } else if (landedTick.containsKey(particle) && landedTick.get(particle) == now - 2) {
                        settledAt.put(particle, distance);
                    } else if (landedTick.containsKey(particle) && landedTick.get(particle) == now - 5) {
                        seen++;
                        if (distance > settledAt.get(particle) + 0.001) {
                            slid++;
                        }
                    }
                }
                return new double[] {slid, seen, error};
            });
            sliding += (int) result[0];
            watched += (int) result[1];
            restError = Math.max(restError, result[2]);
        }
        return new double[] {landedTick.size(), watched == 0 ? 0.0 : (double) sliding / watched, restError};
    }

    private static void slides(TestContext t) {
        standNear(t);
        BlockState stone = Blocks.STONE.defaultBlockState();
        double[] on = afterLanding(t, legacy(receive(t, landing(stone, 4.5, 0.0, 4.5, count(40)))));
        t.check("on: dust particles seen landing", on[0]).atLeast(20);
        t.check("on: share still sliding outwards from 2 to 5 ticks after landing (1.7.10)", on[1]).atLeast(0.8);
        t.check("on: rest height from 0.1 above the ground (the middle of the particle, 1.7.10)", on[2])
            .atMost(EXACT);
        config().legacyFallParticles = false;
        double[] off = afterLanding(t, vanilla(receive(t, landing(stone, 4.5, 0.0, 4.5, count(40)))));
        t.check("off: share still sliding from 2 to 5 ticks after landing (1.21.1: they stop)", off[1]).atMost(0.05);
    }

    // ---- real landings ----------------------------------------------------------------------------------------------

    /** Counts the dust particles that appear within 40 ticks: {1.7.10 dust, 1.21.1 block particles} */
    private static int[] watchParticles(TestContext t) {
        Set<Particle> dust = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Particle> plain = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int tick = 0; tick < 40; tick++) {
            t.tick();
            for (TerrainParticle particle : t.onClientThread(() -> t.particles(TerrainParticle.class))) {
                (particle.getClass() == TerrainParticle.class ? plain : dust).add(particle);
            }
        }
        return new int[] {dust.size(), plain.size()};
    }

    /** Drops the player from 9.5 blocks (6.5 past the safe distance, so 7 counted); returns the particles seen */
    private static int[] playerLanding(TestContext t, GameType mode) {
        t.gameMode(mode);
        t.teleport(4.5, 9.5, 4.5, 0.0F, 60.0F);
        t.setFlying(false);
        t.clearParticles();
        return watchParticles(t);
    }

    /** Tells whether a number of particles is one a landing makes ({@code 150 × d} for some whole fall) */
    private static boolean landingCount(int particles) {
        for (int blocks = 1; blocks <= 40; blocks++) {
            if (count(blocks) == particles) {
                return true;
            }
        }
        return false;
    }

    /** Checks a real landing, on and off: the 1.7.10 dust as many as the 1.21.1 particles the same drop makes */
    private static void checkLanding(TestContext t, String name, int[] on, int[] off) {
        t.check(name + ", on: a landing's count of 1.7.10 dust (" + on[0] + ")", landingCount(on[0])).isTrue();
        t.check(name + ", on: 1.21.1 particles", on[1]).near(0, 0);
        t.check(name + ", off: 1.7.10 dust", off[0]).near(0, 0);
        t.check(name + ", off: 1.21.1 particles, as many as the dust on (one block of fall either way)",
            Math.abs(off[1] - on[0])).atMost(10);
    }

    /** Drops a cow from 6.5 blocks; returns the particles seen */
    private static int[] cowLanding(TestContext t, double x) {
        standNear(t);
        t.clearParticles();
        Spawned<Cow> cow = t.spawn(EntityType.COW, x, 6.5, 4.5, 0.0F, null);
        int[] seen = watchParticles(t);
        t.require(cow.isAlive(), "The cow did not survive its fall");
        return seen;
    }

    private static void realLandings(TestContext t) {
        int[] survival = playerLanding(t, GameType.SURVIVAL);
        int[] creative = playerLanding(t, GameType.CREATIVE);
        int[] mob = cowLanding(t, 8.5);
        config().legacyFallParticles = false;
        checkLanding(t, "survival player, fell 9.5", survival, playerLanding(t, GameType.SURVIVAL));
        checkLanding(t, "creative player, fell 9.5", creative, playerLanding(t, GameType.CREATIVE));
        checkLanding(t, "cow, fell 6.5", mob, cowLanding(t, 12.5));
    }

    // ---- screenshots ------------------------------------------------------------------------------------------------

    private static void screenshots(TestContext t) {
        int fov = t.onClientThread(() -> t.mc().options.fov().get());
        t.onClientThread(() -> {
            t.mc().options.fov().set(110);
            return null;
        });
        try {
            t.site().floor(0, 0, 9, 9, "grass_block");
            t.teleport(4.5, 5.0, 4.5, 0.0F, 90.0F);
            t.setFlying(true);
            t.mc().options.hideGui = true;
            t.ticks(5);
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            NativeImage[] frames = new NativeImage[2];
            for (int i = 0; i < 2; i++) {
                config().legacyFallParticles = i == 0;
                t.clearParticles();
                receive(t, landing(grass, 4.5, 0.0, 4.5, count(40)));
                t.ticks(6);
                frames[i] = t.captureFrame();
            }
            try {
                t.saveFrame("fall_particles_on", frames[0]);
                t.saveFrame("fall_particles_off", frames[1]);
                t.check("differing pixels between on and off, %", Images.differingPercent(frames[0], frames[1]))
                    .atLeast(VIEWS_DIFFER_PERCENT);
            } finally {
                frames[0].close();
                frames[1].close();
            }
        } finally {
            config().legacyFallParticles = true;
            t.onClientThread(() -> {
                t.mc().options.fov().set(fov);
                return null;
            });
        }
    }
}
