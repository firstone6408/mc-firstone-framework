package io.github.firstone.framework.devtest.features.legacyvehicles;

import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesConfig;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.particle.SplashParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tests of Legacy Vehicles (client) and the boat rules of Legacy Mechanics (server)
 *
 * <p>Every option is tested on and off, alone and with the options it works with. The expected numbers are the
 * 1.7.10 ones where 1.7.10 had the behavior (top speed 0.35, eyes 1.12 above the boat, crash speed 0.2), the 1.21.1
 * ones where the option keeps 1.21.1 (ice speed, the 1.21.1 handling), and otherwise the values measured when the
 * behavior was checked in game.</p>
 *
 * <p>Only the feature configs are used directly; checks that need an inner class go through
 * {@link LegacyVehiclesInternals}.</p>
 *
 * <p>Site layout: a water pool at x 0..12 (most tests), ice lanes from x 20, a wide pool for steering from x
 * 40.</p>
 */
public final class LegacyVehiclesTests implements FeatureTests {

    /** Top speed of a 1.7.10 boat */
    private static final double LEGACY_TOP_SPEED = 0.35;

    /** Top speed of a 1.21.1 boat on water, measured */
    private static final double VANILLA_TOP_SPEED = 0.399;

    /** 1.7.10 height of the rider's eyes above the bottom of the boat */
    private static final double LEGACY_EYE_HEIGHT = 1.12;

    /** Speeds of the first 40 ticks of driving forward from rest on water, measured (1.7.10 speed-up factor) */
    private static final String SPEED_UP = "0.000 0.003 0.007 0.011 0.016 0.021 0.026 0.031 0.037 0.043 0.049 0.055 "
        + "0.062 0.069 0.076 0.083 0.090 0.098 0.106 0.114 0.122 0.130 0.138 0.147 0.156 0.164 0.173 0.182 0.191 0.200 "
        + "0.210 0.219 0.228 0.238 0.247 0.257 0.266 0.276 0.286 0.295";

    /** 1.21.1 height of the rider's eyes above the bottom of the boat, measured */
    private static final double VANILLA_EYE_HEIGHT = 1.208;

    @Override
    public String id() {
        return "legacy_vehicles";
    }

