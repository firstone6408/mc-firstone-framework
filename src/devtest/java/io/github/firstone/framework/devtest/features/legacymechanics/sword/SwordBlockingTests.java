package io.github.firstone.framework.devtest.features.legacymechanics.sword;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.features.legacymechanics.LegacyMechanicsInternals;
import io.github.firstone.framework.devtest.mixin.ServerPlayerAccessor;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Function;

/**
 * Tests of the 1.7.10 sword blocking, part of the Legacy Mechanics tests
 *
 * <p>The player holds right click with a sword, as a player does; the server's view is read for the damage, the
 * client's for what the player sees and how fast they walk. 1.7.10 {@code ItemSword}: a block animation for 72000
 * ticks; {@code EntityPlayer.damageEntity}: damage {@code d} becomes {@code (1 + d) × 0.5} before armor unless it
 * ignores armor.</p>
 */
public final class SwordBlockingTests {

    /** Damage of the test hits, before blocking and armor */
    private static final float HIT = 6.0F;

    private SwordBlockingTests() {}

    /**
     * Returns the sword blocking tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Sword Blocking: holding right click with a sword blocks on both sides; off: nothing (1.21.1)",
                SwordBlockingTests::blocking),
            TestCase.of("Sword Blocking: damage is (1 + damage) / 2 before armor; fire, falling and magic are not "
                + "lowered; a sword is not a shield", SwordBlockingTests::damage),
            TestCase.of("Sword Blocking: arrows, player hits, explosions and hits from behind are lowered too; "
                + "1 damage stays 1 (1.7.10)", SwordBlockingTests::otherDamage),
            TestCase.of("Sword Blocking: a shield, food or block in the off hand is used instead; a stick is not",
                SwordBlockingTests::offHand),
            TestCase.of("Sword Blocking: blocking slows walking, like any item in use (1.7.10)",
                SwordBlockingTests::walking),
            TestCase.of("Sword Blocking: the client follows what the server says", SwordBlockingTests::serverSays)
        );
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    private static KeyMapping use(TestContext t) {
        return t.mc().options.keyUse;
    }

    private static ServerPlayer serverPlayer(TestContext t, ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
        t.require(player != null, "The server has no player");
        return player;
    }

    /** What the client and the server say the player is using: {client item, server item, client animation} */
    private record InUse(ItemStack client, ItemStack server, UseAnim animation) {

        boolean sword() {
            return client.is(Items.DIAMOND_SWORD) && server.is(Items.DIAMOND_SWORD);
        }
    }

