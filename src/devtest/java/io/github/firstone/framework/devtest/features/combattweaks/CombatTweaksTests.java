package io.github.firstone.framework.devtest.features.combattweaks;

import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.features.combattweaks.CombatTweaksConfig;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Tests of Combat Tweaks: no attack cooldown and the sweep attack rules
 *
 * <p>Hits are made by the server's player inside one server task and measured as health lost, so the results are
 * exact: a fully charged iron sword hit does 6 damage, the 1.21.1 formula scales it by
 * {@code 0.2 + charge² × 0.8}.</p>
 */
public final class CombatTweaksTests implements FeatureTests {

    /** Damage of an iron sword */
    private static final double IRON_SWORD_DAMAGE = 6.0;

    /** Health of the test cows, enough for many hits */
    private static final float HEALTH = 1000.0F;

    @Override
    public String id() {
        return "combat_tweaks";
    }

    @Override
    public List<Object> configs() {
        return List.of(CombatTweaksFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        return List.of(
            TestCase.of("No Attack Cooldown: hits right after each other do full damage; off: 1.21.1 cooldown",
                this::attackCooldown),
            TestCase.of("No Attack Cooldown: the client's attack indicator is always full; off: it refills",
                this::clientIndicator),
            TestCase.of("Sweep: every combination of the two options, with and without Sweeping Edge", this::sweep)
        );
    }

    private static CombatTweaksConfig config() {
        return CombatTweaksFeature.getConfig();
    }

    /** Spawns a cow that does not move by itself and has health for many hits */
    private static Spawned<Cow> target(TestContext t, double x, double z) {
        return t.spawn(EntityType.COW, x, 0.0, z, 0.0F, cow -> {
            cow.setNoAi(true);
            AttributeInstance health = cow.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(HEALTH);
            }
            cow.setHealth(HEALTH);
        });
    }

    /** Stands the player on the floor 1.5 blocks at -x of (4.5, 4.5), facing +x, with an iron sword */
    private static void standReady(TestContext t, ItemStack sword) {
        t.teleport(3.0, 0.0, 4.5, -90.0F, 0.0F);
        t.setFlying(false);
        t.holdItem(sword);
        t.ticks(10);
    }

    /**
     * The server's player hits the first cow; returns the health each cow lost
     *
     * @param resetCharge hit right after another hit (attack not charged)
     */
    private static double[] hit(TestContext t, boolean resetCharge, List<Spawned<Cow>> cows) {
        return t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            Cow[] entities = new Cow[cows.size()];
            for (int i = 0; i < entities.length; i++) {
                entities[i] = (Cow) level.getEntity(cows.get(i).id());
                t.require(entities[i] != null, "A cow is gone");
                entities[i].setHealth(HEALTH);
                entities[i].invulnerableTime = 0;
                entities[i].hurtTime = 0;
                entities[i].setDeltaMovement(Vec3.ZERO);
            }
            player.setSprinting(false);
            if (resetCharge) {
                player.resetAttackStrengthTicker();
            }
            player.attack(entities[0]);
            double[] lost = new double[entities.length];
            for (int i = 0; i < lost.length; i++) {
                lost[i] = HEALTH - entities[i].getHealth();
            }
            return lost;
        });
    }

    private void attackCooldown(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        standReady(t, new ItemStack(Items.IRON_SWORD));
        t.check("on: first hit damage", hit(t, false, List.of(cow))[0]).near(IRON_SWORD_DAMAGE, 1.0E-4);
        t.check("on: hit right after it", hit(t, true, List.of(cow))[0]).near(IRON_SWORD_DAMAGE, 1.0E-4);
        config().noAttackCooldown = false;
        t.ticks(30);
        t.check("off: first hit damage (charged)", hit(t, false, List.of(cow))[0]).near(IRON_SWORD_DAMAGE, 1.0E-4);
        double weak = hit(t, true, List.of(cow))[0];
        // right after a hit the charge is about 0.04: 6 × (0.2 + 0.04² × 0.8) ≈ 1.2
        t.check("off: hit right after it (1.21.1 cooldown)", weak).between(1.2, 1.3);
    }

    private void clientIndicator(TestContext t) {
        standReady(t, new ItemStack(Items.IRON_SWORD));
        t.player().resetAttackStrengthTicker();
        t.check("on: attack charge right after a hit", (double) t.player().getAttackStrengthScale(0.0F))
            .near(1.0, 1.0E-6);
        config().noAttackCooldown = false;
        t.player().resetAttackStrengthTicker();
        t.check("off: attack charge right after a hit", (double) t.player().getAttackStrengthScale(0.0F))
            .atMost(0.1);
        t.ticks(20);
        t.check("off: attack charge a second later", (double) t.player().getAttackStrengthScale(0.0F))
            .near(1.0, 1.0E-6);
    }

    /** Hits the middle of three cows standing side by side; returns how many neighbors the sweep hurt */
    private static int sweptNeighbors(TestContext t, List<Spawned<Cow>> cows) {
        double[] lost = hit(t, false, cows);
        t.require(lost[0] > 0.0, "The hit did no damage");
        int swept = 0;
        for (int i = 1; i < lost.length; i++) {
            if (lost[i] > 0.0) {
                swept++;
            }
        }
        return swept;
    }

    private void sweep(TestContext t) {
        List<Spawned<Cow>> cows = List.of(target(t, 4.5, 4.5), target(t, 4.5, 5.5), target(t, 4.5, 3.5));
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        ItemStack sweeping = t.enchanted(Items.IRON_SWORD, Enchantments.SWEEPING_EDGE, 3);
        // {disable sweeping, Sweeping Edge required, sword has Sweeping Edge, neighbors hurt}
        Object[][] cases = {
            {true, true, false, 0}, {true, true, true, 2},
            {true, false, false, 0}, {true, false, true, 0},
            {false, true, false, 2}, {false, true, true, 2},
            {false, false, false, 2}, {false, false, true, 2},
        };
        for (Object[] c : cases) {
            config().disableSweepingAttack = (Boolean) c[0];
            config().sweepingEdgeRequired = (Boolean) c[1];
            standReady(t, (Boolean) c[2] ? sweeping : plain);
            String name = "disable " + c[0] + ", Sweeping Edge required " + c[1] + ", sword with Sweeping Edge "
                + c[2] + ": cows hurt by the sweep";
            t.check(name, sweptNeighbors(t, cows)).isEqualTo(c[3]);
        }
    }
}
