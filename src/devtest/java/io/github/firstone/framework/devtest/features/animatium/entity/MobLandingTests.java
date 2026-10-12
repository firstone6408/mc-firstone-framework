package io.github.firstone.framework.devtest.features.animatium.entity;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests of how mobs the 1.7.10 mob physics move on the client land, part of the Animatium tests
 *
 * <p>With "Legacy Mob Physics" the client moves mobs itself, so it also sees them land. The landing's effects belong to
 * the server; on the client a creeper used to swell from its fall ({@code Creeper.causeFallDamage}) and flash white,
 * which a 1.21.1 client never does. The creeper's swell is read on both sides every tick: the client's must stay 0
 * (no white flash), the server's still grows as in 1.21.1.</p>
 */
public final class MobLandingTests {

    /** Ticks a landing is watched */
    private static final int WATCH_TICKS = 40;

    private MobLandingTests() {}

    /**
     * Returns the mob landing tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Mob Landing: a creeper falling 3 blocks does not swell on the client (no white flash); the "
                + "server still swells it", MobLandingTests::creeperDrop),
            TestCase.of("Mob Landing: a creeper thrown off a 1-block ledge (like a sprint hit) does not flash",
                MobLandingTests::creeperThrown),
            TestCase.of("Mob Landing: Legacy Mob Physics off: no flash either (1.21.1)", MobLandingTests::physicsOff),
            TestCase.of("Mob Landing: a mob still takes fall damage from the server", MobLandingTests::fallDamage),
            TestCase.of("Mob Landing: 13 kinds of mobs and the player land without a flash on the client",
                MobLandingTests::allKinds)
        );
    }

    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    /** What a creeper's swell did while it was watched: {largest on the client, white ticks on the client, server} */
    private record Swell(double client, int whiteTicks, double server) {}

    /** Watches a creeper's swell on both sides for {@value #WATCH_TICKS} ticks */
    private static Swell watch(TestContext t, Spawned<Creeper> creeper) {
        double client = 0.0;
        int white = 0;
        double server = 0.0;
        for (int i = 0; i < WATCH_TICKS; i++) {
            t.tick();
            Creeper seen = creeper.client();
            if (seen != null) {
                float swelling = seen.getSwelling(1.0F);
                client = Math.max(client, swelling);
                // CreeperRenderer.getWhiteOverlayProgress: white on every odd tenth
                if ((int) (swelling * 10.0F) % 2 != 0) {
                    white++;
                }
            }
            server = Math.max(server, creeper.server(entity -> (double) entity.getSwelling(1.0F)));
        }
        return new Swell(client, white, server);
    }

    /** A creeper that does not chase the (creative) player, dropped from 3 blocks above the floor */
    private static Spawned<Creeper> droppedCreeper(TestContext t, double x) {
        t.difficulty(Difficulty.EASY);
        return t.spawn(EntityType.CREEPER, x, 3.0, 4.5, 0.0F, null);
    }

    private static void creeperDrop(TestContext t) {
        Swell swell = watch(t, droppedCreeper(t, 4.5));
        t.check("client: largest swell (none: a 1.21.1 client never swells it)", swell.client).near(0.0, 0.0);
        t.check("client: ticks drawn white", swell.whiteTicks).near(0, 0);
        t.check("server: largest swell (1.21.1: grows from the fall)", swell.server).atLeast(0.05);
    }

    private static void creeperThrown(TestContext t) {
        t.difficulty(Difficulty.EASY);
        t.site().fill(2, 0, 2, 6, 0, 6, "stone");
        Spawned<Creeper> creeper = t.spawn(EntityType.CREEPER, 4.5, 1.0, 4.5, 0.0F, null);
        t.ticks(5);
        // a sprint hit's knockback: thrown up and away, it lands one block lower, past the ledge
        t.onServer(level -> {
            Entity entity = level.getEntity(creeper.id());
            t.require(entity != null, "The creeper is gone");
            entity.setDeltaMovement(0.9, 0.5, 0.0);
            entity.hurtMarked = true;
            return null;
        });
        Swell swell = watch(t, creeper);
        t.check("client: largest swell", swell.client).near(0.0, 0.0);
        t.check("client: ticks drawn white", swell.whiteTicks).near(0, 0);
        t.check("server: largest swell (1.21.1: grows from the fall)", swell.server).atLeast(0.05);
    }