    /** Holds right click for some ticks (looking at the sky, so no block is clicked) and reads what is in use */
    private static InUse holdUse(TestContext t) {
        t.onClientThread(() -> {
            t.player().setXRot(-90.0F);
            return null;
        });
        t.hold(use(t));
        t.ticks(8);
        ItemStack client = t.onClientThread(() -> t.player().isUsingItem() ? t.player().getUseItem().copy()
            : ItemStack.EMPTY);
        UseAnim animation = t.onClientThread(() -> t.player().getUseItem().getUseAnimation());
        ItemStack server = t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            return player.isUsingItem() ? player.getUseItem().copy() : ItemStack.EMPTY;
        });
        return new InUse(client, server, animation);
    }

    private static void releaseUse(TestContext t) {
        t.release(use(t));
        t.ticks(5);
    }

    private static void blocking(TestContext t) {
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        InUse on = holdUse(t);
        t.check("on: the sword is in use on the client and the server", on.sword()).isTrue();
        t.check("on: block animation", on.animation() == UseAnim.BLOCK).isTrue();
        int duration = t.onClientThread(() -> t.player().getUseItem().getUseDuration(t.player()));
        t.check("on: use duration (1.7.10: 72000)", duration).isEqualTo(72000);
        releaseUse(t);
        t.check("released: no longer in use", t.onClientThread(() -> t.player().isUsingItem())).isFalse();

        config().legacySwordBlocking = false;
        t.ticks(3);
        InUse off = holdUse(t);
        t.check("off: nothing in use on the client", off.client().isEmpty()).isTrue();
        t.check("off: nothing in use on the server", off.server().isEmpty()).isTrue();
        releaseUse(t);
    }

    /** A survival player in normal difficulty (mob hits are not scaled), a zombie in front to hit them */
    private static Spawned<Zombie> prepare(TestContext t) {
        t.difficulty(Difficulty.NORMAL);
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.setFlying(false);
        t.gameMode(GameType.SURVIVAL);
        return t.spawn(EntityType.ZOMBIE, 4.5, 0.0, 6.5, 180.0F, zombie -> {
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
        });
    }

    /** Hurts the server's player once with a damage source; returns the health lost */
    private static double hurt(TestContext t, Function<ServerLevel, DamageSource> source, float damage) {
        return t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            ((ServerPlayerAccessor) player).devtest$setSpawnInvulnerableTime(0);
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            player.hurt(source.apply(level), damage);
            return (double) (player.getMaxHealth() - player.getHealth());
        });
    }

    private static Function<ServerLevel, DamageSource> zombieHit(TestContext t, Spawned<Zombie> zombie) {
        return level -> level.damageSources().mobAttack((LivingEntity) level.getEntity(zombie.id()));
    }

    private static void damage(TestContext t) {
        Spawned<Zombie> zombie = prepare(t);
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        t.check("not blocking: zombie hit", hurt(t, zombieHit(t, zombie), HIT)).near(HIT, 1.0E-4);
        t.require(holdUse(t).sword(), "The sword is not in use");
        t.check("blocking: zombie hit from the front (1.7.10: (1 + 6) / 2, not 0 like a shield)",
            hurt(t, zombieHit(t, zombie), HIT)).near((1.0 + HIT) * 0.5, 1.0E-4);
        t.check("blocking: the server does not see a raised shield",
            t.onServer(level -> serverPlayer(t, level).isBlocking())).isFalse();
        t.check("blocking: magic (ignores armor)", hurt(t, level -> level.damageSources().magic(), HIT))
            .near(HIT, 1.0E-4);
        t.check("blocking: falling (ignores armor)", hurt(t, level -> level.damageSources().fall(), HIT))
            .near(HIT, 1.0E-4);
        t.check("blocking: fire (ignores armor)", hurt(t, level -> level.damageSources().onFire(), HIT))
            .near(HIT, 1.0E-4);
        // with armor, the block comes first: armor works on (1 + 6) / 2
        t.withServerPlayer(player -> player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)));
        t.ticks(2);
        double expected = t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            return (double) CombatRules.getDamageAfterAbsorb(player, (1.0F + HIT) * 0.5F,
                zombieHit(t, zombie).apply(level), (float) player.getAttributeValue(Attributes.ARMOR),
                (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        });
        t.check("blocking with an iron chestplate: armor applied after the block", hurt(t, zombieHit(t, zombie), HIT))
            .near(expected, 1.0E-4);
        releaseUse(t);

        config().legacySwordBlocking = false;
        t.ticks(3);
        holdUse(t);
        t.check("off: zombie hit while holding right click (no armor change: compare with armor only)",
            hurt(t, zombieHit(t, zombie), HIT)).near(t.onServer(level -> {
                ServerPlayer player = serverPlayer(t, level);
                return (double) CombatRules.getDamageAfterAbsorb(player, HIT, zombieHit(t, zombie).apply(level),
                    (float) player.getAttributeValue(Attributes.ARMOR),
                    (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            }), 1.0E-4);
        releaseUse(t);
    }

    private static void otherDamage(TestContext t) {
        Spawned<Zombie> zombie = prepare(t);
        Spawned<Zombie> behind = t.spawn(EntityType.ZOMBIE, 4.5, 0.0, 2.5, 0.0F, mob -> {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        });
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        t.require(holdUse(t).sword(), "The sword is not in use");
        double lowered = (1.0 + HIT) * 0.5;
        t.check("blocking: arrow (1.7.10: (1 + 6) / 2)", hurt(t, level -> level.damageSources().arrow(
            new Arrow(EntityType.ARROW, level), (LivingEntity) level.getEntity(zombie.id())), HIT))
            .near(lowered, 1.0E-4);
        t.check("blocking: player hit (1.7.10: (1 + 6) / 2)", hurt(t, level -> level.damageSources()
            .playerAttack(serverPlayer(t, level)), HIT)).near(lowered, 1.0E-4);
        t.check("blocking: explosion (1.7.10: (1 + 6) / 2)", hurt(t, level -> level.damageSources()
            .explosion(null, null), HIT)).near(lowered, 1.0E-4);
        t.check("blocking: zombie hit from behind (1.7.10: no direction, (1 + 6) / 2)",
            hurt(t, zombieHit(t, behind), HIT)).near(lowered, 1.0E-4);
        t.check("blocking: a 1 damage hit stays 1 (1.7.10: (1 + 1) / 2)", hurt(t, zombieHit(t, zombie), 1.0F))
            .near(1.0, 1.0E-4);
        t.check("blocking: a 10 damage hit (1.7.10: 5.5)", hurt(t, zombieHit(t, zombie), 10.0F)).near(5.5, 1.0E-4);
        releaseUse(t);
    }

    private static void holdOffHand(TestContext t, ItemStack stack) {
        t.withServerPlayer(player -> player.setItemInHand(InteractionHand.OFF_HAND, stack.copy()));
        t.ticks(3);
    }

    private static void offHand(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        holdOffHand(t, new ItemStack(Items.SHIELD));
        InUse shield = holdUse(t);
        t.check("shield in the off hand: the shield is used", shield.client().is(Items.SHIELD)).isTrue();
        t.check("shield in the off hand: a raised shield on the server (1.21.1)",
            t.onServer(level -> serverPlayer(t, level).isBlocking())).isTrue();
        releaseUse(t);
        holdOffHand(t, new ItemStack(Items.COOKED_BEEF));
        t.withServerPlayer(player -> player.getFoodData().setFoodLevel(10));
        t.ticks(2);
        t.check("food in the off hand: the food is eaten", holdUse(t).client().is(Items.COOKED_BEEF)).isTrue();
        releaseUse(t);
        holdOffHand(t, new ItemStack(Items.COBBLESTONE));
        t.check("block in the off hand: the sword does not block", holdUse(t).client().isEmpty()).isTrue();
        releaseUse(t);
        holdOffHand(t, new ItemStack(Items.STICK));
        t.check("stick in the off hand: the sword blocks", holdUse(t).sword()).isTrue();
        releaseUse(t);
    }

    /** Walks forward on the floor for some ticks; returns the distance the client moved */
    private static double walk(TestContext t, boolean blocking) {
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.setFlying(false);
        t.ticks(5);
        if (blocking) {
            t.hold(use(t));
            t.ticks(3);
        }
        Vec3 start = t.player().position();
        t.hold(t.mc().options.keyUp);
        t.ticks(20);
        t.release(t.mc().options.keyUp);
        double distance = t.player().position().distanceTo(start);
        if (blocking) {
            releaseUse(t);
        }
        return distance;
    }

    private static void walking(TestContext t) {
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        double normal = walk(t, false);
        double blocking = walk(t, true);
        t.note("distance walked in 20 ticks (normal / blocking)", String.format("%.3f / %.3f", normal, blocking));
        t.check("blocking: walking speed compared to normal (1.7.10: 0.2 of the input)", blocking / normal)
            .atMost(0.35);
    }

    private static void serverSays(TestContext t) {
        t.check("joined a server with the mod: the client was told swords block",
            LegacyMechanicsInternals.clientAllowsSwordBlocking()).isTrue();
        config().legacySwordBlocking = false;
        t.ticks(3);
        t.check("setting turned off: the client is told within a few ticks",
            LegacyMechanicsInternals.clientAllowsSwordBlocking()).isFalse();
        config().legacySwordBlocking = true;
        t.ticks(3);
        t.check("setting turned on again: the client is told", LegacyMechanicsInternals.clientAllowsSwordBlocking())
            .isTrue();
        // the server does not block now, like a server without the mod; what the client was told decides whether
        // it starts blocking by itself
        config().legacySwordBlocking = false;
        t.ticks(3);
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        try {
            LegacyMechanicsInternals.setClientAllowsSwordBlocking(true);
            t.check("told so (wrongly): the client starts blocking by itself", holdUse(t).client()
                .is(Items.DIAMOND_SWORD)).isTrue();
            releaseUse(t);
            LegacyMechanicsInternals.setClientAllowsSwordBlocking(false);
            t.check("not told: the client does not start blocking", holdUse(t).client().isEmpty()).isTrue();
            releaseUse(t);
        } finally {
            LegacyMechanicsInternals.setClientAllowsSwordBlocking(config().legacySwordBlocking);
        }
    }
}
