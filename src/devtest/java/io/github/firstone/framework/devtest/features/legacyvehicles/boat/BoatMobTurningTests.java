package io.github.firstone.framework.devtest.features.legacyvehicles.boat;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesConfig;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.List;

/**
 * Tests of "Mobs Turn in Boats", part of the Legacy Vehicles tests
 *
 * <p>1.21.1 {@code Boat.positionRider} locks a passenger's body to the boat's front and sets its head to its yaw,
 * kept within 105 degrees of the front; the server does it too before it sends the head. With the option on, this
 * client keeps a mob's head as received (like on the ground, so it does not shake with "Legacy Entity Sync"), lets the
 * body follow it and keeps the head within the mob's neck ({@code getMaxHeadYRot}: 75 for a pig). The client's copy
 * of the boat places its pigs by hand inside one client task, so those results are exact; other tests follow pigs
 * turned on the server over real ticks.</p>
 */
public final class BoatMobTurningTests {

    /** A pig's neck: how far its head may turn from its body */
    private static final double NECK = 75.0;

    /** Allowed difference of angles worked out on the client */
    private static final double EXACT = 1.0E-3;

    /** Allowed difference of angles sent by the server (they travel as 1/256 of a turn) */
    private static final double SENT = 1.5;

    private BoatMobTurningTests() {}

    /**
     * Returns the tests of mobs turning in boats
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Mobs Turn in Boats: the body follows the head, within the neck; off: locked to the boat's "
                + "front (1.21.1)", BoatMobTurningTests::bodyFollowsHead),
            TestCase.of("Mobs Turn in Boats: the head never looks backwards from the body",
                BoatMobTurningTests::neverBackwards),
            TestCase.of("Mobs Turn in Boats: a head set at once (Legacy Entity Sync) is not pulled back to the yaw; "
                + "off: pulled back (1.21.1)", BoatMobTurningTests::headKept),
            TestCase.of("Mobs Turn in Boats: with Legacy Entity Sync a turning head never moves backwards (no "
                + "shaking); off: it does", BoatMobTurningTests::noShaking),
            TestCase.of("Mobs Turn in Boats: over real ticks the body turns to the head the server sends; off: "
                + "locked", BoatMobTurningTests::overTicks),
            TestCase.of("Mobs Turn in Boats: the body waits, then follows the head, in time with a mob on the ground "
                + "(Legacy Entity Sync on and off)", BoatMobTurningTests::followsLikeOnGround),
            TestCase.of("Mobs Turn in Boats: the body waits the same with the 1.21.1 body rules and in a moving boat",
                BoatMobTurningTests::followsLikeOnGroundOtherwise),
            TestCase.of("Mobs Turn in Boats: two animals turned sideways by the server keep their head within the neck",
                BoatMobTurningTests::twoAnimals),
            TestCase.of("Mobs Turn in Boats: players are not changed (their look is Legacy Riding's)",
                BoatMobTurningTests::players)
        );
    }

    private static LegacyVehiclesConfig config() {
        return LegacyVehiclesFeature.getConfig();
    }

    /**
     * A boat on the floor with still pigs in it; returns the ids of the boat and the pigs
     *
     * <p>The boat stands off the 1/32 block grid, as boats do in play: with "Legacy Entity Sync" the positions the
     * server sends are rounded to that grid.</p>
     */
    private static int[] boatWithPigs(TestContext t, int pigs) {
        Spawned<Boat> boat = t.spawn(EntityType.BOAT, 4.517, 0.0, 4.483, 30.0F, null);
        int[] ids = t.onServer(level -> {
            Entity entity = level.getEntity(boat.id());
            t.require(entity instanceof Boat, "The boat is gone");
            int[] found = new int[pigs + 1];
            found[0] = entity.getId();
            for (int i = 0; i < pigs; i++) {
                Pig pig = EntityType.PIG.create(level);
                t.require(pig != null, "Cannot create a pig");
                pig.setNoAi(true);
                pig.moveTo(entity.position());
                level.addFreshEntity(pig);
                t.require(pig.startRiding(entity, true), "The pig did not get in");
                found[i + 1] = pig.getId();
            }
            return found;
        });
        t.waitUntil("the client sees the pigs in the boat", () -> {
            Entity last = t.level().getEntity(ids[ids.length - 1]);
            return last != null && last.getVehicle() != null;
        }, 40);
        t.ticks(3);
        return ids;
    }

