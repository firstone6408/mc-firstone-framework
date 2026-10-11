package io.github.firstone.framework.devtest.features.animatium.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.mixin.GameRendererInvoker;
import io.github.firstone.framework.devtest.mixin.ServerPlayerAccessor;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Tests of the 1.7.10 hurt camera tilt, part of the Animatium tests
 *
 * <p>A zombie hits the player from either side; the camera rotation of the hurt tilt is then read from the game's own
 * {@code bobHurt} and compared with the formula, the same in both versions apart from the direction: a roll of
 * {@code -sin(g⁴·π) × 14 × Damage Tilt} degrees around an axis turned by the hurt direction (1.7.10: always 0).</p>
 */
public final class HurtTiltTests {

    /** Hurt ticks left when the tilt is read (of 10): near the strongest roll */
    private static final int HURT_TIME = 8;

    /** Hurt duration set by every hit (both versions) */
    private static final float HURT_DURATION = 10.0F;

    /** Largest difference allowed between two rotation matrices (floating point only) */
    private static final double MATRIX_TOLERANCE = 1.0E-5;

    private HurtTiltTests() {}

    /**
     * Returns the hurt tilt tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Hurt Tilt: the camera rolls the same way whichever side the hit comes from; off: away from it",
                HurtTiltTests::direction),
            TestCase.of("Hurt Tilt: the Damage Tilt option still sets how strong the roll is", HurtTiltTests::strength)
        );
    }

    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    /**
     * A zombie standing in front of the survival player, 2 blocks to one side, hits the player once; returns the hurt
     * direction the client received (never straight ahead, so it differs from the 1.7.10 value 0)
     *
     * @param side +1 or -1: which side along x
     */
    private static float hitFrom(TestContext t, int side) {
        Spawned<Zombie> zombie = t.spawn(EntityType.ZOMBIE, 4.5 + side * 2.0, 0.0, 5.5, 0.0F, mob -> {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        });
        t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            Zombie attacker = (Zombie) level.getEntity(zombie.id());
            t.require(attacker != null, "The zombie is gone");
            ((ServerPlayerAccessor) player).devtest$setSpawnInvulnerableTime(0);
            player.invulnerableTime = 0;
            player.setHealth(player.getMaxHealth());
            t.require(player.hurt(level.damageSources().mobAttack(attacker), 1.0F), "The hit did no damage");
            return null;
        });
        t.ticks(3);
        t.removeEntities();
        return t.onClientThread(() -> t.player().getHurtDir());
    }

    /** Reads the camera rotation of the hurt tilt, HURT_TIME ticks into the hurt animation */
    private static Matrix4f tilt(TestContext t) {
        return t.onClientThread(() -> {
            t.player().hurtTime = HURT_TIME;
            PoseStack pose = new PoseStack();
            ((GameRendererInvoker) t.mc().gameRenderer).devtest$bobHurt(pose, 0.0F);
            return new Matrix4f(pose.last().pose());
        });
    }

    /** The hurt tilt formula (both versions): roll around an axis turned by {@code direction} */
    private static Matrix4f expected(float direction, double strength) {
        float g = HURT_TIME / HURT_DURATION;
        g = Mth.sin(g * g * g * g * (float) Math.PI);
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(-direction));
        pose.mulPose(Axis.ZP.rotationDegrees((float) (-g * 14.0 * strength)));
        pose.mulPose(Axis.YP.rotationDegrees(direction));
        return new Matrix4f(pose.last().pose());
    }

    /** Largest difference between the elements of two matrices */
    private static double difference(Matrix4f a, Matrix4f b) {
        float[] x = a.get(new float[16]);
        float[] y = b.get(new float[16]);
        double largest = 0.0;
        for (int i = 0; i < 16; i++) {
            largest = Math.max(largest, Math.abs(x[i] - y[i]));
        }
        return largest;
    }

    private static void standToBeHit(TestContext t) {
        t.command("difficulty easy");
        t.teleport(4.5, 0.0, 4.5, 0.0F, 0.0F);
        t.setFlying(false);
        t.gameMode(GameType.SURVIVAL);
        t.ticks(5);
    }

    private static double damageTilt(TestContext t) {
        return t.onClientThread(() -> t.mc().options.damageTiltStrength().get());
    }

    private static void direction(TestContext t) {
        standToBeHit(t);
        double strength = damageTilt(t);
        float left = hitFrom(t, 1);
        Matrix4f leftOn = tilt(t);
        float right = hitFrom(t, -1);
        Matrix4f rightOn = tilt(t);
        t.note("hurt directions received (left / right)", left + " / " + right);
        t.require(Math.abs(Mth.wrapDegrees(left - right)) > 90.0F, "The two hits came from the same direction");
        t.require(Math.abs(Mth.wrapDegrees(left)) > 10.0F && Math.abs(Mth.wrapDegrees(right)) > 10.0F,
            "A hit came from straight ahead, where both versions roll the same");
        t.check("on, hit from one side: roll only, like 1.7.10", difference(leftOn, expected(0.0F, strength)))
            .atMost(MATRIX_TOLERANCE);
        t.check("on, hit from the other side: the same roll", difference(rightOn, expected(0.0F, strength)))
            .atMost(MATRIX_TOLERANCE);

        config().legacyHurtTilt = false;
        float leftOff = hitFrom(t, 1);
        Matrix4f leftTurned = tilt(t);
        float rightOff = hitFrom(t, -1);
        Matrix4f rightTurned = tilt(t);
        t.check("off, hit from one side: turned by the hurt direction (1.21.1)",
            difference(leftTurned, expected(leftOff, strength))).atMost(MATRIX_TOLERANCE);
        t.check("off, hit from the other side: turned by its hurt direction (1.21.1)",
            difference(rightTurned, expected(rightOff, strength))).atMost(MATRIX_TOLERANCE);
        t.check("off: the two sides roll differently", difference(leftTurned, rightTurned)).atLeast(0.05);
    }

    private static void strength(TestContext t) {
        standToBeHit(t);
        OptionInstance<Double> option = t.mc().options.damageTiltStrength();
        double original = damageTilt(t);
        try {
            t.onClientThread(() -> {
                option.set(0.5);
                return null;
            });
            hitFrom(t, 1);
            t.check("on, Damage Tilt 50%: half the roll", difference(tilt(t), expected(0.0F, 0.5)))
                .atMost(MATRIX_TOLERANCE);
            t.onClientThread(() -> {
                option.set(0.0);
                return null;
            });
            hitFrom(t, -1);
            t.check("on, Damage Tilt 0%: no roll", difference(tilt(t), new Matrix4f())).atMost(MATRIX_TOLERANCE);
        } finally {
            t.onClientThread(() -> {
                option.set(original);
                return null;
            });
        }
    }
}
