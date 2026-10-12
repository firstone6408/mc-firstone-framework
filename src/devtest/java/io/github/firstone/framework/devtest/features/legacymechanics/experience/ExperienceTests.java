package io.github.firstone.framework.devtest.features.legacymechanics.experience;

import io.github.firstone.framework.devtest.core.SoundRecorder;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.mixin.ExperienceOrbAccessor;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Tests of the 1.7.10 experience orbs, part of the Legacy Mechanics tests
 *
 * <p>The amount of experience must stay 1.21.1's; when a killed mob drops it, where orbs appear and how they move
 * must be 1.7.10's ({@code EntityLivingBase.onDeathUpdate}, {@code EntityXPOrb}). Orbs are mostly created, ticked
 * by hand and measured inside one server task, so the results are exact. With the option off: 1.21.1 (drops at the
 * moment of death, {@code ExperienceOrb.tick}, merging).</p>
 */
public final class ExperienceTests {

    /** How much lower a 1.7.10 orb appears: half its height */
    private static final double ORB_HALF = 0.25;

    /** Gravity of an orb per tick (1.7.10 float, 1.21.1 double) */
    private static final double LEGACY_GRAVITY = 0.03F;
    private static final double VANILLA_GRAVITY = 0.03;

    /** Air friction per tick (1.7.10 float on every axis; 1.21.1 float sideways, double up and down) */
    private static final double LEGACY_AIR = 0.98F;
    private static final double VANILLA_AIR_Y = 0.98;

    /** Speeds a kick can give, after one tick of air friction */
    private static final double MIN_KICK = 0.1 * LEGACY_AIR;
    private static final double MAX_KICK = 0.3 * LEGACY_AIR;

    /** How much of the other speeds 1.21.1 keeps when it kicks an orb */
    private static final double VANILLA_KEEP = 0.75;

    /** Experience of a zombie without equipment (1.21.1), and the orbs it is split into */
    private static final int ZOMBIE_EXPERIENCE = 5;
    private static final int ZOMBIE_ORBS = 3;

    /** Ticks from death until the body disappears (both versions) */
    private static final int DEATH_TICKS = 20;

    /** How far an orb looks for a player */
    private static final double FOLLOW = 8.0;

    /** Allowed difference of values computed the same way */
    private static final double EXACT = 1.0E-6;

    /** Where in the world the site's coordinates start */
    private static final BlockPos ORIGIN = TestSite.ORIGIN;

    private ExperienceTests() {}

    /**
     * Returns the experience tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Experience: a killed mob drops its orbs when its body disappears, the 1.21.1 amount; off: at "
                + "once", ExperienceTests::deathTiming),
            TestCase.of("Experience: a dying mob removed early drops its orbs then; off: dropped at death",
                ExperienceTests::removedEarly),
            TestCase.of("Experience: a dying mob saved and loaded again (chunk unload) still drops its orbs; off: "
                + "dropped at death", ExperienceTests::savedWhileDying),
            TestCase.of("Experience: orbs appear 0.25 lower, the client puts them where the server does; off: 1.21.1 "
                + "height", ExperienceTests::spawnHeight),
            TestCase.of("Experience: orbs never merge; off: 1.21.1 merges them, the same amount",
                ExperienceTests::noMerging),
            TestCase.of("Experience: an orb in the ground is kicked like 1.7.10, the other speeds kept; off: 1.21.1 "
                + "kick", ExperienceTests::kick),
            TestCase.of("Experience: orbs sink in water; off: they float up (1.21.1)", ExperienceTests::water),
            TestCase.of("Experience: lava throws an orb up when its middle is in it, with a fizz; off: when its bottom "
                + "is, no fizz", ExperienceTests::lava),
            TestCase.of("Experience: an orb flies to the player's eyes from its middle; off: to the player's middle "
                + "once a second", ExperienceTests::flyToPlayer),
            TestCase.of("Experience: some orbs wait up to 20 ticks before looking for a player (1.7.10)",
                ExperienceTests::searchDelay),
            TestCase.of("Experience: smelting experience appears half a block above and south of the player; off: at "
                + "the feet", ExperienceTests::furnace)
        );
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static ServerPlayer serverPlayer(TestContext t, ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
        t.require(player != null, "The server has no player");
        return player;
    }

    /** The orbs around a position: how many entities, how many orbs they stand for, and their experience */
    private record Orbs(int entities, int orbs, int experience) {}

