package io.github.firstone.framework.devtest.features.legacymechanics;

import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.features.legacymechanics.food.RegenerationTests;
import io.github.firstone.framework.devtest.features.legacymechanics.sword.SwordBlockingTests;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Tests of Legacy Mechanics: knockback, attack knockback and the crouching hitbox (its boat rules are tested with
 * Legacy Vehicles, as they work together)
 *
 * <p>Every hit is made and measured inside one server task, so the results are exact. The expected values are the
 * 1.7.10 formulas ({@code EntityLivingBase.knockBack}, {@code EntityPlayer.attackTargetEntityWithCurrentItem}) with
 * the option on, and the 1.21.1 ones ({@code LivingEntity.knockback}) with it off.</p>
 */
public final class LegacyMechanicsTests implements FeatureTests {

    /** 1.7.10 and 1.21.1 knockback strength of a hit */
    private static final double STRENGTH = 0.4;

    /** Allowed difference of velocities computed the same way */
    private static final double EXACT = 1.0E-6;

    @Override
    public String id() {
        return "legacy_mechanics";
    }

    @Override
    public List<Object> configs() {
        // Combat Tweaks: No Attack Cooldown decides whether a hit is fully charged
        return List.of(LegacyMechanicsFeature.getConfig(), CombatTweaksFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        List<TestCase> tests = new ArrayList<>(List.of(
            TestCase.of("Knockback: a hit on the ground pushes 0.4 away and 0.4 up (same in 1.21.1)", this::onGround),
            TestCase.of("Knockback: a hit in the air pushes up too; off: the fall goes on", this::inAir),
            TestCase.of("Knockback: an arrow pushes away from the shooter; off: along its flight", this::arrow),
            TestCase.of("Knockback: full resistance stops it (on and off)", this::fullResistance),
            TestCase.of("Knockback: half resistance is a chance of all or nothing; off: always half",
                this::halfResistance),
            TestCase.of("Knockback: an explosion with a cause pushes away from it; off: no push", this::explosion),
            TestCase.of("Attack knockback: a sprint hit without full charge still knocks back; off: no extra",
                this::sprintWithoutCharge),
            TestCase.of("Attack knockback: extra knockback is a plain push; off: 1.21.1 knockback",
                this::extraKnockback),
            TestCase.of("Attack knockback: a Knockback sword pushes the same way", this::knockbackSword),
            TestCase.of("Attack knockback: mobs with attack knockback push the same way", this::mobAttack),
            TestCase.of("Crouch hitbox: crouching keeps the 1.8 box and 1.62 eyes on the server; off: 1.5 and 1.27",
                this::crouchHitbox),
            TestCase.of("Crouch hitbox: mobs look at a crouching player's face as when standing; off: lower",
                this::mobsLookAtFace)
        ));
        // parts with their own test classes, in the folders of the feature's parts
        tests.addAll(RegenerationTests.tests());
        tests.addAll(SwordBlockingTests.tests());
        return tests;
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** Spawns a cow that does not move by itself and has health for many hits */
    private static Spawned<Cow> target(TestContext t, double x, double z) {
        return t.spawn(EntityType.COW, x, 0.0, z, 0.0F, cow -> {
            cow.setNoAi(true);
            AttributeInstance health = cow.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(1000.0);
            }
            cow.setHealth(1000.0F);
        });
    }

    /**
     * Hurts the cow once on the server thread, starting from a known motion, and returns its new motion
     *
     * @param motion   the cow's motion before the hit
     * @param onGround whether the cow stands on the ground
     * @param source   makes the damage source from the level and the server's player
     */
    private static Vec3 hurt(TestContext t, Spawned<Cow> cow, Vec3 motion, boolean onGround,
                             BiFunction<ServerLevel, ServerPlayer, DamageSource> source) {
        return t.onServer(level -> {
            Cow entity = (Cow) level.getEntity(cow.id());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(entity != null && player != null, "The cow or the player is gone");
            prepare(entity, motion, onGround);
            entity.hurt(source.apply(level, player), 1.0F);
            return entity.getDeltaMovement();
        });
    }

    private static void prepare(Cow cow, Vec3 motion, boolean onGround) {
        cow.setDeltaMovement(motion);
        cow.setOnGround(onGround);
        cow.invulnerableTime = 0;
        cow.hurtTime = 0;
    }

    /** 1.7.10 {@code EntityLivingBase.knockBack}: away from the attacker at {@code (dx, dz)} from the victim */
    private static Vec3 legacyKnockback(Vec3 motion, double dx, double dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        return new Vec3(motion.x / 2.0 - dx / distance * STRENGTH, Math.min(STRENGTH, motion.y / 2.0 + STRENGTH),
            motion.z / 2.0 - dz / distance * STRENGTH);
    }

    /** 1.21.1 {@code LivingEntity.knockback(strength, dx, dz)} */
    private static Vec3 vanillaKnockback(Vec3 motion, double strength, double dx, double dz, boolean onGround) {
        if (strength <= 0.0) {
            return motion;
        }
        Vec3 push = new Vec3(dx, 0.0, dz).normalize().scale(strength);
        return new Vec3(motion.x / 2.0 - push.x, onGround ? Math.min(0.4, motion.y / 2.0 + strength) : motion.y,
            motion.z / 2.0 - push.z);
    }

    private static void checkVelocity(TestContext t, String name, Vec3 actual, Vec3 expected) {
        t.check(name + ": x", actual.x).near(expected.x, EXACT);
        t.check(name + ": y", actual.y).near(expected.y, EXACT);
        t.check(name + ": z", actual.z).near(expected.z, EXACT);
    }

    /** Where the server's player stands, relative to the cow: {dx, dz} */
    private static double[] playerOffset(TestContext t, Spawned<Cow> cow) {
        return t.onServer(level -> {
            Cow entity = (Cow) level.getEntity(cow.id());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(entity != null && player != null, "The cow or the player is gone");
            return new double[] {player.getX() - entity.getX(), player.getZ() - entity.getZ()};
        });
    }

    // ---- knockback ------------------------------------------------------------------------------------------------

    private void onGround(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        t.teleport(2.5, 0.0, 3.0, 0.0F, 0.0F);
        double[] offset = playerOffset(t, cow);
        Vec3 before = new Vec3(0.1, 0.0, -0.05);
        Vec3 expected = legacyKnockback(before, offset[0], offset[1]);
        checkVelocity(t, "on", hurt(t, cow, before, true, (level, p) -> level.damageSources().playerAttack(p)),
            expected);
        config().legacyKnockback = false;
        checkVelocity(t, "off", hurt(t, cow, before, true, (level, p) -> level.damageSources().playerAttack(p)),
            vanillaKnockback(before, STRENGTH, offset[0], offset[1], true));
    }

    private void inAir(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        t.teleport(2.5, 0.0, 3.0, 0.0F, 0.0F);
        double[] offset = playerOffset(t, cow);
        Vec3 falling = new Vec3(0.0, -0.3, 0.0);
        Vec3 on = hurt(t, cow, falling, false, (level, p) -> level.damageSources().playerAttack(p));
        checkVelocity(t, "on", on, legacyKnockback(falling, offset[0], offset[1]));
        config().legacyKnockback = false;
        Vec3 off = hurt(t, cow, falling, false, (level, p) -> level.damageSources().playerAttack(p));
        checkVelocity(t, "off", off, vanillaKnockback(falling, STRENGTH, offset[0], offset[1], false));
    }

    private void arrow(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        t.teleport(1.5, 0.0, 4.5, 0.0F, 0.0F);
        double[] offset = playerOffset(t, cow);
        // the arrow comes from -z, flying toward +z; the shooter stands at -x
        Vec3 flight = new Vec3(0.0, 0.0, 1.5);
        BiFunction<ServerLevel, ServerPlayer, DamageSource> source = (level, player) -> {
            Arrow arrow = EntityType.ARROW.create(level);
            t.require(arrow != null, "Cannot make an arrow");
            arrow.setOwner(player);
            arrow.setPos(TestSite.ORIGIN.getX() + 4.5, TestSite.ORIGIN.getY() + 1.0, TestSite.ORIGIN.getZ() + 3.0);
            arrow.setDeltaMovement(flight);
            return level.damageSources().arrow(arrow, player);
        };
        checkVelocity(t, "on (away from the shooter)", hurt(t, cow, Vec3.ZERO, true, source),
            legacyKnockback(Vec3.ZERO, offset[0], offset[1]));
        config().legacyKnockback = false;
        checkVelocity(t, "off (along the flight)", hurt(t, cow, Vec3.ZERO, true, source),
            vanillaKnockback(Vec3.ZERO, STRENGTH, -flight.x, -flight.z, true));
    }

    private static void setResistance(TestContext t, Spawned<Cow> cow, double resistance) {
        t.onServer(level -> {
            Cow entity = (Cow) level.getEntity(cow.id());
            AttributeInstance attribute = entity == null ? null : entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            t.require(attribute != null, "The cow has no knockback resistance");
            attribute.setBaseValue(resistance);
            return null;
        });
    }

    private void fullResistance(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        setResistance(t, cow, 1.0);
        t.teleport(2.5, 0.0, 3.0, 0.0F, 0.0F);
        Vec3 before = new Vec3(0.1, 0.0, -0.05);
        checkVelocity(t, "on", hurt(t, cow, before, true, (level, p) -> level.damageSources().playerAttack(p)),
            before);
        config().legacyKnockback = false;
        checkVelocity(t, "off", hurt(t, cow, before, true, (level, p) -> level.damageSources().playerAttack(p)),
            before);
    }

    /** Hits the cow 40 times; returns {full knockbacks, none, other} */
    private static int[] hitMany(TestContext t, Spawned<Cow> cow, Vec3 full, Vec3 none) {
        int[] counts = new int[3];
        for (int i = 0; i < 40; i++) {
            Vec3 motion = hurt(t, cow, Vec3.ZERO, true, (level, p) -> level.damageSources().playerAttack(p));
            if (motion.distanceTo(full) < EXACT) {
                counts[0]++;
            } else if (motion.distanceTo(none) < EXACT) {
                counts[1]++;
            } else {
                counts[2]++;
            }
        }
        return counts;
    }

    private void halfResistance(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        setResistance(t, cow, 0.5);
        t.teleport(2.5, 0.0, 3.0, 0.0F, 0.0F);
        double[] offset = playerOffset(t, cow);
        int[] on = hitMany(t, cow, legacyKnockback(Vec3.ZERO, offset[0], offset[1]), Vec3.ZERO);
        t.check("on: full knockbacks of 40 (a 50% chance each)", on[0]).between(8, 32);
        t.check("on: no knockback of 40", on[1]).between(8, 32);
        t.check("on: anything else", on[2]).isEqualTo(0);
        config().legacyKnockback = false;
        Vec3 half = vanillaKnockback(Vec3.ZERO, STRENGTH * 0.5, offset[0], offset[1], true);
        int[] off = hitMany(t, cow, half, Vec3.ZERO);
        t.check("off: half knockbacks of 40", off[0]).isEqualTo(40);
    }

    private void explosion(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        double tntX = 4.5 + 1.0;
        double tntZ = 4.5 + 2.0;
        BiFunction<ServerLevel, ServerPlayer, DamageSource> caused = (level, player) -> {
            PrimedTnt tnt = EntityType.TNT.create(level);
            t.require(tnt != null, "Cannot make TNT");
            tnt.setPos(TestSite.ORIGIN.getX() + tntX, TestSite.ORIGIN.getY(), TestSite.ORIGIN.getZ() + tntZ);
            return level.damageSources().explosion(tnt, player);
        };
        Vec3 expected = legacyKnockback(Vec3.ZERO, tntX - 4.5, tntZ - 4.5);
        checkVelocity(t, "on: TNT lit by a player", hurt(t, cow, Vec3.ZERO, true, caused), expected);
        checkVelocity(t, "on: explosion without a cause",
            hurt(t, cow, Vec3.ZERO, true, (level, p) -> level.damageSources().explosion(null, null)), Vec3.ZERO);
        config().legacyKnockback = false;
        checkVelocity(t, "off: TNT lit by a player", hurt(t, cow, Vec3.ZERO, true, caused), Vec3.ZERO);
    }

    // ---- attack knockback -----------------------------------------------------------------------------------------

    /**
     * The server's player attacks the cow (facing +x, from 1.5 blocks at -x) and returns the cow's new motion
     *
     * @param sprinting      sprint while hitting
     * @param resetCharge    hit right after another hit (attack not charged)
     */
    private static Vec3 attack(TestContext t, Spawned<Cow> cow, boolean sprinting, boolean resetCharge) {
        return t.onServer(level -> {
            Cow entity = (Cow) level.getEntity(cow.id());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(entity != null && player != null, "The cow or the player is gone");
            prepare(entity, Vec3.ZERO, true);
            player.setSprinting(sprinting);
            if (resetCharge) {
                player.resetAttackStrengthTicker();
            }
            player.attack(entity);
            return entity.getDeltaMovement();
        });
    }

    /** Puts the player 1.5 blocks at -x of the cow, facing it (+x) */
    private static Spawned<Cow> attackSetup(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        t.teleport(3.0, 0.0, 4.5, -90.0F, 0.0F);
        t.setFlying(false);
        t.ticks(10);
        return cow;
    }

    /** 1.7.10 {@code addVelocity(-sin(yaw)·k·0.5, 0.1, cos(yaw)·k·0.5)} added to a motion */
    private static Vec3 legacyPush(Vec3 motion, float yaw, double knockback) {
        double sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        double cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return motion.add(-sin * knockback * 0.5, 0.1, cos * knockback * 0.5);
    }

    /** 1.21.1 {@code knockback(k·0.5, sin(yaw), -cos(yaw))} applied to a motion on the ground */
    private static Vec3 vanillaPush(Vec3 motion, float yaw, double knockback) {
        double sin = Mth.sin(yaw * Mth.DEG_TO_RAD);
        double cos = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return vanillaKnockback(motion, knockback * 0.5, sin, -cos, true);
    }

    private void sprintWithoutCharge(TestContext t) {
        CombatTweaksFeature.getConfig().noAttackCooldown = false;
        Spawned<Cow> cow = attackSetup(t);
        double[] offset = playerOffset(t, cow);
        Vec3 base = legacyKnockback(Vec3.ZERO, offset[0], offset[1]);
        checkVelocity(t, "on", attack(t, cow, true, true), legacyPush(base, -90.0F, 1.0));
        config().legacyAttackKnockback = false;
        checkVelocity(t, "off: no extra knockback", attack(t, cow, true, true), base);
    }

    private void extraKnockback(TestContext t) {
        Spawned<Cow> cow = attackSetup(t);
        double[] offset = playerOffset(t, cow);
        Vec3 base = legacyKnockback(Vec3.ZERO, offset[0], offset[1]);
        checkVelocity(t, "on: fully charged sprint hit", attack(t, cow, true, false), legacyPush(base, -90.0F, 1.0));
        config().legacyAttackKnockback = false;
        checkVelocity(t, "off: fully charged sprint hit", attack(t, cow, true, false),
            vanillaPush(base, -90.0F, 1.0));
    }

    private void knockbackSword(TestContext t) {
        Spawned<Cow> cow = attackSetup(t);
        t.holdItem(t.enchanted(Items.WOODEN_SWORD, Enchantments.KNOCKBACK, 2));
        double[] offset = playerOffset(t, cow);
        Vec3 base = legacyKnockback(Vec3.ZERO, offset[0], offset[1]);
        checkVelocity(t, "on: Knockback II", attack(t, cow, false, false), legacyPush(base, -90.0F, 2.0));
        config().legacyAttackKnockback = false;
        checkVelocity(t, "off: Knockback II", attack(t, cow, false, false), vanillaPush(base, -90.0F, 2.0));
        t.holdItem(ItemStack.EMPTY);
    }

    /** A zombie with attack knockback 1, facing +x from 1 block at -x, hits the cow; returns the cow's motion */
    private static Vec3 zombieHit(TestContext t, Spawned<Cow> cow) {
        return t.onServer(level -> {
            Cow entity = (Cow) level.getEntity(cow.id());
            Zombie zombie = EntityType.ZOMBIE.create(level);
            t.require(entity != null && zombie != null, "The cow or the zombie is missing");
            // not added to the world (it would leave in peaceful); it only needs a position and a level
            zombie.moveTo(entity.getX() - 1.0, entity.getY(), entity.getZ(), -90.0F, 0.0F);
            AttributeInstance knockback = zombie.getAttribute(Attributes.ATTACK_KNOCKBACK);
            t.require(knockback != null, "Zombies have no attack knockback");
            knockback.setBaseValue(1.0);
            prepare(entity, Vec3.ZERO, true);
            zombie.doHurtTarget(entity);
            return entity.getDeltaMovement();
        });
    }

    private void mobAttack(TestContext t) {
        Spawned<Cow> cow = target(t, 4.5, 4.5);
        Vec3 base = legacyKnockback(Vec3.ZERO, -1.0, 0.0);
        checkVelocity(t, "on", zombieHit(t, cow), legacyPush(base, -90.0F, 1.0));
        config().legacyAttackKnockback = false;
        checkVelocity(t, "off", zombieHit(t, cow), vanillaPush(base, -90.0F, 1.0));
    }

    // ---- crouching hitbox -----------------------------------------------------------------------------------------

    /** Crouches on the floor and returns the server player's {pose is crouching, height, eye height} */
    private static double[] crouch(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.ticks(5);
        t.hold(t.mc().options.keyShift);
        t.ticks(10);
        double[] result = t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            return new double[] {player.getPose() == Pose.CROUCHING ? 1 : 0, player.getBbHeight(),
                player.getEyeHeight()};
        });
        t.release(t.mc().options.keyShift);
        t.ticks(5);
        return result;
    }

    /** A pig looks at the server's player with its look control (as its AI does); returns the pig's head pitch */
    private static double lookPitch(TestContext t, Spawned<Pig> pig) {
        return t.onServer(level -> {
            Pig entity = (Pig) level.getEntity(pig.id());
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(entity != null && player != null, "The pig or the player is gone");
            for (int i = 0; i < 3; i++) {
                entity.getLookControl().setLookAt(player, 360.0F, 360.0F);
                entity.getLookControl().tick();
            }
            return (double) entity.getXRot();
        });
    }

    /** Returns the pig's head pitch looking at the player {standing, crouching} */
    private static double[] pitches(TestContext t, Spawned<Pig> pig) {
        double standing = lookPitch(t, pig);
        t.hold(t.mc().options.keyShift);
        t.ticks(10);
        double crouching = lookPitch(t, pig);
        t.release(t.mc().options.keyShift);
        t.ticks(10);
        return new double[] {standing, crouching};
    }

    private void mobsLookAtFace(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.ticks(10);
        Spawned<Pig> pig = t.spawn(EntityType.PIG, 4.5, 0.0, 7.5, 180.0F, entity -> entity.setNoAi(true));
        t.ticks(5);
        double[] on = pitches(t, pig);
        t.note("on: pig head pitch {standing, crouching}", on[0] + ", " + on[1]);
        t.check("on: the pig looks at the same height when the player crouches (degrees)", on[1] - on[0])
            .near(0.0, 0.01);
        config().legacyCrouchHitbox = false;
        double[] off = pitches(t, pig);
        t.note("off: pig head pitch {standing, crouching}", off[0] + ", " + off[1]);
        t.check("off: the pig looks lower when the player crouches (degrees)", off[1] - off[0]).atLeast(1.0);
    }

    private void crouchHitbox(TestContext t) {
        double[] on = crouch(t);
        t.check("on: crouching on the server", on[0]).isEqualTo(1.0);
        t.check("on: hitbox height", on[1]).near(1.8, EXACT);
        t.check("on: eye height", on[2]).near(1.62, EXACT);
        config().legacyCrouchHitbox = false;
        double[] off = crouch(t);
        t.check("off: crouching on the server", off[0]).isEqualTo(1.0);
        t.check("off: hitbox height", off[1]).near(1.5, EXACT);
        t.check("off: eye height", off[2]).near(1.27, EXACT);
    }
}