    @Override
    public List<Object> configs() {
        return List.of(LegacyVehiclesFeature.getConfig(), LegacyMechanicsFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        return List.of(
            // physics formulas (exact, no client/server timing)
            TestCase.of("Physics: 1.7.10 push between a boat and who stands on it", this::pushFormula),
            TestCase.of("Physics: 1.7.10 speed-up on water, tick by tick", this::speedUpCurve),
            // handling
            TestCase.of("Handling: top speed 0.35 on water, eyes 1.12 above the boat", this::waterTopSpeed),
            TestCase.of("Handling: chest boats and rafts also top out at 0.35", this::otherBoatsTopSpeed),
            TestCase.of("Handling: backwards", this::backwards),
            TestCase.of("Handling: the boat goes where the player looks and turns toward its motion", this::steering),
            TestCase.of("Handling: paddles stay still", this::paddles),
            TestCase.of("Handling off: 1.21.1 speed, paddles, the look does not steer", this::vanillaHandling),
            // ice
            TestCase.of("Ice Boating: as fast as 1.21.1 on packed ice, ice and blue ice", this::iceSpeed),
            TestCase.of("Ice Boating off: the boat crawls on ice like 1.7.10 on land", this::iceBoatingOff),
            TestCase.of("Ice Boating: leaving ice into water slows down gradually to 0.35", this::iceIntoWater),
            TestCase.of("Ice Boating: leaving ice onto stone stops the boat in a few ticks", this::iceOntoStone),
            // coasting
            TestCase.of("Coasting: getting out at full speed, the boat keeps going like 1.7.10", this::coasting),
            TestCase.of("Coasting off: a boat without a driver stops like 1.21.1", this::coastingOff),
            TestCase.of("Coasting with Handling off: the 1.21.1 driver's speed carries on",
                this::coastingVanillaDriver),
            // breaking
            TestCase.of("Breaking: a wooden boat that hits a wall fast breaks into 3 planks and 2 sticks",
                this::crashWoodenBoat),
            TestCase.of("Breaking: a chest boat drops its chest and contents too", this::crashChestBoat),
            TestCase.of("Breaking: a bamboo raft drops bamboo planks", this::crashRaft),
            TestCase.of("Breaking: Break Chest Boats and Rafts off keeps them whole", this::chestBoatsAndRaftsSafe),
            TestCase.of("Breaking off: no boat breaks", this::breakingOff),
            TestCase.of("Breaking: works with Coasting off too", this::breakingWithoutCoasting),
            TestCase.of("Breaking: boats without a driver break above 0.2, not below", this::crashSpeedOnWater),
            TestCase.of("Breaking: on land the speed at the start of the tick counts (1.7.10)", this::crashSpeedOnLand),
            TestCase.of("Breaking: Safe on Ice keeps boats on ice whole; off they break", this::safeOnIce),
            TestCase.of("Breaking: no drops with doEntityDrops off", this::noDrops),
            TestCase.of("Breaking: snow layers under a boat are removed; not with Breaking off", this::snow),
            // pushes
            TestCase.of("Pushes: standing on a boat pushes it away and the player falls off", this::standing),
            TestCase.of("Pushes: a boat with a mob pushes the player's idle boat", this::pushedByMobBoat),
            TestCase.of("Pushes: the player's boat pushes a boat with a mob", this::pushingMobBoat),
            // riding
            TestCase.of("Riding: 1.7.10 seat for one rider in a wooden boat; off: 1.21.1 seat", this::seat),
            TestCase.of("Riding: free look; off: the head turns at most 105 degrees", this::freeLook),
            TestCase.of("Riding: two riders and chest boats keep the 1.21.1 seat", this::otherSeats),
            // splash
            TestCase.of("Splash: bobbing boats make no splash sound, a dropped boat does", this::bobbingSplash),
            TestCase.of("Splash: no bobbing splash with Handling and Coasting off either", this::bobbingSplashVanilla),
            TestCase.of("Splash: fast boats throw 1.7.10 splash particles; Boat Splash off: none",
                this::splashParticles),
            // model
            TestCase.of("Model: 1.7.10 model for wooden boats only; off: none", this::modelChoice),
            TestCase.of("Model: a pack with its own boat texture keeps the 1.21.1 model (Resource Pack Boats)",
                this::resourcePackBoats),
            TestCase.of("Model: screenshots of the models, paddles and riding views", this::modelScreenshots),
            TestCase.of("Settings: the Legacy Vehicles and Legacy Mechanics screens open", this::configScreens),
            // everything off
            TestCase.of("All options off: plain 1.21.1 boats", this::allOff)
        );
    }

    private static LegacyVehiclesConfig vehicles() {
        return LegacyVehiclesFeature.getConfig();
    }

    private static LegacyMechanicsConfig mechanics() {
        return LegacyMechanicsFeature.getConfig();
    }

    private static KeyMapping forward(TestContext t) {
        return t.mc().options.keyUp;
    }

    // ---- physics formulas -----------------------------------------------------------------------------------------

    /**
     * Puts a pig on top of an idle boat, {@code offset} blocks toward +x, pushes them once on the server thread and
     * returns the change of their x motion: {boat, pig}
     */
    private static double[] pushOnce(TestContext t, double offset) {
        pool(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 10.5);
        Spawned<Pig> pig = t.spawn(EntityType.PIG, 2.5, 0.0, 2.5, 0.0F, entity -> entity.setNoAi(true));
        return t.onServer(level -> {
            Boat boatEntity = (Boat) level.getEntity(boat.id());
            Pig pigEntity = (Pig) level.getEntity(pig.id());
            t.require(boatEntity != null && pigEntity != null, "The boat or the pig is gone");
            boatEntity.setDeltaMovement(Vec3.ZERO);
            pigEntity.setDeltaMovement(Vec3.ZERO);
            pigEntity.setPos(boatEntity.getX() + offset, boatEntity.getBoundingBox().maxY, boatEntity.getZ());
            LegacyVehiclesInternals.pushStanders(boatEntity);
            return new double[] {boatEntity.getDeltaMovement().x, pigEntity.getDeltaMovement().x};
        });
    }

    /** 1.7.10 {@code Entity.applyEntityCollision}: the push of each entity along x for a distance along x */
    private static double legacyPush(double distance) {
        double root = Math.sqrt(distance);
        return distance / root * Math.min(1.0, 1.0 / root) * 0.05;
    }

    private void pushFormula(TestContext t) {
        for (double offset : new double[] {0.3, 0.8}) {
            double[] push = pushOnce(t, offset);
            t.check("pig " + offset + " to the side: boat pushed along x", push[0]).near(-legacyPush(offset), 1e-4);
            t.check("pig " + offset + " to the side: pig pushed along x", push[1]).near(legacyPush(offset), 1e-4);
            t.removeEntities();
        }
        double[] none = pushOnce(t, 0.005);
        t.check("pig in the middle (closer than 0.01): boat pushed", none[0]).isEqualTo(0.0);
    }

    private void speedUpCurve(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.hold(forward(t));
        List<String> speeds = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            t.tick();
            speeds.add(String.format(Locale.ROOT, "%.3f", boat.speed()));
        }
        t.check("speeds of the first 40 ticks", String.join(" ", speeds)).isEqualTo(SPEED_UP);
    }

    // ---- handling -------------------------------------------------------------------------------------------------

    /** Pool for most tests: x 0..12, z 0..90 */
    private static void pool(TestContext t) {
        t.site().pool(0, 0, 12, 90);
    }