    /** The client's copy of an entity */
    private static Entity client(TestContext t, int id) {
        Entity entity = t.level().getEntity(id);
        t.require(entity != null, "The client has no entity " + id);
        return entity;
    }

    /** {head, body} of a passenger, each from the boat's front */
    private static double[] fromBoat(Boat boat, LivingEntity passenger) {
        return new double[] {Mth.wrapDegrees(passenger.getYHeadRot() - boat.getYRot()),
            Mth.wrapDegrees(passenger.yBodyRot - boat.getYRot())};
    }

    /** On the client: sets the pig's yaw and head, and its body, from the boat's front; the boat places it once */
    private static double[] place(TestContext t, int[] ids, int pig, float head, float body) {
        return t.onClientThread(() -> {
            Boat boat = (Boat) client(t, ids[0]);
            Pig passenger = (Pig) client(t, ids[pig]);
            passenger.setYRot(boat.getYRot() + head);
            passenger.yRotO = passenger.getYRot();
            passenger.setYHeadRot(boat.getYRot() + head);
            passenger.yBodyRot = boat.getYRot() + body;
            boat.positionRider(passenger);
            return fromBoat(boat, passenger);
        });
    }

    private static void bodyFollowsHead(TestContext t) {
        int[] ids = boatWithPigs(t, 1);
        double[] on = place(t, ids, 1, 100.0F, 0.0F);
        t.check("on: head from the boat (as the server's rule puts it)", on[0]).near(100.0, EXACT);
        t.check("on: body turned just enough for the neck (100 - 75)", on[1]).near(100.0 - NECK, EXACT);
        double[] inNeck = place(t, ids, 1, 60.0F, 20.0F);
        t.check("on: a body already within the neck is kept", inNeck[1]).near(20.0, EXACT);
        config().mobsTurnInBoats = false;
        double[] off = place(t, ids, 1, 100.0F, 60.0F);
        t.check("off: head from the boat", off[0]).near(100.0, EXACT);
        t.check("off: body locked to the boat's front (1.21.1)", off[1]).near(0.0, EXACT);
    }

    private static void neverBackwards(TestContext t) {
        int[] ids = boatWithPigs(t, 1);
        for (float body : new float[] {-170.0F, 180.0F, -90.0F, 100.0F}) {
            double[] placed = place(t, ids, 1, 100.0F, body);
            t.check("body at " + body + ": head from the body, at most the neck",
                Math.abs(Mth.wrapDegrees(placed[0] - placed[1]))).atMost(NECK + EXACT);
        }
    }

    private static void headKept(TestContext t) {
        int[] ids = boatWithPigs(t, 1);
        // the yaw is still on its way (smoothed), the head was just set to where the server says
        double[] on = t.onClientThread(() -> placeHeadAhead(t, ids));
        t.check("on: head from the boat (kept as received)", on[0]).near(40.0, EXACT);
        config().mobsTurnInBoats = false;
        double[] off = t.onClientThread(() -> placeHeadAhead(t, ids));
        t.check("off: head from the boat (1.21.1: pulled back to the yaw)", off[0]).near(10.0, EXACT);
    }

    /** On the client: yaw 10 and head 40 from the boat's front; the boat places the pig once; returns {head, body} */
    private static double[] placeHeadAhead(TestContext t, int[] ids) {
        Boat boat = (Boat) client(t, ids[0]);
        Pig pig = (Pig) client(t, ids[1]);
        pig.setYRot(boat.getYRot() + 10.0F);
        pig.yRotO = pig.getYRot();
        pig.setYHeadRot(boat.getYRot() + 40.0F);
        pig.yBodyRot = boat.getYRot() + 40.0F;
        boat.positionRider(pig);
        return fromBoat(boat, pig);
    }