    private static Orbs orbsNear(ServerLevel level, Vec3 at) {
        int entities = 0;
        int orbs = 0;
        int experience = 0;
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(3.0))) {
            int count = ((ExperienceOrbAccessor) orb).devtest$getCount();
            entities++;
            orbs += count;
            experience += orb.getValue() * count;
        }
        return new Orbs(entities, orbs, experience);
    }

    /** The orbs in a box */
    private static ExperienceOrb[] orbsIn(ServerLevel level, AABB box) {
        ExperienceOrb[] found = new ExperienceOrb[0];
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box)) {
            found = Arrays.copyOf(found, found.length + 1);
            found[found.length - 1] = orb;
        }
        return found;
    }

    /** An orb with a known motion and its bottom at a site position, not added to the world */
    private static ExperienceOrb placedOrb(ServerLevel level, double x, double y, double z, Vec3 motion) {
        ExperienceOrb orb = new ExperienceOrb(level, 0.0, 0.0, 0.0, 1);
        orb.setPos(ORIGIN.getX() + x, ORIGIN.getY() + y, ORIGIN.getZ() + z);
        orb.setDeltaMovement(motion);
        return orb;
    }

    /** Ticks an orb once by hand and returns its motion */
    private static Vec3 tickOnce(ExperienceOrb orb) {
        orb.tick();
        Vec3 motion = orb.getDeltaMovement();
        orb.discard();
        return motion;
    }

    /** Spawns a zombie that does not move (easy difficulty, so it stays) */
    private static Spawned<Zombie> zombie(TestContext t) {
        t.difficulty(Difficulty.EASY);
        return t.spawn(EntityType.ZOMBIE, 4.5, 0.0, 4.5, 0.0F, zombie -> zombie.setNoAi(true));
    }

    /** Kills the zombie as the player; returns {game time, orbs right after, their experience} */
    private static long[] kill(TestContext t, Spawned<Zombie> zombie) {
        return t.onServer(level -> {
            Entity entity = level.getEntity(zombie.id());
            t.require(entity instanceof Zombie, "The zombie is gone");
            Zombie target = (Zombie) entity;
            target.hurt(level.damageSources().playerAttack(serverPlayer(t, level)), 1000.0F);
            t.require(target.isDeadOrDying(), "The zombie did not die");
            Orbs orbs = orbsNear(level, target.position());
            return new long[] {level.getGameTime(), orbs.orbs, orbs.experience};
        });
    }

    // ---- when and where orbs appear ---------------------------------------------------------------------------------

    /**
     * Kills a zombie and watches until its orbs appear; returns {orbs right after the kill, their experience, ticks
     * from the kill until the orbs appeared, 1 if the body was gone by then, orbs, experience}
     */
    private static long[] killAndWatch(TestContext t) {
        Spawned<Zombie> zombie = zombie(t);
        Vec3 at = new Vec3(ORIGIN.getX() + 4.5, ORIGIN.getY(), ORIGIN.getZ() + 4.5);
        long[] killed = kill(t, zombie);
        for (int i = 0; i < DEATH_TICKS * 3; i++) {
            t.tick();
            long[] seen = t.onServer(level -> {
                ExperienceOrb[] orbs = orbsIn(level, new AABB(at, at).inflate(3.0));
                if (orbs.length == 0) {
                    return null;
                }
                // an orb ticks from the tick after it appeared: game time minus its ticks is when it appeared
                Orbs found = orbsNear(level, at);
                return new long[] {level.getGameTime() - orbs[0].tickCount - killed[0],
                    level.getEntity(zombie.id()) == null ? 1 : 0, found.orbs, found.experience};
            });
            if (seen != null) {
                t.removeEntities();
                return new long[] {killed[1], killed[2], seen[0], seen[1], seen[2], seen[3]};
            }
        }
        throw new AssertionError("No orbs appeared");
    }

    private static void deathTiming(TestContext t) {
        long[] on = killAndWatch(t);
        t.check("on: no orbs at the moment of death (1.7.10)", on[0]).near(0, 0);
        t.check("on: ticks from death to the orbs (1.7.10: 20, with the death smoke)", on[2]).near(DEATH_TICKS, 0);
        t.check("on: the body is gone when the orbs appear", on[3]).near(1, 0);
        t.check("on: orbs (1.21.1 split: 3 + 1 + 1)", on[4]).near(ZOMBIE_ORBS, 0);
        t.check("on: experience (1.21.1: 5)", on[5]).near(ZOMBIE_EXPERIENCE, 0);

        config().legacyExperience = false;
        long[] off = killAndWatch(t);
        t.check("off: orbs at the moment of death (1.21.1)", off[0]).near(ZOMBIE_ORBS, 0);
        t.check("off: experience (1.21.1: 5)", off[1]).near(ZOMBIE_EXPERIENCE, 0);
        t.check("off: the orbs appeared at the kill", off[2]).near(0, 0);
    }

    /** Kills a zombie, removes the body 5 ticks later; returns {orbs after the kill, orbs after the removal} */
    private static int[] killAndRemove(TestContext t) {
        Spawned<Zombie> zombie = zombie(t);
        long[] killed = kill(t, zombie);
        t.ticks(5);
        int after = t.onServer(level -> {
            Entity entity = level.getEntity(zombie.id());
            t.require(entity != null, "The body is already gone");
            Vec3 at = entity.position();
            entity.discard();
            return orbsNear(level, at).experience;
        });
        t.removeEntities();
        return new int[] {(int) killed[2], after};
    }

    private static void removedEarly(TestContext t) {
        int[] on = killAndRemove(t);
        t.check("on: experience at the moment of death", on[0]).near(0, 0);
        t.check("on: experience when the body is removed", on[1]).near(ZOMBIE_EXPERIENCE, 0);
        config().legacyExperience = false;
        int[] off = killAndRemove(t);
        t.check("off: experience at the moment of death (1.21.1)", off[0]).near(ZOMBIE_EXPERIENCE, 0);
        t.check("off: no more when the body is removed", off[1]).near(ZOMBIE_EXPERIENCE, 0);
    }

    /**
     * Kills a zombie, then 5 ticks later saves it and removes it the way an unloading chunk does, loads the save
     * into a new zombie and waits until that one is gone; returns {experience at death, after the unload, at the
     * end, the loaded zombie's death timer}
     */
    private static int[] killSaveAndLoad(TestContext t) {
        Spawned<Zombie> zombie = zombie(t);
        long[] killed = kill(t, zombie);
        Vec3 at = new Vec3(ORIGIN.getX() + 4.5, ORIGIN.getY(), ORIGIN.getZ() + 4.5);
        t.ticks(5);
        int[] unloaded = t.onServer(level -> {
            Entity entity = level.getEntity(zombie.id());
            t.require(entity != null, "The body is already gone");
            CompoundTag saved = entity.saveWithoutId(new CompoundTag());
            entity.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            int afterUnload = orbsNear(level, at).experience;
            Zombie loaded = EntityType.ZOMBIE.create(level);
            t.require(loaded != null, "Cannot create a zombie");
            loaded.load(saved);
            // the unloaded zombie still owns its UUID in this world
            loaded.setUUID(UUID.randomUUID());
            t.require(level.addFreshEntity(loaded), "The loaded zombie was not added");
            return new int[] {afterUnload, loaded.getId(), loaded.deathTime};
        });
        t.waitUntil("the loaded zombie disappears", () -> t.onServer(level -> level.getEntity(unloaded[1]) == null),
            DEATH_TICKS * 2);
        int end = t.onServer(level -> orbsNear(level, at).experience);
        t.removeEntities();
        return new int[] {(int) killed[2], unloaded[0], end, unloaded[2]};
    }

    private static void savedWhileDying(TestContext t) {
        int[] on = killSaveAndLoad(t);
        t.check("on: experience at the moment of death", on[0]).near(0, 0);
        t.check("on: none dropped by the unload", on[1]).near(0, 0);
        t.check("on: dropped when the loaded body disappears (none lost)", on[2]).near(ZOMBIE_EXPERIENCE, 0);
        t.check("on: the loaded zombie goes on dying (saved death timer, 5 or more)", on[3])
            .between(5, DEATH_TICKS - 1);
        config().legacyExperience = false;
        int[] off = killSaveAndLoad(t);
        t.check("off: experience at the moment of death (1.21.1)", off[0]).near(ZOMBIE_EXPERIENCE, 0);
        t.check("off: no more at the end", off[2]).near(ZOMBIE_EXPERIENCE, 0);
    }

    /**
     * Heights of new orbs above the asked height: {an orb made with a position, an orb from {@code award}}, then
     * how far apart the client's and the server's copy of a still orb are
     */
    private static double[] heights(TestContext t) {
        double[] made = t.onServer(level -> {
            double y = ORIGIN.getY() + 3.0;
            ExperienceOrb orb = new ExperienceOrb(level, ORIGIN.getX() + 4.5, y, ORIGIN.getZ() + 4.5, 1);
            double direct = orb.getY() - y;
            Vec3 at = new Vec3(ORIGIN.getX() + 8.5, y, ORIGIN.getZ() + 4.5);
            ExperienceOrb.award(level, at, 7);
            ExperienceOrb[] awarded = orbsIn(level, new AABB(at, at).inflate(1.0));
            t.require(awarded.length == 1, awarded.length + " orbs were awarded, not 1");
            double award = awarded[0].getY() - y;
            awarded[0].discard();
            return new double[] {direct, award};
        });
        // a still orb (no gravity, no motion, far from the player) seen by both sides
        int id = t.onServer(level -> {
            ExperienceOrb orb = new ExperienceOrb(level, ORIGIN.getX() + 12.5, ORIGIN.getY() + 3.0,
                ORIGIN.getZ() + 4.5, 1);
            orb.setNoGravity(true);
            orb.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(orb);
            return orb.getId();
        });
        t.waitUntil("the client sees the orb", () -> t.level().getEntity(id) != null, 40);
        t.ticks(2);
        double server = t.onServer(level -> {
            Entity orb = level.getEntity(id);
            t.require(orb != null, "The server's orb is gone");
            return orb.getY();
        });
        double client = t.onClientThread(() -> {
            Entity orb = t.level().getEntity(id);
            t.require(orb != null, "The client's orb is gone");
            return orb.getY();
        });
        t.removeEntities();
        return new double[] {made[0], made[1], Math.abs(client - server)};
    }

    private static void spawnHeight(TestContext t) {
        double[] on = heights(t);
        t.check("on: orb made with a position - 0.25 (1.7.10)", on[0]).near(-ORB_HALF, EXACT);
        t.check("on: orb from award - 0.25 (1.7.10)", on[1]).near(-ORB_HALF, EXACT);
        t.check("on: client and server agree (lowered once)", on[2]).atMost(0.001);
        config().legacyExperience = false;
        double[] off = heights(t);
        t.check("off: orb made with a position (1.21.1)", off[0]).near(0.0, EXACT);
        t.check("off: orb from award (1.21.1)", off[1]).near(0.0, EXACT);
        t.check("off: client and server agree", off[2]).atMost(0.001);
    }

    /** Awards 120 orbs of 1 at one spot; returns {orb entities, orbs they stand for, experience} */
    private static int[] awardMany(TestContext t) {
        return t.onServer(level -> {
            Vec3 at = new Vec3(ORIGIN.getX() + 4.5, ORIGIN.getY() + 3.0, ORIGIN.getZ() + 4.5);
            for (int i = 0; i < 120; i++) {
                ExperienceOrb.award(level, at, 1);
            }
            Orbs orbs = orbsNear(level, at);
            level.getEntitiesOfClass(ExperienceOrb.class, new AABB(at, at).inflate(3.0)).forEach(Entity::discard);
            return new int[] {orbs.entities, orbs.orbs, orbs.experience};
        });
    }

    private static void noMerging(TestContext t) {
        int[] on = awardMany(t);
        t.check("on: one entity per orb (1.7.10)", on[0]).near(120, 0);
        t.check("on: orbs counted", on[1]).near(120, 0);
        t.check("on: experience", on[2]).near(120, 0);
        config().legacyExperience = false;
        int[] off = awardMany(t);
        t.check("off: fewer entities, orbs merged (1.21.1)", off[0]).atMost(119);
        t.check("off: the same orbs counted", off[1]).near(120, 0);
        t.check("off: the same experience", off[2]).near(120, 0);
    }

    // ---- how orbs move ----------------------------------------------------------------------------------------------

    /** Checks one tick of an orb kicked towards {@code side}; {@code fallen} is its motion if nothing kicked it */
    private static void checkKick(TestContext t, String name, Vec3 after, Vec3 fallen, Direction side) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == side.getAxis()) {
                t.check(name + ": kicked " + side.getName() + " at 0.1-0.3",
                    after.get(axis) * side.getAxisDirection().getStep()).between(MIN_KICK - EXACT, MAX_KICK + EXACT);
            } else {
                t.check(name + ": " + axis.getName() + " speed", after.get(axis)).near(fallen.get(axis), EXACT);
            }
        }
    }

    /** One tick of an orb half in the ground at (x, z) with a fast upward motion, so it leaves the ground at once */
    private static Vec3 kickedOrb(TestContext t, double x, double z, Vec3 motion) {
        return t.onServer(level -> tickOnce(placedOrb(level, x, -ORB_HALF, z, motion)));
    }

    private static void kick(TestContext t) {
        Vec3 motion = new Vec3(0.05, 0.4, 0.07);
        // 1.7.10: other speeds kept, float gravity and friction
        Vec3 legacy = new Vec3(motion.x * LEGACY_AIR, (motion.y - LEGACY_GRAVITY) * LEGACY_AIR, motion.z * LEGACY_AIR);
        // 1.21.1: other speeds x 0.75
        Vec3 vanilla = new Vec3(motion.x * VANILLA_KEEP * LEGACY_AIR,
            (motion.y - VANILLA_GRAVITY) * VANILLA_KEEP * VANILLA_AIR_Y, motion.z * VANILLA_KEEP * LEGACY_AIR);
        // the middle 0.3 from the west side, 0.4 from the south side
        checkKick(t, "on", kickedOrb(t, 4.3, 4.6, motion), legacy, Direction.WEST);
        // the middle of the block: 1.7.10 tries west first
        checkKick(t, "on: tie", kickedOrb(t, 4.5, 4.5, motion), legacy, Direction.WEST);
        config().legacyExperience = false;
        checkKick(t, "off", kickedOrb(t, 4.3, 4.6, motion), vanilla, Direction.WEST);
        checkKick(t, "off: tie (1.21.1 tries north first)", kickedOrb(t, 4.5, 4.5, motion), vanilla,
            Direction.NORTH);
    }

    /** The up and down motion of a still orb after one tick in still water */
    private static double inWater(TestContext t) {
        t.site().fill(3, 0, 3, 5, 2, 5, "water");
        return t.onServer(level -> tickOnce(placedOrb(level, 4.5, 0.5, 4.5, Vec3.ZERO)).y);
    }

    private static void water(TestContext t) {
        t.check("on: sinks (1.7.10: gravity in water too)", inWater(t)).near(-LEGACY_GRAVITY * LEGACY_AIR, EXACT);
        config().legacyExperience = false;
        // 1.21.1 setUnderwaterMovement: + 0.0005 per tick, then air friction
        t.check("off: floats up (1.21.1)", inWater(t)).near(5.0E-4F * VANILLA_AIR_Y, EXACT);
    }

    /**
     * One tick of still orbs over lava: {up and down motion with only the bottom in lava, the same with the middle in
     * lava}, then the fizz sounds heard
     */
    private static double[] overLava(TestContext t) {
        t.site().set(4, 0, 4, "lava");
        // the player is 11 blocks away: near enough to hear, too far to pull the orbs
        t.teleport(4.5, 0.0, 15.5, 0.0F, 0.0F);
        SoundRecorder sounds = t.recordSounds();
        double[] motions = t.onServer(level -> new double[] {
            tickOnce(placedOrb(level, 4.5, 0.9, 4.5, Vec3.ZERO)).y,
            tickOnce(placedOrb(level, 4.5, 0.1, 4.5, Vec3.ZERO)).y
        });
        t.ticks(5);
        return new double[] {motions[0], motions[1], sounds.count("extinguish_fire")};
    }

    private static void lava(TestContext t) {
        double[] on = overLava(t);
        t.check("on: bottom in lava, middle above: falls (1.7.10 looks at the middle)", on[0])
            .near(-LEGACY_GRAVITY * LEGACY_AIR, EXACT);
        t.check("on: middle in lava: thrown up 0.2", on[1]).near(0.2F * LEGACY_AIR, EXACT);
        t.check("on: fizz sounds (1.7.10 random.fizz)", on[2]).near(1, 0);
        config().legacyExperience = false;
        double[] off = overLava(t);
        t.check("off: bottom in lava: thrown up 0.2 (1.21.1 looks at the bottom)", off[0])
            .near(0.2F * VANILLA_AIR_Y, EXACT);
        t.check("off: middle in lava: thrown up 0.2", off[1]).near(0.2F * VANILLA_AIR_Y, EXACT);
        t.check("off: no fizz (1.21.1)", off[2]).near(0, 0);
    }

    /** A new orb whose id ends in a number of the given range (1.7.10 looks for players by the id's last 2 digits) */
    private static ExperienceOrb orbWithId(ServerLevel level, int lowest, int highest, double x, double y, double z) {
        for (int i = 0; i < 200; i++) {
            ExperienceOrb orb = placedOrb(level, x, y, z, Vec3.ZERO);
            int ending = orb.getId() % 100;
            if (ending >= lowest && ending <= highest) {
                return orb;
            }
        }
        throw new AssertionError("No orb id ended in " + lowest + " to " + highest);
    }

    private static void flyToPlayer(TestContext t) {
        t.teleport(4.5, 1.0, 9.5, 0.0F, 0.0F);
        // {measured x, y, z, expected x, y, z}
        double[] on = t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            ExperienceOrb orb = orbWithId(level, 21, 99, 4.5, 2.0, 4.5);
            double middle = orb.getY() + ORB_HALF;
            // 1.7.10: from the orb's middle to the player's eyes, (1 - distance / 8)^2 * 0.1
            Vec3 toEyes = new Vec3(player.getX() - orb.getX(), player.getEyeY() - middle, player.getZ() - orb.getZ())
                .scale(1.0 / FOLLOW);
            double distance = toEyes.length();
            Vec3 pull = toEyes.scale(1.0 / distance * (1.0 - distance) * (1.0 - distance) * 0.1);
            Vec3 expected = new Vec3(pull.x, pull.y - LEGACY_GRAVITY, pull.z).scale(LEGACY_AIR);
            Vec3 after = tickOnce(orb);
            return new double[] {after.x, after.y, after.z, expected.x, expected.y, expected.z};
        });
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            t.check("on: " + axes[i] + " speed towards the eyes (1.7.10)", on[i]).near(on[i + 3], EXACT);
        }

        config().legacyExperience = false;
        double[] off = t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            ExperienceOrb orb = placedOrb(level, 4.5, 2.0, 4.5, Vec3.ZERO);
            // 1.21.1 looks for players when tickCount % 20 == 1
            orb.tickCount = 1;
            Vec3 toMiddle = new Vec3(player.getX() - orb.getX(), player.getY() + player.getEyeHeight() / 2.0
                - orb.getY(), player.getZ() - orb.getZ());
            double closeness = 1.0 - toMiddle.length() / FOLLOW;
            Vec3 pull = toMiddle.normalize().scale(closeness * closeness * 0.1);
            Vec3 expected = new Vec3(pull.x * LEGACY_AIR, (pull.y - VANILLA_GRAVITY) * VANILLA_AIR_Y,
                pull.z * LEGACY_AIR);
            Vec3 after = tickOnce(orb);
            return new double[] {after.x, after.y, after.z, expected.x, expected.y, expected.z};
        });
        for (int i = 0; i < 3; i++) {
            t.check("off: " + axes[i] + " speed towards the player's middle (1.21.1)", off[i]).near(off[i + 3], EXACT);
        }
    }

    private static void searchDelay(TestContext t) {
        t.teleport(4.5, 1.0, 9.5, 0.0F, 0.0F);
        // {id ending, first tick with a pull towards the player}
        int[] seen = t.onServer(level -> {
            ExperienceOrb orb = orbWithId(level, 5, 15, 4.5, 2.0, 4.5);
            int first = -1;
            for (int tick = 0; tick < 25 && first < 0; tick++) {
                orb.tick();
                if (orb.getDeltaMovement().z > 0.0) {
                    first = tick;
                }
            }
            orb.discard();
            return new int[] {orb.getId() % 100, first};
        });
        // 1.7.10 looks when lastSearch < ticks - 20 + id % 100: first at tick 21 - id % 100
        t.check("on: first pull at tick 21 - id % 100 (id ends in " + seen[0] + ")", seen[1])
            .near(21 - seen[0], 0);
    }

    /** Takes smelted gold from a furnace; returns where the orb appeared, from the player's feet {x, y, z} */
    private static double[] smelt(TestContext t) {
        t.site().set(4, 0, 4, "furnace");
        return t.onServer(level -> {
            ServerPlayer player = serverPlayer(t, level);
            BlockPos pos = ORIGIN.offset(4, 0, 4);
            t.require(level.getBlockState(pos).is(Blocks.FURNACE), "No furnace");
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(pos);
            t.require(furnace != null, "The furnace has no block entity");
            RecipeHolder<?> recipe = level.getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("gold_ingot_from_smelting_raw_gold")).orElse(null);
            t.require(recipe != null, "No gold smelting recipe");
            furnace.setRecipeUsed(recipe);
            furnace.awardUsedRecipesAndPopExperience(player);
            ExperienceOrb[] orbs = orbsIn(level, player.getBoundingBox().inflate(2.0));
            t.require(orbs.length == 1, orbs.length + " orbs, not 1 (raw gold gives 1 experience)");
            ExperienceOrb orb = orbs[0];
            Vec3 offset = orb.position().subtract(player.position());
            orb.discard();
            return new double[] {offset.x, offset.y, offset.z};
        });
    }

    private static void furnace(TestContext t) {
        double[] on = smelt(t);
        t.check("on: x (1.7.10: the player's)", on[0]).near(0.0, EXACT);
        t.check("on: y (1.7.10: middle 0.5 above the feet)", on[1]).near(0.5 - ORB_HALF, EXACT);
        t.check("on: z (1.7.10: 0.5 south)", on[2]).near(0.5, EXACT);
        config().legacyExperience = false;
        double[] off = smelt(t);
        t.check("off: x (1.21.1: the feet)", off[0]).near(0.0, EXACT);
        t.check("off: y (1.21.1: the feet)", off[1]).near(0.0, EXACT);
        t.check("off: z (1.21.1: the feet)", off[2]).near(0.0, EXACT);
    }
}