    private static void physicsOff(TestContext t) {
        config().legacyMobPhysics = false;
        Swell swell = watch(t, droppedCreeper(t, 4.5));
        t.check("client: largest swell", swell.client).near(0.0, 0.0);
        t.check("client: ticks drawn white", swell.whiteTicks).near(0, 0);
    }

    private static void fallDamage(TestContext t) {
        Spawned<Cow> cow = t.spawn(EntityType.COW, 4.5, 6.5, 4.5, 0.0F, null);
        t.ticks(WATCH_TICKS);
        t.check("server: the cow's health after falling 6.5 blocks (hurt)",
            cow.server(entity -> (double) entity.getHealth())).atMost(cow.server(entity -> (double) entity
            .getMaxHealth()) - 1.0);
        Cow seen = cow.client();
        t.require(seen != null, "The client has no cow");
        t.check("client: the cow's health (sent by the server)", seen.getHealth())
            .near(cow.server(entity -> (double) entity.getHealth()), 0.0);
    }

    /** Mobs of many kinds: hostile and passive, small and big, with and without their own fall rules */
    private static final EntityType<?>[] KINDS = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
        EntityType.CREEPER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME, EntityType.COW, EntityType.PIG,
        EntityType.HORSE, EntityType.WOLF, EntityType.VILLAGER, EntityType.IRON_GOLEM};

    /**
     * Drops one mob of each kind 2.5 blocks (no fall damage) and the player too; on the client none of them may look
     * hurt (red) or swell (white), as the server hurts none of them
     */
    private static void allKinds(TestContext t) {
        t.difficulty(Difficulty.EASY);
        // at night, so zombies and skeletons do not burn (the server would hurt them)
        t.command("time set midnight");
        List<Spawned<?>> mobs = new ArrayList<>();
        for (int i = 0; i < KINDS.length; i++) {
            mobs.add(t.spawn(KINDS[i], 2.5 + i * 3.0, 2.5, 4.5, 0.0F, null));
        }
        t.teleport(4.5, 2.5, 10.5, 180.0F, 0.0F);
        t.setFlying(false);
        int[] hurtTicks = new int[KINDS.length + 1];
        double swell = 0.0;
        for (int tick = 0; tick < WATCH_TICKS; tick++) {
            t.tick();
            for (int i = 0; i < KINDS.length; i++) {
                if (mobs.get(i).client() instanceof LivingEntity mob) {
                    hurtTicks[i] += mob.hurtTime > 0 ? 1 : 0;
                    if (mob instanceof Creeper creeper) {
                        swell = Math.max(swell, creeper.getSwelling(1.0F));
                    }
                }
            }
            hurtTicks[KINDS.length] += t.player().hurtTime > 0 ? 1 : 0;
        }
        for (int i = 0; i < KINDS.length; i++) {
            t.check(KINDS[i].getDescription().getString() + ": client ticks drawn hurt (red)", hurtTicks[i])
                .near(0, 0);
        }
        t.check("player: client ticks drawn hurt (red)", hurtTicks[KINDS.length]).near(0, 0);
        t.check("creeper: largest swell on the client (white)", swell).near(0.0, 0.0);
        boolean landed = t.onServer(level -> {
            for (Spawned<?> mob : mobs) {
                Entity entity = level.getEntity(mob.id());
                if (entity == null || !entity.onGround()) {
                    return false;
                }
            }
            return true;
        });
        t.require(landed, "Not every mob landed");
    }
}