    /**
     * With "Legacy Entity Sync" on, the server turns the pig 3 degrees a tick for 40 ticks; returns how many client
     * ticks the head moved backwards within the tick (from {@code yHeadRotO} to {@code yHeadRot}), which is what is
     * drawn between two ticks
     */
    private static int backwardTicks(TestContext t, int[] ids) {
        int backwards = 0;
        for (int i = 0; i < 40; i++) {
            float turn = -60.0F + i * 3.0F;
            t.onServer(level -> {
                Entity boat = level.getEntity(ids[0]);
                Entity pig = level.getEntity(ids[1]);
                t.require(boat instanceof Boat && pig instanceof Pig, "The boat or the pig is gone");
                pig.setYRot(boat.getYRot() + turn);
                pig.setYHeadRot(boat.getYRot() + turn);
                return null;
            });
            t.tick();
            double step = t.onClientThread(() -> {
                Pig pig = (Pig) client(t, ids[1]);
                return (double) Mth.wrapDegrees(pig.getYHeadRot() - pig.yHeadRotO);
            });
            if (step < -0.01) {
                backwards++;
            }
        }
        return backwards;
    }

    private static void noShaking(TestContext t) {
        AnimatiumConfig animatium = AnimatiumFeature.getConfig();
        boolean sync = animatium.legacyEntitySync;
        animatium.legacyEntitySync = true;
        try {
            int[] ids = boatWithPigs(t, 1);
            t.check("on: ticks the head moved backwards while turning", backwardTicks(t, ids)).near(0, 0);
            config().mobsTurnInBoats = false;
            t.check("off: ticks the head moved backwards while turning (1.21.1 shakes)", backwardTicks(t, ids))
                .atLeast(1);
        } finally {
            animatium.legacyEntitySync = sync;
        }
    }

    /** The server turns the still pig; after 30 ticks returns the client's {head, body} from the boat */
    private static double[] turnedOnServer(TestContext t, int[] ids, float turn) {
        t.onServer(level -> {
            Entity boat = level.getEntity(ids[0]);
            Entity pig = level.getEntity(ids[1]);
            t.require(boat instanceof Boat && pig instanceof Pig, "The boat or the pig is gone");
            float yaw = boat.getYRot() + turn;
            pig.setYRot(yaw);
            pig.setYHeadRot(yaw);
            return null;
        });
        t.ticks(30);
        return t.onClientThread(() -> fromBoat((Boat) client(t, ids[0]), (Pig) client(t, ids[1])));
    }

    private static void overTicks(TestContext t) {
        int[] ids = boatWithPigs(t, 1);
        double[] on = turnedOnServer(t, ids, 100.0F);
        t.check("on: client head from the boat (as the server sends it)", on[0]).near(100.0, SENT);
        t.check("on: client head from the body, at most the neck", Math.abs(Mth.wrapDegrees(on[0] - on[1])))
            .atMost(NECK + SENT);
        t.check("on: the body turned away from the boat's front", on[1]).atLeast(100.0 - NECK - SENT);
        double[] past = turnedOnServer(t, ids, 150.0F);
        t.check("on, turned 150 on the server: client head from the boat (the server's rule: 105)", past[0])
            .near(105.0, SENT);
        t.check("on, turned 150 on the server: head from the body, at most the neck",
            Math.abs(Mth.wrapDegrees(past[0] - past[1]))).atMost(NECK + SENT);
        config().mobsTurnInBoats = false;
        double[] off = turnedOnServer(t, ids, 100.0F);
        t.check("off: client head from the boat", off[0]).near(100.0, SENT);
        t.check("off: client body locked to the boat's front (1.21.1)", off[1]).near(0.0, SENT);
    }

