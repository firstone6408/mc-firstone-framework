package io.github.firstone.framework.devtest.features.legacyvehicles.boat;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.features.legacyvehicles.Boats;
import io.github.firstone.framework.devtest.mixin.HandRendererAccessor;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesConfig;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Tests of the 1.7.10 hands while paddling, part of the Legacy Vehicles tests
 *
 * <p>The player paddles an oak boat (forward key held) with snowballs in hand, looking at the sky so clicks hit
 * nothing. 1.21.1 marks the hands busy while paddling: the hand goes down, attacking does not swing and using an item
 * throws nothing. With "Legacy Hands" the hand stays up and both clicks work.</p>
 */
public final class BoatHandsTests {

    /** Ticks the forward key is held before checking, enough to lower a hand fully (0.4 per tick) */
    private static final int PADDLE_TICKS = 10;

    private BoatHandsTests() {}

    /**
     * Returns the tests of the hands while paddling
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Hands: the hand stays up while paddling; off: both hands go down (1.21.1)",
                BoatHandsTests::handHeight),
            TestCase.of("Hands: attacking and using items work while paddling; off: both are blocked (1.21.1)",
                BoatHandsTests::clicks)
        );
    }

    private static LegacyVehiclesConfig config() {
        return LegacyVehiclesFeature.getConfig();
    }

    private static KeyMapping forward(TestContext t) {
        return t.mc().options.keyUp;
    }

    /** Puts the player in an oak boat on a pool, snowballs in hand, looking straight up */
    private static void rideWithSnowballs(TestContext t) {
        t.site().pool(0, 0, 12, 90);
        Spawned<Boat> boat = Boats.boat(t, Boat.Type.OAK, 6.5, Boats.ON_WATER, 3.5);
        t.holdItem(new ItemStack(Items.SNOWBALL, 16));
        t.ride(boat);
        t.face(0.0F);
        t.onClientThread(() -> {
            t.player().setXRot(-90.0F);
            return null;
        });
        t.ticks(10);
    }

    private static float handHeight(TestContext t, boolean mainHand) {
        HandRendererAccessor hand = (HandRendererAccessor) t.mc().gameRenderer.itemInHandRenderer;
        return t.onClientThread(() -> mainHand ? hand.devtest$getMainHandHeight() : hand.devtest$getOffHandHeight());
    }

    private static boolean handsBusy(TestContext t) {
        return t.onClientThread(() -> t.player().isHandsBusy());
    }

    /** Holds the forward key for a while; returns the main hand's lowest height meanwhile */
    private static double paddle(TestContext t) {
        t.hold(forward(t));
        double lowest = 1.0;
        for (int i = 0; i < PADDLE_TICKS; i++) {
            t.tick();
            lowest = Math.min(lowest, handHeight(t, true));
        }
        return lowest;
    }

    private static void handHeight(TestContext t) {
        rideWithSnowballs(t);
        double before = handHeight(t, true);
        double lowest = paddle(t);
        t.check("on: hands busy while paddling", handsBusy(t)).isFalse();
        t.note("main hand height before paddling", before);
        t.check("on: main hand lowest while paddling (as high as before)", lowest).atLeast(before - 0.01);
        t.release(forward(t));
        t.ticks(10);

        config().legacyBoatHands = false;
        paddle(t);
        t.check("off: hands busy while paddling (1.21.1)", handsBusy(t)).isTrue();
        t.check("off: main hand down while paddling (1.21.1)", (double) handHeight(t, true)).atMost(0.01);
        t.check("off: off hand down while paddling (1.21.1)", (double) handHeight(t, false)).atMost(0.01);
        t.release(forward(t));
        t.ticks(10);
        t.check("off: main hand back up after paddling", (double) handHeight(t, true)).atLeast(before - 0.01);
    }

    /** Clicks attack while paddling; returns whether the arm swung */
    private static boolean attackSwings(TestContext t) {
        t.onClientThread(() -> {
            t.player().swinging = false;
            return null;
        });
        t.click(t.mc().options.keyAttack);
        boolean swung = false;
        for (int i = 0; i < 3; i++) {
            swung |= t.onClientThread(() -> t.player().swinging);
            t.tick();
        }
        return swung;
    }

    /** Clicks use while paddling; returns how many snowballs were thrown */
    private static int snowballsThrown(TestContext t) {
        t.removeEntities(Snowball.class);
        t.click(t.mc().options.keyUse);
        t.ticks(2);
        return t.onServer(level -> level.getEntities(EntityType.SNOWBALL, entity -> true).size());
    }

    private static void clicks(TestContext t) {
        rideWithSnowballs(t);
        paddle(t);
        t.check("on: attack while paddling swings the arm", attackSwings(t)).isTrue();
        t.ticks(10);
        t.check("on: use while paddling throws a snowball", snowballsThrown(t)).isEqualTo(1);
        t.release(forward(t));
        t.ticks(10);

        config().legacyBoatHands = false;
        paddle(t);
        t.require(handsBusy(t), "The hands are not busy while paddling");
        t.check("off: attack while paddling (1.21.1: no swing)", attackSwings(t)).isFalse();
        t.ticks(10);
        t.check("off: use while paddling (1.21.1: nothing thrown)", snowballsThrown(t)).isEqualTo(0);
        t.release(forward(t));
    }
}
