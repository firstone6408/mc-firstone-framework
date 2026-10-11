package io.github.firstone.framework.devtest.features.legacymechanics.food;

import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * Tests of the 1.7.10 natural regeneration, part of the Legacy Mechanics tests
 *
 * <p>Everything is read on the server: between two heals in a row the test measures the ticks (server game time),
 * the health gained and the exhaustion spent (exhaustion added, plus 4 for each point of saturation or food it was
 * turned into). 1.7.10 heals 1 every 80 ticks for 3 exhaustion; 1.21.1 also heals {@code saturation / 6} every 10
 * ticks while food is full, for 6 exhaustion per point healed, and its slow rule costs 6.</p>
 */
public final class RegenerationTests {

    /** Ticks between heals of the slow rule (both versions) */
    private static final int SLOW_TICKS = 80;

    /** Ticks between heals of the 1.21.1 fast rule */
    private static final int FAST_TICKS = 10;

    /** The heals are seen a client tick after they happen at most, so tick counts may be off by this much */
    private static final int TICK_TOLERANCE = 2;

    private RegenerationTests() {}

    /**
     * Returns the regeneration tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.slow("Regeneration: full food with saturation heals 1 every 80 ticks for 3 exhaustion; off: fast "
                + "saturation healing (1.21.1)", RegenerationTests::fullFood),
            TestCase.slow("Regeneration: food under 20 heals 1 every 80 ticks for 3 exhaustion; off: for 6 (1.21.1)",
                RegenerationTests::foodUnder20),
            TestCase.of("Regeneration: food 17 does not heal, on and off", RegenerationTests::food17),
            TestCase.of("Regeneration: the naturalRegeneration game rule still turns healing off",
                RegenerationTests::gameRuleOff)
        );
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    /** What the server has for the player at one moment */
    private record Food(long time, float health, int food, float saturation, float exhaustion) {

        /** Exhaustion spent since {@code before}: added now, plus 4 for each point of saturation or food used up */
        float spentSince(Food before) {
            return this.exhaustion - before.exhaustion + 4.0F * (before.saturation - this.saturation)
                + 4.0F * (before.food - this.food);
        }
    }

    private static ServerPlayer serverPlayer(TestContext t, ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
        t.require(player != null, "The server has no player");
        return player;
    }

    private static Food read(TestContext t) {
        return t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            FoodData food = player.getFoodData();
            return new Food(level.getGameTime(), player.getHealth(), food.getFoodLevel(), food.getSaturationLevel(),
                food.getExhaustionLevel());
        });
    }

    /** A survival player in easy difficulty (peaceful heals by itself), with the given health and food */
    private static void prepare(TestContext t, float health, int foodLevel, float saturation) {
        t.difficulty(Difficulty.EASY);
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.setFlying(false);
        t.gameMode(GameType.SURVIVAL);
        t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            player.setHealth(health);
            FoodData food = player.getFoodData();
            food.setFoodLevel(foodLevel);
            food.setSaturation(saturation);
            food.setExhaustion(0.0F);
            return null;
        });
    }

    /** Waits until the player's health rises above {@code after}'s; returns that moment, or null after maxTicks */
    private static Food nextHeal(TestContext t, Food after, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            t.tick();
            Food now = read(t);
            if (now.health > after.health) {
                return now;
            }
        }
        return null;
    }

    /** Waits for two heals in a row; returns {ticks between them, health gained, exhaustion spent} */
    private static double[] twoHeals(TestContext t, int ticks) {
        Food first = nextHeal(t, read(t), ticks + TICK_TOLERANCE * 2);
        t.require(first != null, "No heal came");
        Food second = nextHeal(t, first, ticks + TICK_TOLERANCE * 2);
        t.require(second != null, "No second heal came");
        return new double[] {second.time - first.time, second.health - first.health, second.spentSince(first)};
    }

    private static void fullFood(TestContext t) {
        prepare(t, 10.0F, 20, 5.0F);
        double[] on = twoHeals(t, SLOW_TICKS);
        t.check("on: ticks between heals (1.7.10: 80)", on[0]).near(SLOW_TICKS, TICK_TOLERANCE);
        t.check("on: health per heal (1.7.10: 1)", on[1]).near(1.0, 1.0E-5);
        t.check("on: exhaustion per heal (1.7.10: 3)", on[2]).near(3.0, 1.0E-4);

        config().legacyRegeneration = false;
        prepare(t, 10.0F, 20, 5.0F);
        double[] off = twoHeals(t, FAST_TICKS);
        t.check("off: ticks between heals (1.21.1: 10)", off[0]).near(FAST_TICKS, TICK_TOLERANCE);
        t.check("off: health per heal (1.21.1: saturation / 6)", off[1]).between(0.5, 1.0);
        t.check("off: exhaustion per point healed (1.21.1: 6)", off[2] / off[1]).near(6.0, 1.0E-3);
    }

    /** Food 19 without saturation: the slow rule only; 19 so that food used up by the first heal (18) still heals */
    private static void foodUnder20(TestContext t) {
        prepare(t, 10.0F, 19, 0.0F);
        double[] on = twoHeals(t, SLOW_TICKS);
        t.check("on: ticks between heals (1.7.10: 80)", on[0]).near(SLOW_TICKS, TICK_TOLERANCE);
        t.check("on: health per heal (1.7.10: 1)", on[1]).near(1.0, 1.0E-5);
        t.check("on: exhaustion per heal (1.7.10: 3)", on[2]).near(3.0, 1.0E-4);

        config().legacyRegeneration = false;
        prepare(t, 10.0F, 19, 0.0F);
        double[] off = twoHeals(t, SLOW_TICKS);
        t.check("off: ticks between heals (1.21.1: 80)", off[0]).near(SLOW_TICKS, TICK_TOLERANCE);
        t.check("off: health per heal (1.21.1: 1)", off[1]).near(1.0, 1.0E-5);
        t.check("off: exhaustion per heal (1.21.1: 6)", off[2]).near(6.0, 1.0E-4);
    }

    /** Health gained over a little more than one slow heal's time */
    private static double gainedOverSlowTime(TestContext t) {
        Food before = read(t);
        t.ticks(SLOW_TICKS + 20);
        return read(t).health - before.health;
    }

    private static void food17(TestContext t) {
        prepare(t, 10.0F, 17, 5.0F);
        t.check("on: health gained in 100 ticks", gainedOverSlowTime(t)).near(0.0, 1.0E-5);
        config().legacyRegeneration = false;
        prepare(t, 10.0F, 17, 5.0F);
        t.check("off: health gained in 100 ticks", gainedOverSlowTime(t)).near(0.0, 1.0E-5);
    }

    private static void gameRuleOff(TestContext t) {
        prepare(t, 10.0F, 20, 5.0F);
        try {
            t.onServer(level -> {
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, level.getServer());
                return null;
            });
            t.check("on, naturalRegeneration false: health gained in 100 ticks", gainedOverSlowTime(t))
                .near(0.0, 1.0E-5);
        } finally {
            t.onServer(level -> {
                level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(true, level.getServer());
                return null;
            });
        }
    }
}