    /** How long the head stays turned while the bodies are watched, in ticks */
    private static final int WATCH_TICKS = 25;

    /** A tick by which a body that waits like on the ground has not followed the head yet */
    private static final int STILL_WAITING_TICK = 5;

    /** Largest difference of body lag between the pig in the boat and the pig on the ground, in degrees */
    private static final double SAME_TIMING = 3.0;

    /**
     * A pig in a boat and a pig on the ground, both facing the boat's front; the server turns both heads 60 degrees
     * at once (moving the boat along x each tick when asked), then for {@value #WATCH_TICKS} client ticks returns how
     * far each body is behind its head: {boat pig, ground pig} per tick
     */
    private static double[][] bodyLag(TestContext t, boolean movingBoat) {
        int[] ids = boatWithPigs(t, 1);
        Spawned<Pig> ground = t.spawn(EntityType.PIG, 8.5, 0.0, 9.5, 30.0F, pig -> pig.setNoAi(true));
        turnBoth(t, ids, ground.id(), 0.0F, 0.0);
        t.ticks(30);
        double[][] lag = new double[WATCH_TICKS][];
        turnBoth(t, ids, ground.id(), 60.0F, 0.0);
        for (int i = 0; i < WATCH_TICKS; i++) {
            if (movingBoat) {
                turnBoth(t, ids, ground.id(), 60.0F, 0.05);
            }
            t.tick();
            lag[i] = t.onClientThread(() -> new double[] {lagOf((Pig) client(t, ids[1])),
                lagOf((Pig) client(t, ground.id()))});
        }
        t.removeEntities();
        return lag;
    }

    /** On the server: both pigs' yaw and head to {@code turn} from the boat's front; the boat moves by {@code move} */
    private static void turnBoth(TestContext t, int[] ids, int ground, float turn, double move) {
        t.onServer(level -> {
            Entity boat = level.getEntity(ids[0]);
            Entity inBoat = level.getEntity(ids[1]);
            Entity onGround = level.getEntity(ground);
            t.require(boat instanceof Boat && inBoat instanceof Pig && onGround instanceof Pig, "A pig is gone");
            boat.setPos(boat.getX() + move, boat.getY(), boat.getZ());
            float yaw = boat.getYRot() + turn;
            for (Entity pig : new Entity[] {inBoat, onGround}) {
                pig.setYRot(yaw);
                pig.setYHeadRot(yaw);
            }
            return null;
        });
    }

    /** How far a mob's body is behind its head, in degrees */
    private static double lagOf(LivingEntity mob) {
        return Math.abs(Mth.wrapDegrees(mob.getYHeadRot() - mob.yBodyRot));
    }

    /** The first tick a body is behind its head: when the turned head reached the client */
    private static int firstLag(double[][] lag, int pig) {
        for (int i = 0; i < lag.length; i++) {
            if (lag[i][pig] > 1.0) {
                return i;
            }
        }
        return lag.length;
    }

    /**
     * Checks that the boat pig's body waits, then follows, in time with the ground pig's, each counted from the tick
     * its turned head reached the client (the two heads may arrive a tick apart)
     */
    private static void checkSameTiming(TestContext t, String name, double[][] lag) {
        int boatStart = firstLag(lag, 0);
        int groundStart = firstLag(lag, 1);
        t.check(name + ": boat pig's body still behind the head " + STILL_WAITING_TICK + " ticks after the turn",
            lag[boatStart + STILL_WAITING_TICK][0]).atLeast(50.0);
        t.check(name + ": ground pig's body still behind the head " + STILL_WAITING_TICK + " ticks after the turn",
            lag[groundStart + STILL_WAITING_TICK][1]).atLeast(50.0);
        double largest = 0.0;
        int compared = 0;
        for (int i = 0; boatStart + i < lag.length && groundStart + i < lag.length; i++) {
            largest = Math.max(largest, Math.abs(lag[boatStart + i][0] - lag[groundStart + i][1]));
            compared++;
        }
        t.check(name + ": ticks compared from each turn", compared).atLeast(WATCH_TICKS - 5);
        t.check(name + ": largest difference from the ground pig, from each turn", largest).atMost(SAME_TIMING);
        t.check(name + ": boat pig's body caught up with the head at the end", lag[WATCH_TICKS - 1][0])
            .atMost(SAME_TIMING);
    }