    /** Spawns an oak boat in the pool, puts the player in it facing +z */
    private static Spawned<Boat> rideOnWater(TestContext t, double x, double z) {
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, x, Boats.ON_WATER, z);
        t.ride(boat);
        t.face(0.0F);
        t.ticks(5);
        return boat;
    }

    private void waterTopSpeed(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.check("eyes above the boat", Boats.eyeAboveBoat(t, boat)).near(LEGACY_EYE_HEIGHT, 0.005);
        t.check("top speed", Boats.drive(t, boat, forward(t), 100)).near(LEGACY_TOP_SPEED, 0.001);
    }

    private void otherBoatsTopSpeed(TestContext t) {
        pool(t);
        Spawned<ChestBoat> chestBoat = Boats.spawn(t, EntityType.CHEST_BOAT, Boat.Type.OAK, 3.5, Boats.ON_WATER, 3.5,
            null);
        t.ride(chestBoat);
        t.face(0.0F);
        t.check("chest boat top speed", Boats.drive(t, chestBoat, forward(t), 100)).near(LEGACY_TOP_SPEED, 0.001);
        t.dismount();

        Spawned<Boat> raft = Boats.boat(t, Boat.Type.BAMBOO, 9.5, Boats.ON_WATER, 3.5);
        t.ride(raft);
        t.face(0.0F);
        t.check("bamboo raft top speed", Boats.drive(t, raft, forward(t), 100)).near(LEGACY_TOP_SPEED, 0.001);
    }

    private void backwards(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 80.5);
        double top = Boats.drive(t, boat, t.mc().options.keyDown, 100);
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        t.check("top speed backwards", top).near(LEGACY_TOP_SPEED, 0.001);
        t.check("moved backwards (-z)", client.getZ() - client.zo < 0.0).isTrue();
    }

    private void steering(TestContext t) {
        t.site().pool(40, 0, 100, 40);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 95.5, Boats.ON_WATER, 3.5);
        t.ride(boat);
        t.face(0.0F);
        t.hold(forward(t));
        t.ticks(40);
        t.face(90.0F);
        double maxTurn = 0.0;
        Vec3 before = Vec3.ZERO;
        for (int i = 0; i < 80; i++) {
            Boat client = boat.client();
            t.require(client != null, "The boat is gone");
            float yaw = client.getYRot();
            if (i == 60) {
                before = client.position();
            }
            t.tick();
            maxTurn = Math.max(maxTurn, Math.abs(Mth.wrapDegrees(client.getYRot() - yaw)));
        }
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        double motionYaw = Mth.atan2(-(client.getX() - client.xo), client.getZ() - client.zo) * Mth.RAD_TO_DEG;
        t.check("largest turn in one tick (degrees)", maxTurn).atMost(20.001);
        t.check("boat yaw minus the direction it moves", Mth.wrapDegrees(client.getYRot() - motionYaw))
            .near(0.0, 1.0);
        t.check("moved toward -x in the last 20 ticks", client.getX() - before.x).near(-6.972, 0.1);
        t.check("still drifting along z in the last 20 ticks", Math.abs(client.getZ() - before.z)).near(0.610, 0.05);
    }

    private void paddles(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.hold(forward(t));
        t.ticks(20);
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        t.check("left paddle moves", client.getPaddleState(0)).isFalse();
        t.check("right paddle moves", client.getPaddleState(1)).isFalse();
    }

    private void vanillaHandling(TestContext t) {
        vehicles().legacyBoatHandling = false;
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.hold(forward(t));
        t.ticks(20);
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        t.check("left paddle moves", client.getPaddleState(0)).isTrue();
        t.check("right paddle moves", client.getPaddleState(1)).isTrue();
        double x = client.getX();
        t.face(90.0F);
        t.check("top speed", Boats.drive(t, boat, forward(t), 80)).near(VANILLA_TOP_SPEED, 0.002);
        t.check("looking at -x does not steer: x moved", Math.abs(client.getX() - x)).atMost(0.1);
    }

    // ---- ice ------------------------------------------------------------------------------------------------------

    /** Drives 80 ticks on a lane of ice (x 20..24) and returns the top speed */
    private static double iceLane(TestContext t, String ice) {
        t.site().floor(20, 0, 24, 125, ice);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 22.5, Boats.ON_FLOOR, 1.5);
        t.ride(boat);
        t.face(0.0F);
        t.ticks(5);
        double top = Boats.drive(t, boat, forward(t), 80);
        t.dismount();
        t.removeEntities(Boat.class);
        return top;
    }

    private void iceSpeed(TestContext t) {
        for (String ice : List.of("packed_ice", "ice", "blue_ice")) {
            double legacy = iceLane(t, ice);
            vehicles().legacyBoatHandling = false;
            double vanilla = iceLane(t, ice);
            vehicles().legacyBoatHandling = true;
            t.note(ice + ": 1.21.1 handling top speed", vanilla);
            t.check(ice + ": top speed, same as 1.21.1", legacy).near(vanilla, 0.005);
        }
        // 1.21.1: friction first, then the 0.04 push: v(n) = 0.04 / (1 - f) * (1 - f^n)
        t.check("packed ice top speed after 80 ticks", iceLane(t, "packed_ice"))
            .near(0.04 / 0.02 * (1 - Math.pow(0.98, 80)), 0.02);
    }

    private void iceBoatingOff(TestContext t) {
        vehicles().iceBoating = false;
        t.check("top speed on packed ice", iceLane(t, "packed_ice")).near(0.016, 0.003);
    }

    /** Ice (z 0..50) then water or stone (z 51..120) on lane x 20..24; returns the speeds after the ice */
    private static List<Double> leaveIce(TestContext t, boolean intoWater) {
        t.site().floor(20, 0, 24, 50, "packed_ice");
        if (intoWater) {
            t.site().pool(20, 51, 24, 120);
        }
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 22.5, Boats.ON_FLOOR, 1.5);
        t.ride(boat);
        t.face(0.0F);
        t.hold(forward(t));
        List<Double> speeds = new ArrayList<>();
        double lastOnIce = 0.0;
        for (int i = 0; i < 160 && speeds.size() < 30; i++) {
            t.tick();
            Boat client = boat.client();
            t.require(client != null, "The boat is gone");
            double z = client.getZ() - TestSite.ORIGIN.getZ();
            if (z < 51.0) {
                lastOnIce = boat.speed();
            } else {
                speeds.add(boat.speed());
            }
        }
        t.require(speeds.size() == 30, "The boat did not leave the ice");
        t.note("speed on the last ice tick", lastOnIce);
        t.note("speeds after the ice", speeds.stream().map(s -> String.format("%.3f", s)).toList());
        speeds.add(0, lastOnIce);
        return speeds;
    }

    private void iceIntoWater(TestContext t) {
        List<Double> speeds = leaveIce(t, true);
        t.check("speed on the last ice tick", speeds.get(0)).atLeast(1.0);
        double lowestKept = 1.0;
        int ticksToLimit = -1;
        for (int i = 1; i < speeds.size(); i++) {
            if (speeds.get(i - 1) > 0.36) {
                lowestKept = Math.min(lowestKept, speeds.get(i) / speeds.get(i - 1));
            }
            if (ticksToLimit < 0 && speeds.get(i) <= LEGACY_TOP_SPEED + 0.0005) {
                ticksToLimit = i;
            }
        }
        // 0.9 like 1.21.1 water, then the 1.7.10 drag 0.99
        t.check("least share of speed kept per tick while fast", lowestKept).near(0.891, 0.003);
        t.check("ticks to slow down to 0.35", ticksToLimit).near(15, 1);
        t.check("speed afterwards", speeds.get(speeds.size() - 1)).near(LEGACY_TOP_SPEED, 0.001);
    }

    private void iceOntoStone(TestContext t) {
        List<Double> speeds = leaveIce(t, false);
        t.check("speed 8 ticks after the ice", speeds.get(8)).near(0.027, 0.005);
        t.check("speed 20 ticks after the ice (1.7.10 land crawl)", speeds.get(20)).near(0.015, 0.003);
    }

    // ---- coasting -------------------------------------------------------------------------------------------------

    /** Drives at full speed, gets out; returns the client boat's speeds of the 20 ticks after getting out */
    private static List<Double> getOutAtSpeed(TestContext t, Spawned<Boat> boat) {
        Boats.drive(t, boat, forward(t), 60);
        t.dismount();
        List<Double> speeds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            speeds.add(boat.speed());
            t.tick();
        }
        t.note("speeds after getting out", speeds.stream().map(s -> String.format("%.3f", s)).toList());
        return speeds;
    }

    private void coasting(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        List<Double> speeds = getOutAtSpeed(t, boat);
        Double serverZ = boat.server(entity -> entity.getZ());
        t.ticks(20);
        Double serverZLater = boat.server(entity -> entity.getZ());
        t.require(serverZ != null && serverZLater != null, "The boat is gone");
        t.check("speed on getting out (no pause)", speeds.get(0)).atLeast(0.32);
        t.check("speed 19 ticks later (1.7.10 drag 0.99)", speeds.get(19)).near(0.298, 0.01);
        t.check("server boat moved in the next 20 ticks", serverZLater - serverZ).near(5.2, 0.6);
    }

    private void coastingOff(TestContext t) {
        mechanics().legacyBoatCoasting = false;
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        List<Double> speeds = getOutAtSpeed(t, boat);
        t.check("speed 19 ticks after getting out", speeds.get(19)).atMost(0.06);
    }

    private void coastingVanillaDriver(TestContext t) {
        vehicles().legacyBoatHandling = false;
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        getOutAtSpeed(t, boat);
        Double serverZ = boat.server(entity -> entity.getZ());
        t.ticks(20);
        Double serverZLater = boat.server(entity -> entity.getZ());
        t.require(serverZ != null && serverZLater != null, "The boat is gone");
        t.check("server boat moved in the next 20 ticks", serverZLater - serverZ).near(5.2, 0.6);
    }

    // ---- breaking -------------------------------------------------------------------------------------------------

    /** Pool with a wall across it at z 30; the boat starts at z 15 */
    private static void crashSite(TestContext t) {
        pool(t);
        t.site().fill(0, -1, 30, 12, 1, 30, "stone");
    }

    /** Drives a boat into the wall (getting in first if needed); returns whether it broke */
    private static boolean driveIntoWall(TestContext t, Spawned<? extends Boat> boat) {
        if (t.player().getVehicle() == null) {
            t.ride(boat);
        }
        t.face(0.0F);
        t.hold(forward(t));
        for (int i = 0; i < 100 && boat.isAlive(); i++) {
            t.tick();
        }
        t.release(forward(t));
        t.ticks(30);
        return !boat.isAlive();
    }

    private void crashWoodenBoat(TestContext t) {
        crashSite(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 15.5);
        t.check("boat broke", driveIntoWall(t, boat)).isTrue();
        t.check("drops", Boats.drops(t)).isEqualTo("{oak_planks=3, stick=2}");
        t.check("player still riding", t.player().getVehicle() != null).isFalse();

        crashSite(t);
        Spawned<Boat> spruce = Boats.boat(t, Boat.Type.SPRUCE, 6.5, Boats.ON_WATER, 15.5);
        Boats.clearDrops(t);
        t.check("spruce boat broke", driveIntoWall(t, spruce)).isTrue();
        t.check("spruce drops", Boats.drops(t)).isEqualTo("{spruce_planks=3, stick=2}");
    }

    private void crashChestBoat(TestContext t) {
        crashSite(t);
        Spawned<ChestBoat> boat = Boats.spawn(t, EntityType.CHEST_BOAT, Boat.Type.OAK, 6.5, Boats.ON_WATER, 15.5,
            Boats.withItem(new ItemStack(Items.DIAMOND, 5)));
        t.check("chest boat broke", driveIntoWall(t, boat)).isTrue();
        t.check("drops", Boats.drops(t)).isEqualTo("{chest=1, diamond=5, oak_planks=3, stick=2}");
    }

    private void crashRaft(TestContext t) {
        crashSite(t);
        Spawned<Boat> raft = Boats.boat(t, Boat.Type.BAMBOO, 6.5, Boats.ON_WATER, 15.5);
        t.check("raft broke", driveIntoWall(t, raft)).isTrue();
        t.check("drops", Boats.drops(t)).isEqualTo("{bamboo_planks=3, stick=2}");
    }

    private void chestBoatsAndRaftsSafe(TestContext t) {
        mechanics().breakChestBoatsAndRafts = false;
        crashSite(t);
        Spawned<ChestBoat> chestBoat = Boats.spawn(t, EntityType.CHEST_BOAT, Boat.Type.OAK, 3.5, Boats.ON_WATER, 15.5,
            null);
        t.check("chest boat broke", driveIntoWall(t, chestBoat)).isFalse();
        t.dismount();
        Spawned<Boat> raft = Boats.boat(t, Boat.Type.BAMBOO, 9.5, Boats.ON_WATER, 15.5);
        t.check("raft broke", driveIntoWall(t, raft)).isFalse();
        t.dismount();
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 15.5);
        t.check("wooden boat broke", driveIntoWall(t, boat)).isTrue();
    }

    private void breakingOff(TestContext t) {
        mechanics().legacyBoatBreaking = false;
        crashSite(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 15.5);
        t.check("wooden boat broke", driveIntoWall(t, boat)).isFalse();
        t.dismount();
        Spawned<ChestBoat> chestBoat = Boats.spawn(t, EntityType.CHEST_BOAT, Boat.Type.OAK, 3.5, Boats.ON_WATER, 15.5,
            null);
        t.check("chest boat broke", driveIntoWall(t, chestBoat)).isFalse();
    }

    private void breakingWithoutCoasting(TestContext t) {
        mechanics().legacyBoatCoasting = false;
        crashSite(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 15.5);
        t.check("wooden boat broke", driveIntoWall(t, boat)).isTrue();
    }

    /** Spawns a boat without a driver moving toward +z at {@code speed}, close to a wall; returns whether it broke */
    private static boolean slideIntoWall(TestContext t, double x, double y, double z, double speed) {
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, x, y, z);
        boat.server(entity -> {
            entity.setDeltaMovement(0.0, 0.0, speed);
            return null;
        });
        t.ticks(20);
        return !boat.isAlive();
    }

    private void crashSpeedOnWater(TestContext t) {
        crashSite(t);
        t.check("0.3 into a wall breaks", slideIntoWall(t, 3.5, Boats.ON_WATER, 28.5, 0.3)).isTrue();
        t.check("0.15 into a wall does not break", slideIntoWall(t, 9.5, Boats.ON_WATER, 28.5, 0.15)).isFalse();
    }

    private void crashSpeedOnLand(TestContext t) {
        t.site().wall(0, 10, 12, 10, 2);
        t.check("0.4 on stone into a wall breaks", slideIntoWall(t, 3.5, Boats.ON_FLOOR, 9.2, 0.4)).isTrue();
        t.check("0.15 on stone into a wall does not break", slideIntoWall(t, 9.5, Boats.ON_FLOOR, 9.2, 0.15))
            .isFalse();
    }

    /** Ice lane x 20..24, z 0..40 with a wall at z 41 */
    private static boolean driveIntoIceWall(TestContext t) {
        t.site().floor(20, 0, 24, 40, "packed_ice");
        t.site().wall(20, 41, 24, 41, 2);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 22.5, Boats.ON_FLOOR, 1.5);
        return driveIntoWall(t, boat);
    }

    private void safeOnIce(TestContext t) {
        t.check("boat on ice broke (Safe on Ice on)", driveIntoIceWall(t)).isFalse();
        t.dismount();
        t.removeEntities(Boat.class);
        mechanics().boatsSafeOnIce = false;
        t.check("boat on ice broke (Safe on Ice off)", driveIntoIceWall(t)).isTrue();
    }

    private void noDrops(TestContext t) {
        t.command("gamerule doEntityDrops false");
        crashSite(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 15.5);
        t.check("boat broke", driveIntoWall(t, boat)).isTrue();
        t.check("drops", Boats.drops(t)).isEqualTo("{}");
    }

    private static boolean snowAt(TestContext t, int x, int z) {
        return t.onServer(level -> level.getBlockState(TestSite.ORIGIN.offset(x, 0, z)).is(Blocks.SNOW));
    }

    private void snow(TestContext t) {
        t.site().set(3, 0, 3, "snow[layers=1]");
        t.site().set(9, 0, 3, "snow[layers=1]");
        Boats.boat(t, Boat.Type.OAK, 3.5, Boats.ON_FLOOR, 3.5);
        t.ticks(10);
        t.check("snow under the boat removed", !snowAt(t, 3, 3)).isTrue();

        mechanics().legacyBoatBreaking = false;
        Boats.boat(t, Boat.Type.OAK, 9.5, Boats.ON_FLOOR, 3.5);
        t.ticks(10);
        t.check("snow under the boat removed (Breaking off)", !snowAt(t, 9, 3)).isFalse();
    }

    // ---- pushes ---------------------------------------------------------------------------------------------------

    /** A survival player lands on an idle boat; returns how far the boat moved along x and the player's height */
    private static double[] standOnBoat(TestContext t) {
        pool(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 10.5);
        Double startX = boat.server(entity -> entity.getX());
        t.gameMode(GameType.SURVIVAL);
        t.teleport(6.8, 1.5, 10.5, 0.0F, 0.0F);
        t.ticks(40);
        Double endX = boat.server(entity -> entity.getX());
        t.require(startX != null && endX != null, "The boat is gone");
        return new double[] {endX - startX, t.player().getY() - TestSite.ORIGIN.getY()};
    }

    private void standing(TestContext t) {
        double[] result = standOnBoat(t);
        // client and server ticks are not in step: the server-side results vary between runs
        t.check("boat pushed away from the player (-x)", result[0]).between(-7.0, -1.5);
        t.check("player fell into the water (height)", result[1]).atMost(0.0);

        // the server's boat stays (1.21.1); the client still pushes its own player off the boat, as on any server
        // that does not move boats from under their standers
        mechanics().legacyBoatCoasting = false;
        t.removeEntities(Boat.class);
        t.gameMode(GameType.CREATIVE);
        result = standOnBoat(t);
        t.check("Coasting off: boat moved", Math.abs(result[0])).near(0.345, 0.2);
        t.check("Coasting off: player pushed off the boat into the water (height)", result[1]).atMost(0.0);
    }

    /** A pig boat moving at -0.3 toward the player's idle boat; returns how far the player's boat moved along z */
    private static double rammedByMobBoat(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 20.5);
        Spawned<Boat> pigBoat = Boats.boat(t, Boat.Type.BIRCH, 6.5, Boats.ON_WATER, 26.5);
        t.onServer(level -> {
            Boat entity = (Boat) level.getEntity(pigBoat.id());
            Pig pig = EntityType.PIG.create(level);
            t.require(entity != null && pig != null, "Cannot make the pig boat");
            pig.moveTo(entity.position());
            level.addFreshEntity(pig);
            pig.startRiding(entity, true);
            entity.setDeltaMovement(0.0, 0.0, -0.3);
            return null;
        });
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        double startZ = client.getZ();
        t.ticks(50);
        return client.getZ() - startZ;
    }

    private void pushedByMobBoat(TestContext t) {
        t.check("player's boat moved (negative: pushed back)", rammedByMobBoat(t)).between(-11.0, -4.0);
        t.dismount();
        t.removeEntities();
        mechanics().legacyBoatCoasting = false;
        t.check("Coasting off: player's boat moved", Math.abs(rammedByMobBoat(t))).atMost(0.5);
    }

    /** The player drives into an idle pig boat; returns how far the pig boat moved along z */
    private static double ramMobBoat(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        Spawned<Boat> pigBoat = Boats.boat(t, Boat.Type.BIRCH, 6.5, Boats.ON_WATER, 12.5);
        t.onServer(level -> {
            Pig pig = EntityType.PIG.create(level);
            Boat entity = (Boat) level.getEntity(pigBoat.id());
            t.require(entity != null && pig != null, "Cannot make the pig boat");
            pig.moveTo(entity.position());
            level.addFreshEntity(pig);
            pig.startRiding(entity, true);
            return null;
        });
        t.ticks(5);
        Double startZ = pigBoat.server(entity -> entity.getZ());
        Boats.drive(t, boat, forward(t), 40);
        t.ticks(20);
        Double endZ = pigBoat.server(entity -> entity.getZ());
        t.require(startZ != null && endZ != null, "The pig boat is gone");
        return endZ - startZ;
    }

    private void pushingMobBoat(TestContext t) {
        t.check("pig boat moved (positive: pushed away)", ramMobBoat(t)).between(1.0, 3.5);
        t.dismount();
        t.removeEntities();
        mechanics().legacyBoatCoasting = false;
        t.check("Coasting off: pig boat moved", Math.abs(ramMobBoat(t))).atMost(0.3);
    }

    // ---- riding ---------------------------------------------------------------------------------------------------

    private void seat(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.check("eyes above the boat", Boats.eyeAboveBoat(t, boat)).near(LEGACY_EYE_HEIGHT, 0.005);
        vehicles().legacyBoatRiding = false;
        t.ticks(3);
        double vanilla = Boats.eyeAboveBoat(t, boat);
        t.check("Riding off: eyes above the boat (1.21.1)", vanilla).near(VANILLA_EYE_HEIGHT, 0.005);
    }

    /** Turns the player's head 170 degrees from the boat; returns the angle between them after 5 ticks */
    private static double headTurn(TestContext t, Spawned<Boat> boat) {
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        t.player().setYRot(client.getYRot() + 170.0F);
        t.ticks(5);
        return Math.abs(Mth.wrapDegrees(t.player().getYRot() - client.getYRot()));
    }

    private void freeLook(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.check("head angle from the boat", headTurn(t, boat)).near(170.0, 0.5);
        vehicles().legacyBoatRiding = false;
        t.face(0.0F);
        t.ticks(3);
        t.check("Riding off: head angle from the boat", headTurn(t, boat)).atMost(105.01);
    }

    private void otherSeats(TestContext t) {
        pool(t);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 3.5, Boats.ON_WATER, 3.5);
        t.onServer(level -> {
            Pig pig = EntityType.PIG.create(level);
            Boat entity = (Boat) level.getEntity(boat.id());
            t.require(entity != null && pig != null, "Cannot make the pig boat");
            pig.moveTo(entity.position());
            level.addFreshEntity(pig);
            pig.startRiding(entity, true);
            return null;
        });
        t.ticks(5);
        t.ride(boat);
        t.ticks(5);
        t.check("two riders: eyes above the boat (1.21.1)", Boats.eyeAboveBoat(t, boat))
            .near(VANILLA_EYE_HEIGHT, 0.005);
        t.dismount();

        Spawned<ChestBoat> chestBoat = Boats.spawn(t, EntityType.CHEST_BOAT, Boat.Type.OAK, 9.5, Boats.ON_WATER, 3.5,
            null);
        t.ride(chestBoat);
        t.ticks(5);
        t.check("chest boat: eyes above the boat (1.21.1)", Boats.eyeAboveBoat(t, chestBoat))
            .near(VANILLA_EYE_HEIGHT, 0.005);
    }

    // ---- splash ---------------------------------------------------------------------------------------------------

    /** Six idle boats for 100 ticks, then one dropped from 5 blocks; returns the splash sounds of each part */
    private static int[] splashSounds(TestContext t) {
        pool(t);
        t.teleport(6.5, 3.0, 2.5, 0.0F, 30.0F);
        for (int i = 0; i < 6; i++) {
            Boats.boat(t, Boat.Type.OAK, 2.5 + (i % 3) * 4, Boats.ON_WATER, 8.5 + (i / 3) * 4);
        }
        t.ticks(20);
        var sounds = t.recordSounds();
        t.ticks(100);
        int idle = sounds.count("splash");
        sounds.clear();
        Boats.boat(t, Boat.Type.OAK, 6.5, 5.0, 4.5);
        t.ticks(40);
        return new int[] {idle, sounds.count("splash")};
    }

    private void bobbingSplash(TestContext t) {
        int[] sounds = splashSounds(t);
        t.check("splash sounds of 6 idle boats in 5 s", sounds[0]).isEqualTo(0);
        t.check("splash sounds of a boat dropped into water", sounds[1]).atLeast(1);
    }

    private void bobbingSplashVanilla(TestContext t) {
        vehicles().legacyBoatHandling = false;
        mechanics().legacyBoatCoasting = false;
        int[] sounds = splashSounds(t);
        t.check("splash sounds of 6 idle boats in 5 s", sounds[0]).isEqualTo(0);
        t.check("splash sounds of a boat dropped into water", sounds[1]).atLeast(1);
    }

    /** Drives at full speed and returns the most splash particles seen at once */
    private static int particlesWhileDriving(TestContext t) {
        pool(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.hold(forward(t));
        int most = 0;
        for (int i = 0; i < 90; i++) {
            t.tick();
            // splash particles live up to 40 ticks: skip the player's own splash when getting in
            if (i >= 45) {
                most = Math.max(most, t.particleCount(SplashParticle.class));
            }
        }
        t.release(forward(t));
        t.note("top speed", boat.speed());
        return most;
    }

    private void splashParticles(TestContext t) {
        t.check("splash particles while driving fast", particlesWhileDriving(t)).atLeast(100);
        // out of the water, so the player's own splash is gone before the next count
        t.dismount();
        t.removeEntities(Boat.class);
        t.teleport(-6.5, 2.0, -6.5, 0.0F, 0.0F);
        t.setFlying(true);
        vehicles().boatSplash = false;
        t.ticks(60);
        t.check("Boat Splash off: splash particles while driving fast", particlesWhileDriving(t)).atMost(5);
    }

    // ---- model ----------------------------------------------------------------------------------------------------

    /** Spawns an oak, a spruce, a chest boat and a raft in a row across the pool (x 1.5 .. 10.5, z 12.5) */
    private static List<Spawned<? extends Boat>> boatRow(TestContext t) {
        pool(t);
        List<Spawned<? extends Boat>> boats = new ArrayList<>();
        boats.add(Boats.boat(t, Boat.Type.OAK, 1.5, Boats.ON_WATER, 12.5));
        boats.add(Boats.boat(t, Boat.Type.SPRUCE, 4.5, Boats.ON_WATER, 12.5));
        boats.add(Boats.spawn(t, EntityType.CHEST_BOAT, Boat.Type.OAK, 7.5, Boats.ON_WATER, 12.5, null));
        boats.add(Boats.boat(t, Boat.Type.BAMBOO, 10.5, Boats.ON_WATER, 12.5));
        return boats;
    }

    private static boolean draws(Spawned<? extends Boat> boat) {
        Boat client = boat.client();
        return client != null && LegacyVehiclesInternals.drawsLegacyModel(client);
    }

    private void modelChoice(TestContext t) {
        List<Spawned<? extends Boat>> boats = boatRow(t);
        t.check("oak boat: 1.7.10 model", draws(boats.get(0))).isTrue();
        t.check("spruce boat: 1.7.10 model", draws(boats.get(1))).isTrue();
        t.check("chest boat: 1.7.10 model", draws(boats.get(2))).isFalse();
        t.check("bamboo raft: 1.7.10 model", draws(boats.get(3))).isFalse();
        vehicles().legacyBoatModel = false;
        t.check("Model off: oak boat: 1.7.10 model", draws(boats.get(0))).isFalse();
        t.check("Model off: spruce boat: 1.7.10 model", draws(boats.get(1))).isFalse();
    }

    private void resourcePackBoats(TestContext t) throws Exception {
        List<Spawned<? extends Boat>> boats = boatRow(t);
        var pack = t.resourcePack("oak_boat_texture")
            .image("assets/minecraft/textures/entity/boat/oak.png", 128, 64, 0xFF2020D0);
        pack.enable();
        t.check("pack on: oak boat: 1.7.10 model", draws(boats.get(0))).isFalse();
        t.check("pack on: spruce boat: 1.7.10 model", draws(boats.get(1))).isTrue();
        t.teleport(6.0, 3.0, 6.5, 0.0F, 35.0F);
        t.screenshot("resource_pack_oak_boat", 2.0);
        vehicles().resourcePackBoats = false;
        t.check("pack on, Resource Pack Boats off: oak boat: 1.7.10 model", draws(boats.get(0))).isTrue();
        vehicles().resourcePackBoats = true;
        pack.disable();
        t.check("pack off again: oak boat: 1.7.10 model", draws(boats.get(0))).isTrue();
    }

    private void modelScreenshots(TestContext t) {
        List<Spawned<? extends Boat>> boats = boatRow(t);
        t.teleport(6.0, 3.0, 6.5, 0.0F, 35.0F);
        t.screenshot("models", 2.0);
        vehicles().hidePaddles = false;
        t.screenshot("models_paddles_shown", 2.0);
        vehicles().hidePaddles = true;
        vehicles().legacyBoatModel = false;
        t.screenshot("models_legacy_model_off", 2.0);
        vehicles().legacyBoatModel = true;

        // riding views bob with the boat: a little more may differ
        t.ride(boats.get(0));
        t.face(0.0F);
        t.ticks(40);
        t.screenshot("riding_first_person", 5.0);
        t.mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        t.screenshot("riding_third_person", 5.0);
    }

    private void configScreens(TestContext t) {
        t.check("Legacy Vehicles screen open", t.openSettings("legacy_vehicles")).isTrue();
        t.saveScreenshot("settings_legacy_vehicles");
        t.closeScreen();
        t.check("Legacy Mechanics screen open", t.openSettings("legacy_mechanics")).isTrue();
        t.saveScreenshot("settings_legacy_mechanics");
        t.closeScreen();
        t.check("screens closed", t.mc().screen == null).isTrue();
    }

    // ---- everything off -------------------------------------------------------------------------------------------

    private void allOff(TestContext t) {
        LegacyVehiclesConfig vehicles = vehicles();
        vehicles.legacyBoatHandling = false;
        vehicles.iceBoating = false;
        vehicles.legacyBoatRiding = false;
        vehicles.legacyBoatModel = false;
        vehicles.resourcePackBoats = false;
        vehicles.hidePaddles = false;
        vehicles.boatSplash = false;
        LegacyMechanicsConfig mechanics = mechanics();
        mechanics.legacyBoatBreaking = false;
        mechanics.breakChestBoatsAndRafts = false;
        mechanics.boatsSafeOnIce = false;
        mechanics.legacyBoatCoasting = false;

        crashSite(t);
        Spawned<Boat> boat = rideOnWater(t, 6.5, 3.5);
        t.check("eyes above the boat (1.21.1)", Boats.eyeAboveBoat(t, boat)).near(VANILLA_EYE_HEIGHT, 0.005);
        t.check("1.7.10 model", draws(boat)).isFalse();
        t.check("top speed (1.21.1)", Boats.drive(t, boat, forward(t), 60)).near(VANILLA_TOP_SPEED, 0.002);
        t.check("boat broke on the wall", driveIntoWall(t, boat)).isFalse();
    }
}