    /** Runs a part with "Legacy Entity Sync" and Animatium's body rules set, then puts them back */
    private static void withAnimatium(boolean sync, boolean bodyRules, Runnable part) {
        AnimatiumConfig animatium = AnimatiumFeature.getConfig();
        boolean syncBefore = animatium.legacyEntitySync;
        boolean bodyBefore = animatium.legacyBodyRotation;
        animatium.legacyEntitySync = sync;
        animatium.legacyBodyRotation = bodyRules;
        try {
            part.run();
        } finally {
            animatium.legacyEntitySync = syncBefore;
            animatium.legacyBodyRotation = bodyBefore;
        }
    }

    private static void followsLikeOnGround(TestContext t) {
        withAnimatium(true, true, () -> checkSameTiming(t, "Legacy Entity Sync on", bodyLag(t, false)));
        withAnimatium(false, true, () -> checkSameTiming(t, "Legacy Entity Sync off", bodyLag(t, false)));
    }

    private static void followsLikeOnGroundOtherwise(TestContext t) {
        withAnimatium(true, false, () -> checkSameTiming(t, "1.21.1 body rules, sync on", bodyLag(t, false)));
        withAnimatium(true, true, () -> checkSameTiming(t, "moving boat, sync on", bodyLag(t, true)));
        withAnimatium(false, true, () -> checkSameTiming(t, "moving boat, sync off", bodyLag(t, true)));
    }

    private static void twoAnimals(TestContext t) {
        int[] ids = boatWithPigs(t, 2);
        // the server turns two animals' heads 90 or 270 degrees from their yaw (1.21.1); the client shows that head
        t.ticks(10);
        for (int pig = 1; pig <= 2; pig++) {
            int index = pig;
            double server = t.onServer(level -> {
                Entity boat = level.getEntity(ids[0]);
                Entity passenger = level.getEntity(ids[index]);
                t.require(boat != null && passenger instanceof Pig, "The boat or the pig is gone");
                return (double) Mth.wrapDegrees(passenger.getYHeadRot() - passenger.getYRot());
            });
            t.check("pig " + pig + ": the server turned the head sideways from the yaw (1.21.1)", Math.abs(server))
                .near(90.0, EXACT);
            double[] seen = t.onClientThread(() -> {
                Pig passenger = (Pig) client(t, ids[index]);
                return new double[] {Mth.wrapDegrees(passenger.getYHeadRot() - passenger.getYRot()),
                    Mth.wrapDegrees(passenger.getYHeadRot() - passenger.yBodyRot)};
            });
            t.check("pig " + pig + ": client head from the yaw (as the server sends it)", seen[0])
                .near(server, SENT);
            t.check("pig " + pig + ": head from the body, at most the neck", Math.abs(seen[1])).atMost(NECK + SENT);
        }
    }

    private static void players(TestContext t) {
        Spawned<Boat> boat = t.spawn(EntityType.BOAT, 4.5, 0.0, 4.5, 30.0F, null);
        t.ride(boat);
        t.ticks(3);
        config().legacyBoatRiding = false;
        double body = t.onClientThread(() -> {
            Boat entity = (Boat) client(t, boat.id());
            LocalPlayer player = t.player();
            t.require(player.getVehicle() == entity, "The player is not in the boat");
            player.yBodyRot = entity.getYRot() + 60.0F;
            entity.positionRider(player);
            return (double) Mth.wrapDegrees(player.yBodyRot - entity.getYRot());
        });
        t.check("Legacy Riding off, player: body locked to the boat's front (1.21.1, not this option)", body)
            .near(0.0, EXACT);
        t.dismount();
    }
}
