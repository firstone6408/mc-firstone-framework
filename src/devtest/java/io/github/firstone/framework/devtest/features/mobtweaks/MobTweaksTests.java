package io.github.firstone.framework.devtest.features.mobtweaks;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.MainConfigScreen;
import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.mixin.MobAccessor;
import io.github.firstone.framework.features.mobtweaks.MobTweaksConfig;
import io.github.firstone.framework.features.mobtweaks.MobTweaksFeature;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tests of Mob Tweaks: Enderman Blocks Stay
 *
 * <p>Endermen are spawned on the test site among grass blocks. To make the rare moments of picking up (1 in 10 per
 * look) and putting down (1 in 1000) happen at once, a test runs the enderman's own goals step by step on the server
 * (found by their priorities in 1.21.1: 10 put down, 11 pick up); each step still chooses a random block as the game
 * does. One test lets the enderman's AI run by itself. What changed is read from the world (the blocks of the area),
 * the enderman (what it holds, whether it may despawn), its drops and the client's copy.</p>
 *
 * <p>No 1.7.10 counterpart for the option; 1.7.10 lost a held block on death, which the option also does.</p>
 */
public final class MobTweaksTests implements FeatureTests {

    /** Priorities of the enderman's goals in 1.21.1 ({@code EnderMan.registerGoals}) */
    private static final int PUT_DOWN_GOAL = 10;
    private static final int PICK_UP_GOAL = 11;

    /** Steps of a goal tried before a test gives up (a pick-up step hits a grass block about 1 in 6) */
    private static final int MAX_STEPS = 400;

    /** Where the enderman stands, relative to the site origin (the middle of its block) */
    private static final double X = 5.5;
    private static final double Z = 5.5;

    @Override
    public String id() {
        return "mob_tweaks";
    }

    @Override
    public List<Object> configs() {
        return List.of(MobTweaksFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        return List.of(
            TestCase.of("Enderman Blocks Stay: picking up leaves the block, the enderman holds it (also on the "
                + "client); putting down places nothing; it picks up again", this::forShow),
            TestCase.of("Enderman Blocks Stay: a block held for show does not drop and the enderman may despawn; "
                + "off: the block is taken, placed, dropped and keeps the enderman", this::dropsAndDespawn),
            TestCase.of("Enderman Blocks Stay: the mark is saved; off later never brings the block; a real block "
                + "stays real when it is turned on", this::switching),
            TestCase.of("Enderman Blocks Stay: the enderman's own AI picks up and the area stays the same",
                this::ownAi),
            TestCase.of("Odd cases: every kind of block an enderman holds stays exactly as it was (sand, gravel, TNT, "
                + "pumpkin, melon, clay, dirt, a flower), over many pick-ups", this::blockKinds),
            TestCase.of("Odd cases: game rule mobGriefing off: no picking up, option on or off", this::mobGriefingOff),
            TestCase.of("Odd cases: despawns when the player is far; a named one and a real carrier stay; the cost of "
                + "the check", this::despawn),
            TestCase.of("Odd cases: the block broken while held, a mark left by a command, two endermen, killed by a "
                + "player", this::oddStates),
            TestCase.of("Odd cases: a sculk sensor hears nothing for show; off: it hears the block taken",
                this::sculkSensor),
            TestCase.of("Spawn Only in the Dark: piglins and hoglins refused above the light limit; other reasons, "
                + "zombified piglins and sky light not; off; the limit", this::spawnRule),
            TestCase.of("Attack Only in the Dark: a piglin does not start in the light, does in the dark, keeps "
                + "chasing into the light for the anger time, then stops", this::piglinLight),
            TestCase.of("Attack Only in the Dark: a piglin hit in the light attacks and calls its kind for the anger "
                + "time; a chest opened near it angers it", this::piglinAnger),
            TestCase.of("Attack Only in the Dark: hoglins likewise; hitting one calls hoglins, not piglins",
                this::hoglins),
            TestCase.of("Attack Only in the Dark: bastion mobs and option off attack in the light; brutes, zombified "
                + "piglins and gold armor as in 1.21.1", this::unchanged),
            TestCase.of("Nether world: hoglins and piglins act the same with the option on and off in the dark; in "
                + "the light only starting differs; repellents and Peaceful as in 1.21.1", this::netherWorld),
            TestCase.of("Nether world: 3 hoglins in light 12, one hidden from the hit one: all attack; a baby hit; an "
                + "arrow; off", this::netherGroups),
            TestCase.of("Nether world: piglins barter (thrown and handed gold) in the light, in the dark with gold, "
                + "off; refuse after a hit; mined gold angers", this::netherBarter),
            TestCase.of("Nether world: a minecart on a lit rail is safe, a dark one not; night vision is not light; "
                + "the limit exactly", this::netherRails),
            TestCase.of("Nether world: the game's natural spawner in a crimson forest: no piglins or hoglins on lit "
                + "ground; off: there too", this::netherSpawner),
            TestCase.of("Nether world: anger 5 s and 120 s; a bastion hoglin keeps its mark when saved; zombified "
                + "piglins unchanged", this::netherExtremes),
            TestCase.of("Nether world: two players: one player's hit does not end the anger at the other",
                this::netherTwoPlayers),
            TestCase.of("Nether world: options off leave no trace on mobs; switching the option during a chase",
                this::netherNoTrace),
            TestCase.of("Nether world: other mobs unchanged: spawn rules of every Nether mob, zoglins and brutes in "
                + "the light", this::netherOtherMobs),
            TestCase.of("Piglins and hoglins otherwise as in 1.21.1: hoglins breed, piglins turn into zombified "
                + "piglins in the Overworld", this::otherBehavior),
            TestCase.of("Nether world: 60 piglins and hoglins: server tick time with the options on and off",
                this::netherLoad),
            TestCase.of("Cost: the check each piglin and hoglin gets every tick, idle and while attacking",
                this::cost),
            TestCase.of("Settings: a Mob Tweaks tile on the main screen; the toggle writes its own field and the file; "
                + "Reset", this::settings)
        );
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** Grass blocks around the enderman's feet (the 8 blocks next to it), on the stone floor */
    private static void grassRing(TestContext t) {
        grassRing(t, 0);
    }

    /** Grass blocks around the feet of an enderman standing {@code dx} blocks further along x */
    private static void grassRing(TestContext t, int dx) {
        int x = (int) Math.floor(X) + dx;
        int z = (int) Math.floor(Z);
        t.site().fill(x - 1, 0, z - 1, x + 1, 0, z + 1, "grass_block");
        t.site().set(x, 0, z, "air");
    }

    /** Spawns an enderman without AI (a test runs its goals); hostile mobs need a difficulty above Peaceful */
    private static Spawned<EnderMan> enderman(TestContext t) {
        return enderman(t, 0);
    }

    /** Spawns an enderman without AI {@code dx} blocks further along x */
    private static Spawned<EnderMan> enderman(TestContext t, int dx) {
        t.difficulty(Difficulty.EASY);
        return t.spawn(EntityType.ENDERMAN, X + dx, 0.0, Z, 0.0F, enderman -> enderman.setNoAi(true));
    }

    /** Returns the positions of every non-air block in the area around the enderman (y 0 to 3), as text */
    private static List<String> area(TestContext t) {
        return t.onServer(level -> {
            List<String> blocks = new ArrayList<>();
            BlockPos origin = TestSite.ORIGIN;
            for (int dx = -3; dx <= 20; dx++) {
                for (int dz = -3; dz <= 8; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (!level.getBlockState(pos).isAir()) {
                            blocks.add(dx + "," + dy + "," + dz + "=" + level.getBlockState(pos).getBlock());
                        }
                    }
                }
            }
            return blocks;
        });
    }

    /** Returns the enderman's goal of a priority */
    private static Goal goal(EnderMan enderman, int priority) {
        for (WrappedGoal wrapped : ((MobAccessor) enderman).devtest$getGoalSelector().getAvailableGoals()) {
            if (wrapped.getPriority() == priority) {
                return wrapped.getGoal();
            }
        }
        throw new IllegalStateException("No goal of priority " + priority);
    }

    /** Reads something from the server's enderman of an entity id, on the server thread */
    private static <R> R onEnderman(TestContext t, int id, Function<EnderMan, R> reader) {
        return t.onServer(level -> {
            if (!(level.getEntity(id) instanceof EnderMan enderman) || enderman.isRemoved()) {
                throw new IllegalStateException("The enderman is gone");
            }
            return reader.apply(enderman);
        });
    }

    /** Runs a goal step by step until the enderman is in the wanted state; returns the steps taken, or -1 */
    private static int runGoal(TestContext t, int id, int priority, Predicate<EnderMan> done) {
        return onEnderman(t, id, server -> {
            Goal goal = goal(server, priority);
            for (int step = 1; step <= MAX_STEPS; step++) {
                goal.tick();
                if (done.test(server)) {
                    return step;
                }
            }
            return -1;
        });
    }

    private static int pickUp(TestContext t, int id) {
        return runGoal(t, id, PICK_UP_GOAL, server -> server.getCarriedBlock() != null);
    }

    private static int putDown(TestContext t, int id) {
        return runGoal(t, id, PUT_DOWN_GOAL, server -> server.getCarriedBlock() == null);
    }

    private static boolean holds(TestContext t, int id) {
        return onEnderman(t, id, server -> server.getCarriedBlock() != null);
    }

    /** Kills the enderman and returns how many grass block items dropped near it */
    private static int killAndCountBlockDrops(TestContext t, int id) {
        onEnderman(t, id, server -> {
            server.kill();
            return null;
        });
        t.ticks(5);
        return countBlockDrops(t);
    }

    private static boolean shownOnly(TestContext t, int id) {
        return onEnderman(t, id, server -> MobTweaksInternals.isShownOnly(server));
    }

    private static boolean persistent(TestContext t, int id) {
        return onEnderman(t, id, server -> server.requiresCustomPersistence());
    }

    /** Finds a widget of the open screen (also inside lists and rows) that passes a test */
    @Nullable
    private static AbstractWidget widget(@Nullable ContainerEventHandler parent, Predicate<AbstractWidget> wanted) {
        if (parent == null) {
            return null;
        }
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget found && wanted.test(found)) {
                return found;
            }
            if (child instanceof ContainerEventHandler container) {
                AbstractWidget found = widget(container, wanted);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Presses a usable button of the open screen; returns false if there is none */
    private static boolean press(TestContext t, Predicate<AbstractWidget> wanted) {
        boolean pressed = t.onClientThread(() -> {
            if (widget(t.mc().screen, wanted) instanceof AbstractButton button && button.active) {
                button.onPress();
                return true;
            }
            return false;
        });
        t.ticks(2);
        return pressed;
    }

    private static Predicate<AbstractWidget> key(String key) {
        return widget -> widget.getMessage().getContents() instanceof TranslatableContents contents
            && contents.getKey().equals(key);
    }

    private static void removeMobs(TestContext t) {
        t.removeEntities(Mob.class);
        t.removeEntities(ItemEntity.class);
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private void forShow(TestContext t) {
        try {
            grassRing(t);
            Spawned<EnderMan> spawned = enderman(t);
            int id = spawned.id();
            List<String> before = area(t);
            t.check("picks up a block", pickUp(t, id)).atLeast(1);
            t.check("the area is unchanged after picking up", area(t)).isEqualTo(before);
            t.check("it holds a grass block", onEnderman(t, id, server -> server.getCarriedBlock() != null
                && server.getCarriedBlock().is(Blocks.GRASS_BLOCK))).isTrue();
            t.check("held for show", shownOnly(t, id)).isTrue();
            t.ticks(3);
            EnderMan client = spawned.client();
            t.check("the client shows the block in its hands", client != null && client.getCarriedBlock() != null)
                .isTrue();
            t.saveScreenshot("enderman_holding");
            t.check("puts it down", putDown(t, id)).atLeast(1);
            t.check("the area is unchanged after putting down (nothing placed)", area(t)).isEqualTo(before);
            t.check("hands empty, the mark forgotten", !holds(t, id) && !shownOnly(t, id)).isTrue();
            t.check("picks up again", pickUp(t, id)).atLeast(1);
            t.check("the area is still unchanged", area(t)).isEqualTo(before);
        } finally {
            removeMobs(t);
        }
    }

    private void dropsAndDespawn(TestContext t) {
        try {
            grassRing(t);
            int shown = enderman(t).id();
            t.require(pickUp(t, shown) > 0, "The enderman did not pick up a block");
            t.check("held for show: may despawn", persistent(t, shown)).isFalse();
            t.check("held for show: no block drops on death", killAndCountBlockDrops(t, shown)).isEqualTo(0);
            removeMobs(t);

            MobTweaksFeature.getConfig().endermanBlocksStay = false;
            grassRing(t);
            int real = enderman(t).id();
            List<String> before = area(t);
            t.require(pickUp(t, real) > 0, "The enderman did not pick up a block");
            t.check("off: the block is taken out of the world", area(t).size()).isEqualTo(before.size() - 1);
            t.check("off: not marked", shownOnly(t, real)).isFalse();
            t.check("off: an enderman holding a block never despawns (1.21.1)", persistent(t, real)).isTrue();
            t.check("off: puts it down", putDown(t, real)).atLeast(1);
            t.check("off: the block is placed again", area(t).size()).isEqualTo(before.size());
            t.require(pickUp(t, real) > 0, "The enderman did not pick up a block");
            t.check("off: the held block drops on death", killAndCountBlockDrops(t, real)).isEqualTo(1);
        } finally {
            removeMobs(t);
        }
    }

    private void switching(TestContext t) {
        try {
            grassRing(t);
            int first = enderman(t).id();
            t.require(pickUp(t, first) > 0, "The enderman did not pick up a block");
            List<String> before = area(t);
            // saved and loaded again, as when the chunk unloads or the server restarts
            int loaded = t.onServer(level -> {
                EnderMan original = (EnderMan) level.getEntity(first);
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                original.discard();
                @Nullable EnderMan copy = EntityType.ENDERMAN.create(level);
                if (copy == null) {
                    throw new IllegalStateException("Cannot create an enderman");
                }
                copy.load(saved);
                level.addFreshEntity(copy);
                return copy.getId();
            });
            t.ticks(2);
            t.check("loaded again: still holds the block", holds(t, loaded)).isTrue();
            t.check("loaded again: still held for show", shownOnly(t, loaded)).isTrue();

            MobTweaksFeature.getConfig().endermanBlocksStay = false;
            t.check("turned off later: no drop", killAndCountBlockDrops(t, loaded)).isEqualTo(0);
            t.check("turned off later: the area unchanged", area(t)).isEqualTo(before);
            removeMobs(t);
            grassRing(t);
            int second = enderman(t).id();
            MobTweaksFeature.getConfig().endermanBlocksStay = true;
            t.require(pickUp(t, second) > 0, "The enderman did not pick up a block");
            MobTweaksFeature.getConfig().endermanBlocksStay = false;
            List<String> held = area(t);
            t.check("turned off later: putting down places nothing", putDown(t, second)).atLeast(1);
            t.check("turned off later: the area unchanged", area(t)).isEqualTo(held);
            removeMobs(t);

            grassRing(t);
            int real = enderman(t).id();
            List<String> full = area(t);
            t.require(pickUp(t, real) > 0, "The enderman did not pick up a block");
            MobTweaksFeature.getConfig().endermanBlocksStay = true;
            t.check("a real block, turned on later: still real", shownOnly(t, real)).isFalse();
            t.check("a real block, turned on later: keeps the enderman", persistent(t, real)).isTrue();
            t.check("a real block, turned on later: placed again", putDown(t, real)).atLeast(1);
            t.check("a real block, turned on later: the area full again", area(t).size()).isEqualTo(full.size());
        } finally {
            removeMobs(t);
        }
    }

    private void ownAi(TestContext t) {
        try {
            t.command("time set midnight");
            int x = (int) Math.floor(X);
            int z = (int) Math.floor(Z);
            // a grass hut: walls and roof around the enderman, so it cannot leave and the moon cannot reach it
            t.site().fill(x - 1, 0, z - 1, x + 1, 3, z + 1, "grass_block");
            t.site().fill(x, 0, z, x, 2, z, "air");
            t.teleport(-10.5, 0.0, -10.5, 180.0F, 0.0F);
            List<String> before = area(t);
            t.difficulty(Difficulty.EASY);
            int id = t.spawn(EntityType.ENDERMAN, X, 0.0, Z, 0.0F, null).id();
            t.waitUntil("the enderman picks up a block by itself", () -> holds(t, id), 20 * 60);
            t.check("its own AI: held for show", shownOnly(t, id)).isTrue();
            t.ticks(40);
            t.check("its own AI: the hut is unchanged", area(t)).isEqualTo(before);
        } finally {
            t.command("time set noon");
            removeMobs(t);
        }
    }

    private void blockKinds(TestContext t) {
        try {
            int x = (int) Math.floor(X);
            int z = (int) Math.floor(Z);
            String[] kinds = {"sand", "gravel", "tnt", "pumpkin", "melon", "clay", "dirt", "poppy"};
            int index = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    String kind = kinds[index++];
                    if (kind.equals("poppy")) {
                        t.site().set(x + dx, -1, z + dz, "grass_block");
                    }
                    t.site().set(x + dx, 0, z + dz, kind);
                }
            }
            int id = enderman(t).id();
            List<String> before = area(t);
            List<String> held = new ArrayList<>();
            for (int round = 0; round < 12; round++) {
                t.require(pickUp(t, id) > 0, "The enderman did not pick up a block");
                held.add(onEnderman(t, id, server -> server.getCarriedBlock().getBlock().toString()));
                t.require(putDown(t, id) > 0, "The enderman did not put the block down");
            }
            t.note("blocks it held", held);
            t.check("12 pick-ups of different kinds: every block in place, with its state", area(t)).isEqualTo(before);
            t.check("it held several kinds", held.stream().distinct().count()).atLeast(3);
        } finally {
            removeMobs(t);
        }
    }

    private void mobGriefingOff(TestContext t) {
        try {
            t.command("gamerule mobGriefing false");
            grassRing(t);
            int id = enderman(t).id();
            for (boolean on : new boolean[] {true, false}) {
                MobTweaksFeature.getConfig().endermanBlocksStay = on;
                boolean wants = onEnderman(t, id, server -> {
                    Goal goal = goal(server, PICK_UP_GOAL);
                    for (int step = 0; step < 2000; step++) {
                        if (goal.canUse()) {
                            return true;
                        }
                    }
                    return false;
                });
                t.check("mobGriefing off, option " + (on ? "on" : "off") + ": never wants to pick up", wants)
                    .isFalse();
            }
        } finally {
            t.command("gamerule mobGriefing true");
            removeMobs(t);
        }
    }

    /** Moves the player far away, lets the enderman check whether it despawns, moves the player back */
    private static boolean despawnsWhenFar(TestContext t, int id) {
        return onEnderman(t, id, server -> {
            ServerPlayer player = ((ServerLevel) server.level()).players().getFirst();
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();
            player.teleportTo(x + 300.0, y, z);
            server.checkDespawn();
            player.teleportTo(x, y, z);
            return server.isRemoved();
        });
    }

    private void despawn(TestContext t) {
        try {
            grassRing(t);
            int shown = enderman(t).id();
            t.require(pickUp(t, shown) > 0, "The enderman did not pick up a block");
            long nanosShown = onEnderman(t, shown, server -> {
                long begin = System.nanoTime();
                boolean any = false;
                for (int call = 0; call < 1_000_000; call++) {
                    any |= server.requiresCustomPersistence();
                }
                return any ? -1 : System.nanoTime() - begin;
            });
            t.check("held for show: despawns when the player is far", despawnsWhenFar(t, shown)).isTrue();
            removeMobs(t);

            grassRing(t);
            int named = enderman(t).id();
            t.require(pickUp(t, named) > 0, "The enderman did not pick up a block");
            onEnderman(t, named, server -> {
                server.setPersistenceRequired(); // as a name tag does
                return null;
            });
            t.check("held for show but named: stays", despawnsWhenFar(t, named)).isFalse();
            removeMobs(t);

            MobTweaksFeature.getConfig().endermanBlocksStay = false;
            grassRing(t);
            int real = enderman(t).id();
            t.require(pickUp(t, real) > 0, "The enderman did not pick up a block");
            long nanosReal = onEnderman(t, real, server -> {
                long begin = System.nanoTime();
                boolean all = true;
                for (int call = 0; call < 1_000_000; call++) {
                    all &= server.requiresCustomPersistence();
                }
                return all ? System.nanoTime() - begin : -1;
            });
            t.check("a real block: stays (1.21.1)", despawnsWhenFar(t, real)).isFalse();
            t.note("despawn check, held for show (ns per call)", nanosShown / 1_000_000.0);
            t.note("despawn check, 1.21.1 real block (ns per call)", nanosReal / 1_000_000.0);
        } finally {
            removeMobs(t);
        }
    }

    private void oddStates(TestContext t) {
        try {
            grassRing(t);
            int id = enderman(t).id();
            t.require(pickUp(t, id) > 0, "The enderman did not pick up a block");
            int x = (int) Math.floor(X);
            int z = (int) Math.floor(Z);
            t.site().fill(x - 1, 0, z - 1, x + 1, 0, z + 1, "air"); // a player mines every block it could hold
            List<String> mined = area(t);
            t.check("the blocks broken while held: putting down places nothing", putDown(t, id)).atLeast(1);
            t.check("the blocks broken while held: nothing comes back", area(t)).isEqualTo(mined);
            removeMobs(t);

            grassRing(t);
            int stale = enderman(t).id();
            t.require(pickUp(t, stale) > 0, "The enderman did not pick up a block");
            onEnderman(t, stale, server -> {
                server.setCarriedBlock(null); // as /data modify does: the hands empty, the mark left behind
                return null;
            });
            MobTweaksFeature.getConfig().endermanBlocksStay = false;
            List<String> full = area(t);
            t.require(pickUp(t, stale) > 0, "The enderman did not pick up a block");
            t.check("a mark left by a command: a real pick-up takes the block", area(t).size())
                .isEqualTo(full.size() - 1);
            t.check("a mark left by a command: the real block is not treated as shown", shownOnly(t, stale)).isFalse();
            t.require(putDown(t, stale) > 0, "The enderman did not put the block down");
            t.check("a mark left by a command: the real block is placed (not lost)", area(t).size())
                .isEqualTo(full.size());
            removeMobs(t);

            MobTweaksFeature.getConfig().endermanBlocksStay = true;
            grassRing(t, 0);
            grassRing(t, 8);
            int shownOne = enderman(t, 0).id();
            int realOne = enderman(t, 8).id();
            t.require(pickUp(t, shownOne) > 0, "The first enderman did not pick up a block");
            MobTweaksFeature.getConfig().endermanBlocksStay = false;
            List<String> both = area(t);
            t.require(pickUp(t, realOne) > 0, "The second enderman did not pick up a block");
            t.check("two endermen: each keeps its own state", shownOnly(t, shownOne) && !shownOnly(t, realOne))
                .isTrue();
            t.check("two endermen: only the real one took a block", area(t).size()).isEqualTo(both.size() - 1);
            t.require(putDown(t, shownOne) > 0 && putDown(t, realOne) > 0, "An enderman did not put down");
            t.check("two endermen: the area full again, nothing extra", area(t).size()).isEqualTo(both.size());
            removeMobs(t);

            MobTweaksFeature.getConfig().endermanBlocksStay = true;
            grassRing(t);
            int victim = enderman(t).id();
            t.require(pickUp(t, victim) > 0, "The enderman did not pick up a block");
            onEnderman(t, victim, server -> {
                ServerPlayer player = ((ServerLevel) server.level()).players().getFirst();
                server.hurt(server.damageSources().playerAttack(player), 1000.0F);
                return null;
            });
            t.ticks(5);
            t.check("killed by a player: no block drops", countBlockDrops(t)).isEqualTo(0);
        } finally {
            removeMobs(t);
        }
    }

    private void sculkSensor(TestContext t) {
        try {
            t.teleport(-12.5, 0.0, -12.5, 0.0F, 0.0F);
            int x = (int) Math.floor(X);
            int z = (int) Math.floor(Z);
            t.site().set(x + 3, 0, z, "sculk_sensor");
            for (boolean on : new boolean[] {true, false}) {
                MobTweaksFeature.getConfig().endermanBlocksStay = on;
                grassRing(t);
                int id = enderman(t).id();
                t.ticks(60); // the sensor settles (it may have heard the enderman arrive)
                t.require(pickUp(t, id) > 0, "The enderman did not pick up a block");
                boolean heard = false;
                for (int tick = 0; tick < 40 && !heard; tick++) {
                    t.tick();
                    heard = t.onServer(level -> level.getBlockState(TestSite.ORIGIN.offset(x + 3, 0, z))
                        .getValue(SculkSensorBlock.PHASE) != SculkSensorPhase.INACTIVE);
                }
                t.check(on ? "for show: the sensor hears nothing" : "off: the sensor hears the block taken", heard)
                    .isEqualTo(!on);
                removeMobs(t);
            }
        } finally {
            removeMobs(t);
        }
    }

    /** Returns how many grass block items lie near the enderman's place */
    private static int countBlockDrops(TestContext t) {
        return t.onServer(level -> {
            BlockPos origin = TestSite.ORIGIN;
            AABB box = new AABB(origin.offset(-4, -1, -4).getCenter(), origin.offset(20, 6, 10).getCenter());
            int count = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
                if (item.getItem().is(Items.GRASS_BLOCK)) {
                    count += item.getItem().getCount();
                }
            }
            return count;
        });
    }

    // ---- Nether --------------------------------------------------------------------------------------------------

    /** Where the player stands in the Nether tests (relative to the site), in a hole in the floor */
    private static final int PX = 40;
    private static final int PZ = 5;

    /** Asks the game's spawn rules (as the natural spawner does) with this much block light at the spot */
    private static boolean spawnAllowed(TestContext t, EntityType<?> type, MobSpawnType reason, int light) {
        t.site().set(20, 0, 5, light > 0 ? "light[level=" + light + "]" : "air");
        t.ticks(3); // the light spreads a moment after the block changes
        return t.onServer(level -> SpawnPlacements.checkSpawnRules(type, level, reason,
            TestSite.ORIGIN.offset(20, 0, 5), level.getRandom()));
    }

    private void spawnRule(TestContext t) {
        t.difficulty(Difficulty.EASY);
        List<String> wrong = new ArrayList<>();
        Object[][] cases = {
            {EntityType.PIGLIN, MobSpawnType.NATURAL, 10, true},
            {EntityType.PIGLIN, MobSpawnType.NATURAL, 11, false},
            {EntityType.PIGLIN, MobSpawnType.NATURAL, 15, false},
            {EntityType.PIGLIN, MobSpawnType.NATURAL, 0, true},
            {EntityType.HOGLIN, MobSpawnType.NATURAL, 10, true},
            {EntityType.HOGLIN, MobSpawnType.NATURAL, 11, false},
            {EntityType.PIGLIN, MobSpawnType.CHUNK_GENERATION, 11, false},
            {EntityType.HOGLIN, MobSpawnType.CHUNK_GENERATION, 15, false},
            {EntityType.PIGLIN, MobSpawnType.SPAWNER, 15, true},
            {EntityType.HOGLIN, MobSpawnType.SPAWN_EGG, 15, true},
            {EntityType.PIGLIN, MobSpawnType.STRUCTURE, 15, true},
            {EntityType.HOGLIN, MobSpawnType.BREEDING, 15, true},
            {EntityType.ZOMBIFIED_PIGLIN, MobSpawnType.NATURAL, 15, true},
        };
        for (Object[] testCase : cases) {
            boolean allowed = spawnAllowed(t, (EntityType<?>) testCase[0], (MobSpawnType) testCase[1],
                (int) testCase[2]);
            if (allowed != (boolean) testCase[3]) {
                wrong.add(testCase[0] + " " + testCase[1] + " light " + testCase[2] + ": " + allowed);
            }
        }
        t.check("on: every case as expected (sky light 15 here does not count) " + wrong, wrong.size()).isEqualTo(0);
        t.site().set(20, -1, 5, "nether_wart_block");
        t.check("the game's own rule still refuses (on nether wart blocks, in the dark)",
            spawnAllowed(t, EntityType.PIGLIN, MobSpawnType.NATURAL, 0)).isFalse();
        t.site().set(20, -1, 5, "stone");

        MobTweaksFeature.getConfig().netherSpawnMaxLight = 5;
        t.check("limit 5: light 6 refused", spawnAllowed(t, EntityType.PIGLIN, MobSpawnType.NATURAL, 6)).isFalse();
        t.check("limit 5: light 5 allowed", spawnAllowed(t, EntityType.HOGLIN, MobSpawnType.NATURAL, 5)).isTrue();
        MobTweaksFeature.getConfig().netherSpawnMaxLight = 99;
        t.check("limit edited to 99: counts as 15, every light allowed",
            spawnAllowed(t, EntityType.PIGLIN, MobSpawnType.NATURAL, 15)).isTrue();
        MobTweaksFeature.getConfig().netherSpawnMaxLight = -3;
        t.check("limit edited to -3: counts as 0, light 1 refused",
            spawnAllowed(t, EntityType.PIGLIN, MobSpawnType.NATURAL, 1)).isFalse();
        MobTweaksFeature.getConfig().netherSpawnMaxLight = 10;
        MobTweaksFeature.getConfig().netherSpawnInDark = false;
        t.check("off: allowed in any light", spawnAllowed(t, EntityType.PIGLIN, MobSpawnType.NATURAL, 15)
            && spawnAllowed(t, EntityType.HOGLIN, MobSpawnType.NATURAL, 15)).isTrue();
    }

    /**
     * The player in survival, standing in a hole in the floor (the mobs see and reach the player, but nothing pushes
     * them out of the light), with this much light from blocks at their feet; resistance keeps them unhurt
     */
    private static void arena(TestContext t, int light) {
        setLight(t, light);
        t.teleport(PX + 0.5, -1.0, PZ + 0.5, 90.0F, 0.0F);
        t.gameMode(GameType.SURVIVAL);
        t.withServerPlayer(player -> {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 300, 4, false, false));
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        });
    }

    /** Lets the player be pushed again after a Nether test */
    private static void leaveArena(TestContext t) {
        t.withServerPlayer(player -> player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0));
    }

    /** Sets the light from blocks where the player stands, and lets it spread */
    private static void setLight(TestContext t, int light) {
        t.site().set(PX, -1, PZ, light > 0 ? "light[level=" + light + "]" : "air");
        t.ticks(3);
    }

    /** Spawns a grown piglin, hoglin or brute that never turns into a zombie, as the given reason spawns it */
    private static <T extends Mob> int netherMob(TestContext t, EntityType<T> type, double x, double z,
                                                MobSpawnType reason) {
        t.difficulty(Difficulty.EASY);
        return t.spawn(type, x, 0.0, z, 90.0F, created -> {
            Mob mob = created;
            ServerLevel level = (ServerLevel) mob.level();
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), reason, null);
            mob.setBaby(false);
            mob.setPersistenceRequired();
            if (mob instanceof AbstractPiglin piglin) {
                piglin.setImmuneToZombification(true);
                piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            } else if (mob instanceof Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
        }).id();
    }

    /** Tells whether a mob's target is the player */
    private static boolean targetsPlayer(TestContext t, int id) {
        return t.onServer(level -> level.getEntity(id) instanceof Mob mob
            && (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                ? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) instanceof Player
                : mob.getTarget() instanceof Player));
    }

    /** Ticks until a mob targets the player (or stops, with {@code wanted} false); returns the ticks, or -1 */
    private static int waitTarget(TestContext t, int id, boolean wanted, int maxTicks) {
        for (int tick = 0; tick <= maxTicks; tick++) {
            if (targetsPlayer(t, id) == wanted) {
                return tick;
            }
            t.tick();
        }
        return -1;
    }

    /**
     * Watches mobs for some ticks; returns for each the first and the last tick it targeted the player ({-1, -1} if
     * never). A mob may drop its target for a moment by itself (1.21.1), so the last tick is what tells when it
     * really stopped.
     */
    private static int[][] targetSpans(TestContext t, int ticks, int... ids) {
        int[][] spans = new int[ids.length][];
        for (int index = 0; index < ids.length; index++) {
            spans[index] = new int[] {-1, -1};
        }
        for (int tick = 0; tick < ticks; tick++) {
            for (int index = 0; index < ids.length; index++) {
                if (targetsPlayer(t, ids[index])) {
                    if (spans[index][0] < 0) {
                        spans[index][0] = tick;
                    }
                    spans[index][1] = tick;
                }
            }
            t.tick();
        }
        return spans;
    }

    /** Tells whether a mob targets the player at any tick of a watch */
    private static boolean targetsDuring(TestContext t, int id, int ticks) {
        return waitTarget(t, id, true, ticks) >= 0;
    }

    /** Tells whether a mob's brain turns to fighting at any tick of a watch (it then also makes its angry sound) */
    private static boolean fightsDuring(TestContext t, int id, int ticks) {
        for (int tick = 0; tick <= ticks; tick++) {
            boolean fighting = t.onServer(level -> level.getEntity(id) instanceof Mob mob
                && mob.getBrain().isActive(Activity.FIGHT));
            if (fighting) {
                return true;
            }
            t.tick();
        }
        return false;
    }

    /** The player hits a mob lightly */
    private static void hit(TestContext t, int id) {
        t.onServer(level -> {
            if (level.getEntity(id) instanceof Mob mob) {
                mob.hurt(level.damageSources().playerAttack(level.players().getFirst()), 0.5F);
            }
            return null;
        });
    }

    private void piglinLight(TestContext t) {
        try {
            MobTweaksFeature.getConfig().netherAngerSeconds = 10;
            arena(t, 15);
            int piglin = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.check("the player stands in light 15: never starts attacking (10 s)", targetsDuring(t, piglin, 200))
                .isFalse();
            t.check("not even for a moment: it never turns to fighting (no angry sounds)", fightsDuring(t, piglin, 100))
                .isFalse();
            setLight(t, 0);
            t.check("in the dark: starts attacking", waitTarget(t, piglin, true, 200)).atLeast(0);
            t.ticks(20);
            setLight(t, 15);
            int[] span = targetSpans(t, 320, piglin)[0];
            t.note("back in the light: first and last tick it still attacked (anger 10 s)", List.of(span[0], span[1]));
            t.check("back in the light: keeps chasing until the anger time is over, then stops (10 s after the "
                + "dark)", span[1]).between(170, 230);
            t.check("calm afterwards in the light (5 s)", targetsDuring(t, piglin, 100)).isFalse();
            MobTweaksFeature.getConfig().netherAttackMaxLight = 15;
            t.check("limit 15: attacks even in light 15", waitTarget(t, piglin, true, 200)).atLeast(0);
        } finally {
            leaveArena(t);
            removeMobs(t);
        }
    }

    private void piglinAnger(TestContext t) {
        try {
            MobTweaksFeature.getConfig().netherAngerSeconds = 10;
            arena(t, 15);
            int first = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            int second = netherMob(t, EntityType.PIGLIN, PX + 6.5, PZ + 3.5, MobSpawnType.NATURAL);
            t.require(!targetsDuring(t, first, 40) && !targetsDuring(t, second, 0), "A piglin attacked at once");
            hit(t, first);
            int[][] spans = targetSpans(t, 320, first, second);
            t.note("first and last tick attacking after the hit: the hit piglin, the other (anger 10 s)",
                List.of(spans[0][0], spans[0][1], spans[1][0], spans[1][1]));
            t.check("hit in the light: the piglin attacks at once", spans[0][0]).between(0, 40);
            t.check("its kind joins in", spans[1][0]).between(0, 60);
            t.check("the hit piglin stops when the anger time is over", spans[0][1]).between(170, 230);
            t.check("the other stops when its anger is over too", spans[1][1]).between(150, 230);
            t.check("calm afterwards (5 s)", targetsDuring(t, first, 100) || targetsDuring(t, second, 0)).isFalse();

            // a chest angers only idle piglins that see the player (1.21.1): a fresh one, given time to look around
            removeMobs(t);
            int watcher = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.require(!targetsDuring(t, watcher, 40), "The new piglin attacked at once");
            t.site().set(PX - 1, 0, PZ, "chest[facing=east]");
            t.onServer(level -> {
                BlockPos chest = TestSite.ORIGIN.offset(PX - 1, 0, PZ);
                ServerPlayer player = level.players().getFirst();
                level.getBlockState(chest).useWithoutItem(level, player,
                    new BlockHitResult(chest.getCenter(), Direction.EAST, chest, false));
                return null;
            });
            t.check("a chest opened in the light: the piglin attacks (1.21.1)", waitTarget(t, watcher, true, 60))
                .atLeast(0);
            t.withServerPlayer(player -> player.closeContainer());
        } finally {
            leaveArena(t);
            removeMobs(t);
        }
    }

    private void hoglins(TestContext t) {
        try {
            MobTweaksFeature.getConfig().netherAngerSeconds = 10;
            arena(t, 15);
            int first = netherMob(t, EntityType.HOGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            int second = netherMob(t, EntityType.HOGLIN, PX + 5.5, PZ - 2.5, MobSpawnType.NATURAL);
            int piglin = netherMob(t, EntityType.PIGLIN, PX - 6.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.check("in the light: no hoglin starts (10 s)", targetsDuring(t, first, 200)
                || targetsDuring(t, second, 0)).isFalse();
            t.check("not even for a moment: no hoglin turns to fighting", fightsDuring(t, first, 100)
                || fightsDuring(t, second, 0)).isFalse();
            hit(t, first);
            int[][] spans = targetSpans(t, 320, first, second, piglin);
            t.note("first and last tick attacking after the hit: the hit hoglin, the other (anger 10 s)",
                List.of(spans[0][0], spans[0][1], spans[1][0], spans[1][1]));
            t.check("hit in the light: the hoglin attacks at once", spans[0][0]).between(0, 40);
            t.check("the other hoglin joins in", spans[1][0]).between(0, 60);
            t.check("the piglin never attacks the player", spans[2][0]).isEqualTo(-1);
            t.check("the hit hoglin stops when the anger time is over", spans[0][1]).between(170, 230);
            t.check("calm afterwards (5 s)", targetsDuring(t, first, 100) || targetsDuring(t, second, 0)).isFalse();
            setLight(t, 0);
            t.check("in the dark: attacks on sight", waitTarget(t, first, true, 200)).atLeast(0);
        } finally {
            leaveArena(t);
            removeMobs(t);
        }
    }

    private void unchanged(TestContext t) {
        try {
            arena(t, 15);
            int bastion = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.STRUCTURE);
            t.check("a bastion piglin (placed by a structure) attacks in the light", waitTarget(t, bastion, true, 200))
                .atLeast(0);
            removeMobs(t);
            int brute = netherMob(t, EntityType.PIGLIN_BRUTE, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.check("a piglin brute attacks in the light (1.21.1)", waitTarget(t, brute, true, 200)).atLeast(0);
            removeMobs(t);
            int zombified = netherMob(t, EntityType.ZOMBIFIED_PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            setLight(t, 0);
            t.check("a zombified piglin stays neutral in the dark (1.21.1)", targetsDuring(t, zombified, 100))
                .isFalse();
            removeMobs(t);
            t.withServerPlayer(player -> player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET)));
            int golden = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.check("gold armor keeps a piglin calm in the dark (1.21.1)", targetsDuring(t, golden, 100)).isFalse();
            t.withServerPlayer(player -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
            removeMobs(t);
            setLight(t, 15);
            MobTweaksFeature.getConfig().netherAttackInDark = false;
            int piglin = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            int hoglin = netherMob(t, EntityType.HOGLIN, PX - 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.check("off: a piglin attacks in the light", waitTarget(t, piglin, true, 200)).atLeast(0);
            t.check("off: a hoglin attacks in the light", waitTarget(t, hoglin, true, 200)).atLeast(0);
        } finally {
            leaveArena(t);
            removeMobs(t);
        }
    }


    // ---- the real Nether ------------------------------------------------------------------------------------------

    /** A closed blackstone room in the Nether around (0, 70, 0); the player stands in a hole at its middle */
    private static final int NETHER_Y = 69;

    private static ServerLevel nether(ServerLevel level) {
        return level.getServer().getLevel(Level.NETHER);
    }

    /** Takes the player to the Nether and builds the room: dark inside, no repellent within 19 blocks */
    private static void netherRoom(TestContext t) {
        t.command("gamerule doMobSpawning false");
        t.command("execute in minecraft:the_nether run tp DevTest 0.5 " + (NETHER_Y + 1) + " 0.5");
        t.waitUntil("the Nether room's chunks load", () -> t.onServer(level -> nether(level)
            .isLoaded(new BlockPos(20, NETHER_Y, 20)) && nether(level).isLoaded(new BlockPos(-20, NETHER_Y, -20))),
            200);
        t.command("execute in minecraft:the_nether run fill -20 " + (NETHER_Y - 2) + " -20 20 " + (NETHER_Y + 8)
                + " 20 minecraft:blackstone",
            "execute in minecraft:the_nether run fill -19 " + (NETHER_Y + 1) + " -19 19 " + (NETHER_Y + 7)
                + " 19 minecraft:air");
        clearNetherRoom(t);
        netherLight(t, 0);
        t.withServerPlayer(player -> {
            player.teleportTo(player.serverLevel(), 0.5, NETHER_Y, 0.5, 90.0F, 0.0F);
            player.setGameMode(GameType.SURVIVAL);
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 600, 0, false, false));
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        });
        lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
        t.ticks(5);
    }

    /** Turns the player (in the Nether room's hole) to look at a point, so the test can be watched */
    private static void lookAt(TestContext t, double x, double y, double z) {
        t.withServerPlayer(player -> {
            double dx = x - player.getX();
            double dy = y - player.getEyeY();
            double dz = z - player.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), yaw, pitch);
        });
    }

    /** Removes every entity but players in and around the Nether room (no drops) */
    private static void clearNetherRoom(TestContext t) {
        t.onServer(level -> {
            AABB room = new AABB(-25, NETHER_Y - 5, -25, 25, NETHER_Y + 12, 25);
            nether(level).getEntitiesOfClass(Entity.class, room, entity -> !(entity instanceof Player))
                .forEach(entity -> entity.discard());
            return null;
        });
    }

    /** Sets the light from blocks where the player stands in the Nether room */
    private static void netherLight(TestContext t, int light) {
        t.onServer(level -> nether(level).setBlock(new BlockPos(0, NETHER_Y, 0), light > 0
            ? Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light) : Blocks.AIR.defaultBlockState(), 3));
        t.ticks(3);
    }

    /** Brings the player back to the test site in the Overworld */
    private static void leaveNether(TestContext t) {
        t.withServerPlayer(player -> {
            player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
            player.teleportTo(player.getServer().overworld(), TestSite.ORIGIN.getX() + 0.5,
                TestSite.ORIGIN.getY(), TestSite.ORIGIN.getZ() + 0.5, 0.0F, 0.0F);
        });
        t.command("gamerule doMobSpawning true");
        t.ticks(5);
    }

    /** Spawns a grown hoglin or piglin in the Nether room, as the natural spawner would */
    private static <T extends Mob> int netherRoomMob(TestContext t, EntityType<T> type, double x, double z) {
        return netherRoomMob(t, type, x, z, MobSpawnType.NATURAL, false);
    }

    /** Spawns a hoglin, piglin or zombified piglin in the Nether room for a reason, grown or a baby */
    private static <T extends Mob> int netherRoomMob(TestContext t, EntityType<T> type, double x, double z,
                                                    MobSpawnType reason, boolean baby) {
        return t.onServer(level -> {
            ServerLevel nether = nether(level);
            Mob mob = type.create(nether);
            if (mob == null) {
                throw new IllegalStateException("Cannot create " + type);
            }
            mob.moveTo(x, NETHER_Y + 1, z, 270.0F, 0.0F);
            mob.finalizeSpawn(nether, nether.getCurrentDifficultyAt(mob.blockPosition()), reason, null);
            mob.setBaby(baby);
            mob.setPersistenceRequired();
            if (mob instanceof AbstractPiglin piglin) {
                piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
            }
            nether.addFreshEntity(mob);
            return mob.getId();
        });
    }

    /**
     * What a Nether room mob does this tick: {targets the player, fighting, avoiding, passive (hoglin), exists, knows a
     * repellent}
     */
    private static boolean[] state(TestContext t, int id) {
        return t.onServer(level -> {
            if (!(nether(level).getEntity(id) instanceof Mob mob) || mob.isRemoved()) {
                return new boolean[STATES];
            }
            return new boolean[] {
                (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    ? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : mob.getTarget())
                    instanceof Player,
                mob.getBrain().isActive(Activity.FIGHT),
                mob.getBrain().isActive(Activity.AVOID),
                mob.getBrain().hasMemoryValue(MemoryModuleType.PACIFIED),
                true,
                mob.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT)
            };
        });
    }

    /** Number of values {@link #state} returns */
    private static final int STATES = 6;

    /** Counts, over some ticks, the ticks of each value of {@link #state} */
    private static int[] watch(TestContext t, int id, int ticks) {
        int[] counts = new int[STATES];
        for (int tick = 0; tick < ticks; tick++) {
            boolean[] now = state(t, id);
            for (int index = 0; index < STATES; index++) {
                counts[index] += now[index] ? 1 : 0;
            }
            t.tick();
        }
        return counts;
    }

    /**
     * One trial: a fresh mob 5 blocks away is watched for 6 s, then hit by the player and watched for 10 s; returns
     * {ticks targeting before the hit, ticks targeting after it, ticks avoiding, ticks passive}
     */
    private static int[] trial(TestContext t, EntityType<? extends Mob> type) {
        int id = netherRoomMob(t, type, 5.5, 0.5);
        int[] before = watch(t, id, 120);
        t.onServer(level -> {
            if (nether(level).getEntity(id) instanceof Mob mob) {
                mob.hurt(nether(level).damageSources().playerAttack(level.getServer().getPlayerList().getPlayers()
                    .getFirst()), 0.5F);
            }
            return null;
        });
        int[] after = watch(t, id, 200);
        clearNetherRoom(t);
        t.ticks(2);
        return new int[] {before[0], after[0], before[2] + after[2], before[3] + after[3], after[4], after[5],
            before[5]};
    }

    private void netherWorld(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            String[] names = {"hoglin", "piglin"};
            List<EntityType<? extends Mob>> types = List.of(EntityType.HOGLIN, EntityType.PIGLIN);
            for (int kind = 0; kind < 2; kind++) {
                for (int light : new int[] {0, 15}) {
                    netherLight(t, light);
                    for (boolean on : new boolean[] {false, true}) {
                        MobTweaksFeature.getConfig().netherAttackInDark = on;
                        int[] sum = new int[4];
                        for (int round = 0; round < 2; round++) {
                            int[] result = trial(t, types.get(kind));
                            for (int index = 0; index < 4; index++) {
                                sum[index] += result[index];
                            }
                        }
                        String name = names[kind] + ", " + (light == 0 ? "dark" : "light 15") + ", option "
                            + (on ? "on" : "off");
                        t.note(name + ": ticks attacking before the hit (of 240), after it (of 400), avoiding, "
                            + "passive", List.of(sum[0], sum[1], sum[2], sum[3]));
                        boolean calmBefore = on && light > 10;
                        t.check(name + ": attacks on sight " + (calmBefore ? "never" : "yes"), sum[0] > 0)
                            .isEqualTo(!calmBefore);
                        t.check(name + ": attacks back after the hit, nearly all of the 10 s", sum[1]).atLeast(320);
                        t.check(name + ": never flees or goes passive (no repellent near)", sum[2] + sum[3])
                            .isEqualTo(0);
                    }
                }
            }

            // 1.21.1's own fears, the same with the option on and off
            netherLight(t, 0);
            // between the player and the hoglin: every place of the fight is well within its 8 blocks of fear
            t.command("execute in minecraft:the_nether run setblock 3 " + NETHER_Y + " 3 minecraft:warped_nylium",
                "execute in minecraft:the_nether run setblock 3 " + (NETHER_Y + 1) + " 3 minecraft:warped_fungus");
            for (boolean on : new boolean[] {false, true, false, true}) {
                MobTweaksFeature.getConfig().netherAttackInDark = on;
                int[] near = trial(t, EntityType.HOGLIN);
                t.note("hoglin next to a warped fungus, option " + (on ? "on" : "off") + ": ticks attacking before "
                    + "the hit (of 120), after it (of 200), avoiding, passive; after the hit: exists, knows the fungus;"
                    + " before: knows the fungus", List.of(near[0], near[1], near[2], near[3], near[4], near[5],
                    near[6]));
                // how long it stays passive or fights back is random in 1.21.1 (it walks away from the fungus and
                // may lose it); what never fails is that it fears the fungus
                t.check("hoglin next to a warped fungus, option " + (on ? "on" : "off") + ": goes passive (it fears "
                    + "the fungus, as in 1.21.1)", near[3]).atLeast(20);
            }
            t.command("execute in minecraft:the_nether run setblock 3 " + (NETHER_Y + 1) + " 3 minecraft:air");

            MobTweaksFeature.getConfig().netherAttackInDark = false;
            MobTweaksFeature.getConfig().netherSpawnInDark = false;
            int hoglin = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            int piglin = netherRoomMob(t, EntityType.PIGLIN, -5.5, 0.5);
            int zombified = netherRoomMob(t, EntityType.ZOMBIFIED_PIGLIN, 0.5, 5.5);
            t.difficulty(Difficulty.PEACEFUL);
            t.ticks(40);
            boolean[] alive = t.onServer(level -> new boolean[] {nether(level).getEntity(hoglin) != null,
                nether(level).getEntity(piglin) != null, nether(level).getEntity(zombified) != null});
            t.check("Peaceful, both options off: hoglins and piglins stay, zombified piglins go (1.21.1)",
                alive[0] && alive[1] && !alive[2]).isTrue();
            t.onServer(level -> {
                if (nether(level).getEntity(hoglin) instanceof Mob mob) {
                    mob.hurt(nether(level).damageSources().playerAttack(level.getServer().getPlayerList()
                        .getPlayers().getFirst()), 0.5F);
                }
                return null;
            });
            t.check("Peaceful, both options off: a hit hoglin cannot attack back (1.21.1 refuses targets)",
                watch(t, hoglin, 60)[0]).isEqualTo(0);
        } finally {
            t.difficulty(Difficulty.PEACEFUL);
            clearNetherRoom(t);
            leaveNether(t);
        }
    }


    /** Watches Nether room mobs; returns for each the first and last tick it targeted the player ({-1, -1}: never) */
    private static int[][] netherSpans(TestContext t, int ticks, int... ids) {
        int[][] spans = new int[ids.length][];
        for (int index = 0; index < ids.length; index++) {
            spans[index] = new int[] {-1, -1};
        }
        for (int tick = 0; tick < ticks; tick++) {
            for (int index = 0; index < ids.length; index++) {
                if (state(t, ids[index])[0]) {
                    if (spans[index][0] < 0) {
                        spans[index][0] = tick;
                    }
                    spans[index][1] = tick;
                }
            }
            t.tick();
        }
        return spans;
    }

    /** The player hits a Nether room mob: by hand, or with an arrow */
    private static void netherHit(TestContext t, int id, boolean arrow) {
        t.onServer(level -> {
            ServerLevel nether = nether(level);
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
            if (nether.getEntity(id) instanceof Mob mob) {
                mob.hurt(arrow ? nether.damageSources().arrow(new Arrow(nether, player, new ItemStack(Items.ARROW),
                    null), player) : nether.damageSources().playerAttack(player), 0.5F);
            }
            return null;
        });
    }

    /** Sets a block in the Nether */
    private static void netherBlock(TestContext t, int x, int y, int z, BlockState state) {
        t.onServer(level -> nether(level).setBlock(new BlockPos(x, y, z), state, 3));
    }

    private void netherGroups(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            netherLight(t, 12);
            // a wall hides hoglin c from hoglin a, but both see the player
            for (int x = 2; x <= 4; x++) {
                for (int z = 3; z <= 4; z++) {
                    for (int y = NETHER_Y + 1; y <= NETHER_Y + 3; y++) {
                        netherBlock(t, x, y, z, Blocks.BLACKSTONE.defaultBlockState());
                    }
                }
            }
            int a = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            int b = netherRoomMob(t, EntityType.HOGLIN, 5.5, -3.5);
            int c = netherRoomMob(t, EntityType.HOGLIN, 0.5, 6.5);
            lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
            int[][] calm = netherSpans(t, 60, a, b, c);
            t.check("light 12 at the player (limit 10): no hoglin attacks first", calm[0][0] + calm[1][0] + calm[2][0])
                .isEqualTo(-3);
            boolean seesC = t.onServer(level -> nether(level).getEntity(a) instanceof Hoglin hoglin && hoglin
                .getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS).orElse(List.of()).stream()
                .anyMatch(other -> other.getId() == c));
            t.note("hoglin a sees hoglin c behind the wall (1.21.1 would call only those it sees)", seesC);
            t.saveScreenshot("nether_hoglins_before_hit");
            netherHit(t, a, false);
            int[] attacking = new int[3];
            int hiddenFirst = -1;
            for (int tick = 0; tick < 160; tick++) {
                attacking[0] += state(t, a)[0] ? 1 : 0;
                attacking[1] += state(t, b)[0] ? 1 : 0;
                boolean hidden = state(t, c)[0];
                attacking[2] += hidden ? 1 : 0;
                hiddenFirst = hidden && hiddenFirst < 0 ? tick : hiddenFirst;
                t.tick();
            }
            t.saveScreenshot("nether_hoglins_after_hit");
            t.note("ticks attacking in the 8 s after the hit: a, b, c (hidden from a)", List.of(attacking[0],
                attacking[1], attacking[2]));
            t.check("the hit hoglin attacks, nearly all the time", attacking[0]).atLeast(130);
            t.check("the hoglin it sees joins in for good", attacking[1]).atLeast(100);
            t.check("the hoglin hidden from it by the wall joins in for good too (all within 16 blocks)",
                attacking[2]).atLeast(80);
            t.check("and at once, not only when another hoglin calls it after a hit", hiddenFirst).between(0, 20);
            clearNetherRoom(t);
            for (int x = 2; x <= 4; x++) {
                for (int z = 3; z <= 4; z++) {
                    for (int y = NETHER_Y + 1; y <= NETHER_Y + 3; y++) {
                        netherBlock(t, x, y, z, Blocks.AIR.defaultBlockState());
                    }
                }
            }

            // a hoglin 25 blocks from the hit one (beyond 16), called later by one that fights beside the player
            int hitFar = netherRoomMob(t, EntityType.HOGLIN, 12.5, 0.5);
            int middle = netherRoomMob(t, EntityType.HOGLIN, 0.5, 6.5);
            int far = netherRoomMob(t, EntityType.HOGLIN, -12.5, 0.5);
            lookAt(t, 0.5, NETHER_Y + 1.5, 6.5);
            t.require(netherSpans(t, 40, hitFar, middle, far)[2][0] < 0, "The far hoglin attacked first");
            netherHit(t, hitFar, false);
            int farTicks = 0;
            for (int tick = 0; tick < 240; tick++) {
                farTicks += state(t, far)[0] ? 1 : 0;
                t.tick();
            }
            t.note("ticks the far hoglin attacked in the 12 s after the hit", farTicks);
            t.check("a hoglin beyond 16 blocks joins for good once a fighting hoglin calls it (its anger is shared, "
                + "no flicker)", farTicks).atLeast(60);
            clearNetherRoom(t);

            int adult = netherRoomMob(t, EntityType.HOGLIN, 6.5, 0.5);
            int babyHoglin = netherRoomMob(t, EntityType.HOGLIN, 4.5, -1.5, MobSpawnType.NATURAL, true);
            lookAt(t, 4.5, NETHER_Y + 1.0, -1.5);
            t.require(netherSpans(t, 40, adult)[0][0] < 0, "The hoglin attacked first");
            netherHit(t, babyHoglin, false);
            t.check("a baby hoglin hit: the grown one nearby attacks", netherSpans(t, 100, adult)[0][0])
                .between(0, 100);
            clearNetherRoom(t);

            int shot = netherRoomMob(t, EntityType.HOGLIN, 6.5, 0.5);
            int friend = netherRoomMob(t, EntityType.HOGLIN, 6.5, -2.5);
            lookAt(t, 6.5, NETHER_Y + 1.5, 0.5);
            t.require(netherSpans(t, 40, shot, friend)[0][0] < 0, "A hoglin attacked first");
            netherHit(t, shot, true);
            int[][] arrowed = netherSpans(t, 100, shot, friend);
            t.check("an arrow from the light: the shot hoglin and its friend attack", arrowed[0][0] >= 0
                && arrowed[1][0] >= 0).isTrue();
            clearNetherRoom(t);

            MobTweaksFeature.getConfig().netherAttackInDark = false;
            int vanilla = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            t.check("option off: attacks on sight in light 12", netherSpans(t, 100, vanilla)[0][0]).between(0, 100);
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    /** Counts the items lying in the Nether room that are not gold ingots (a piglin's barter) */
    private static int barterItems(TestContext t) {
        return t.onServer(level -> nether(level).getEntitiesOfClass(ItemEntity.class,
            new AABB(-20, NETHER_Y - 2, -20, 20, NETHER_Y + 10, 20), item -> !item.getItem().is(Items.GOLD_INGOT))
            .size());
    }

    /** Waits until a piglin barters (non-gold items appear); returns the ticks, or -1 */
    private static int waitBarter(TestContext t, int maxTicks) {
        for (int tick = 0; tick <= maxTicks; tick += 5) {
            if (barterItems(t) > 0) {
                return tick;
            }
            t.ticks(5);
        }
        return -1;
    }

    /** Throws a gold ingot at the feet of a Nether room mob */
    private static void throwGold(TestContext t, int id) {
        t.onServer(level -> {
            ServerLevel nether = nether(level);
            if (nether.getEntity(id) instanceof Mob mob) {
                ItemEntity gold = new ItemEntity(nether, mob.getX(), mob.getY() + 0.5, mob.getZ(),
                    new ItemStack(Items.GOLD_INGOT));
                gold.setPickUpDelay(0);
                nether.addFreshEntity(gold);
            }
            return null;
        });
    }

    /** Hands a gold ingot to a Nether room piglin (a right click with gold); returns whether it took it */
    private static boolean handGold(TestContext t, int id) {
        return t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_INGOT));
            boolean took = nether(level).getEntity(id) instanceof Mob mob
                && mob.interact(player, InteractionHand.MAIN_HAND).consumesAction();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return took;
        });
    }

    private static void clearItems(TestContext t) {
        t.onServer(level -> {
            nether(level).getEntitiesOfClass(ItemEntity.class, new AABB(-25, NETHER_Y - 5, -25, 25, NETHER_Y + 12,
                25)).forEach(item -> item.discard());
            return null;
        });
    }

    private void netherBarter(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            // {light at the player, option on, gold helmet}
            Object[][] cases = {{15, true, false}, {0, true, true}, {15, false, true}};
            for (Object[] testCase : cases) {
                int light = (int) testCase[0];
                boolean on = (boolean) testCase[1];
                boolean golden = (boolean) testCase[2];
                String name = (light == 0 ? "dark" : "light 15") + ", option " + (on ? "on" : "off")
                    + (golden ? ", gold helmet" : ", no gold");
                MobTweaksFeature.getConfig().netherAttackInDark = on;
                netherLight(t, light);
                t.withServerPlayer(player -> player.setItemSlot(EquipmentSlot.HEAD,
                    golden ? new ItemStack(Items.GOLDEN_HELMET) : ItemStack.EMPTY));
                int piglin = netherRoomMob(t, EntityType.PIGLIN, 4.5, 0.5);
                lookAt(t, 4.5, NETHER_Y + 1.5, 0.5);
                t.ticks(20);
                throwGold(t, piglin);
                int thrown = waitBarter(t, 400);
                t.saveScreenshot("nether_barter_" + (light == 0 ? "dark" : "light") + (on ? "_on" : "_off"));
                t.check(name + ": gold thrown on the ground: the piglin barters", thrown).atLeast(0);
                t.check(name + ": it never attacked", netherSpans(t, 1, piglin)[0][0]).isEqualTo(-1);
                clearItems(t);
                t.ticks(20);
                t.check(name + ": gold handed over: it takes it", handGold(t, piglin)).isTrue();
                t.check(name + ": and barters", waitBarter(t, 400)).atLeast(0);
                clearItems(t);
                clearNetherRoom(t);
            }
            t.withServerPlayer(player -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));

            MobTweaksFeature.getConfig().netherAttackInDark = true;
            netherLight(t, 15);
            int hit = netherRoomMob(t, EntityType.PIGLIN, 4.5, 0.5);
            t.ticks(20);
            netherHit(t, hit, false);
            t.ticks(10);
            t.check("a piglin hit in the light refuses gold for a while (1.21.1)", handGold(t, hit)).isFalse();
            clearNetherRoom(t);

            int guard = netherRoomMob(t, EntityType.PIGLIN, 4.5, 0.5);
            netherBlock(t, -2, NETHER_Y + 1, 0, Blocks.GOLD_BLOCK.defaultBlockState());
            lookAt(t, -2.0, NETHER_Y + 1.5, 0.5);
            t.ticks(20);
            t.onServer(level -> level.getServer().getPlayerList().getPlayers().getFirst().gameMode
                .destroyBlock(new BlockPos(-2, NETHER_Y + 1, 0)));
            t.check("a gold block mined in the light: the piglin attacks (1.21.1)", netherSpans(t, 60, guard)[0][0])
                .between(0, 60);
        } finally {
            t.withServerPlayer(player -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
            clearItems(t);
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    private void netherRails(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            // a rail on the floor (as a track is built), glowstone beside it
            netherBlock(t, 0, NETHER_Y, 0, Blocks.BLACKSTONE.defaultBlockState());
            netherBlock(t, 0, NETHER_Y + 1, 0, Blocks.RAIL.defaultBlockState());
            netherBlock(t, 0, NETHER_Y + 1, 1, Blocks.GLOWSTONE.defaultBlockState());
            int cart = t.onServer(level -> {
                ServerLevel nether = nether(level);
                Minecart minecart = new Minecart(nether, 0.5, NETHER_Y + 1, 0.5);
                nether.addFreshEntity(minecart);
                level.getServer().getPlayerList().getPlayers().getFirst().startRiding(minecart, true);
                return minecart.getId();
            });
            t.ticks(5);
            String where = t.onServer(level -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
                ServerLevel nether = nether(level);
                return player.level().dimension().location() + " at " + player.position() + " block "
                    + player.blockPosition() + " riding " + player.isPassenger() + ", light there "
                    + nether.getBrightness(LightLayer.BLOCK, player.blockPosition()) + ", at the eyes "
                    + nether.getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getX(), player.getEyeY(),
                    player.getZ())) + ", at the rail " + nether.getBrightness(LightLayer.BLOCK, new BlockPos(0,
                    NETHER_Y + 1, 0));
            });
            t.note("where the player sits in the minecart", where);
            int hoglin = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            int piglin = netherRoomMob(t, EntityType.PIGLIN, 5.5, -2.5);
            lookAt(t, 5.5, NETHER_Y + 1.5, -1.0);
            int[][] lit = netherSpans(t, 120, hoglin, piglin);
            t.saveScreenshot("nether_minecart_lit");
            t.check("in a minecart on a rail lit by glowstone: neither attacks", lit[0][0] + lit[1][0]).isEqualTo(-2);
            netherBlock(t, 0, NETHER_Y + 1, 1, Blocks.AIR.defaultBlockState());
            t.ticks(3);
            int darkLight = t.onServer(level -> {
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
                return Math.max(nether(level).getBrightness(LightLayer.BLOCK, player.blockPosition()),
                    nether(level).getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getX(),
                        player.getEyeY(), player.getZ())));
            });
            int[][] dark = netherSpans(t, 200, hoglin, piglin);
            t.note("the rail dark: light at the player, first tick attacking: hoglin, piglin", List.of(darkLight,
                dark[0][0], dark[1][0]));
            MobTweaksFeature.getConfig().netherAttackInDark = false;
            int[][] vanilla = netherSpans(t, 200, hoglin, piglin);
            MobTweaksFeature.getConfig().netherAttackInDark = true;
            String seen = t.onServer(level -> {
                StringBuilder text = new StringBuilder();
                for (int id : new int[] {hoglin, piglin}) {
                    if (nether(level).getEntity(id) instanceof Mob mob) {
                        Player player = level.getServer().getPlayerList().getPlayers().getFirst();
                        boolean knows = mob.getBrain().hasMemoryValue(
                                MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER)
                            || mob.getBrain().hasMemoryValue(
                                MemoryModuleType.NEAREST_TARGETABLE_PLAYER_NOT_WEARING_GOLD);
                        text.append(mob.getType().toShortString()).append(" at ")
                            .append(mob.blockPosition().toShortString()).append(" sees the player ")
                            .append(mob.hasLineOfSight(player)).append(", knows them ").append(knows).append("; ");
                    }
                }
                return text.toString();
            });
            t.note("the rail dark, option off: first tick attacking: hoglin, piglin; what they see", List.of(
                vanilla[0][0], vanilla[1][0], seen));
            t.check("the rail dark (night vision on): both attack", dark[0][0] >= 0 && dark[1][0] >= 0).isTrue();
            t.check("the rail dark, option off: both attack too (1.21.1)", vanilla[0][0] >= 0 && vanilla[1][0] >= 0
                || dark[0][0] >= 0 && dark[1][0] >= 0).isTrue();
            t.onServer(level -> {
                level.getServer().getPlayerList().getPlayers().getFirst().stopRiding();
                if (nether(level).getEntity(cart) != null) {
                    nether(level).getEntity(cart).discard();
                }
                return null;
            });
            clearNetherRoom(t);
            netherBlock(t, 0, NETHER_Y + 1, 0, Blocks.AIR.defaultBlockState());
            netherBlock(t, 0, NETHER_Y, 0, Blocks.AIR.defaultBlockState());
            t.withServerPlayer(player -> player.teleportTo(player.serverLevel(), 0.5, NETHER_Y, 0.5, 0.0F, 0.0F));

            for (int level : new int[] {10, 11}) {
                netherLight(t, level);
                int at = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
                lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
                boolean attacked = netherSpans(t, 120, at)[0][0] >= 0;
                t.check("light exactly " + level + " at the player (limit 10): " + (level <= 10 ? "attacks" : "calm"),
                    attacked).isEqualTo(level <= 10);
                clearNetherRoom(t);
            }
        } finally {
            t.onServer(level -> {
                level.getServer().getPlayerList().getPlayers().getFirst().stopRiding();
                return null;
            });
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    /** Counts piglins and hoglins in a box of the Nether */
    private static int piglinsAndHoglins(TestContext t, AABB box) {
        return t.onServer(level -> nether(level).getEntitiesOfClass(Mob.class, box,
            mob -> mob.getType() == EntityType.PIGLIN || mob.getType() == EntityType.HOGLIN).size());
    }

    /** Runs the game's natural spawner many times on random spots of a box; returns the piglins and hoglins there */
    private static int naturalSpawns(TestContext t, int x1, int z1, int attempts) {
        AABB box = new AABB(x1, NETHER_Y, z1, x1 + 16, NETHER_Y + 8, z1 + 16);
        t.onServer(level -> {
            ServerLevel nether = nether(level);
            for (int attempt = 0; attempt < attempts; attempt++) {
                BlockPos pos = new BlockPos(x1 + 2 + nether.getRandom().nextInt(13), NETHER_Y,
                    z1 + 2 + nether.getRandom().nextInt(13));
                NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, nether, pos);
            }
            return null;
        });
        int count = piglinsAndHoglins(t, box);
        t.onServer(level -> {
            nether(level).getEntitiesOfClass(Mob.class, box).forEach(mob -> mob.discard());
            return null;
        });
        return count;
    }

    private void netherSpawner(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            // two closed crimson forest rooms, 30+ blocks from the player (the spawner keeps 24 away): lit, dark
            for (int x1 : new int[] {30, -46}) {
                String box = x1 + " " + (NETHER_Y - 1) + " -8 " + (x1 + 16) + " " + (NETHER_Y + 9) + " 8";
                String inside = (x1 + 1) + " " + NETHER_Y + " -7 " + (x1 + 15) + " " + (NETHER_Y + 8) + " 7";
                t.waitUntil("the spawner room's chunks load", () -> t.onServer(level -> nether(level).isLoaded(
                    new BlockPos(x1, NETHER_Y, -8)) && nether(level).isLoaded(new BlockPos(x1 + 16, NETHER_Y, 8))),
                    200);
                t.command("execute in minecraft:the_nether run fill " + box + " minecraft:blackstone",
                    "execute in minecraft:the_nether run fill " + inside + " minecraft:air",
                    "execute in minecraft:the_nether run fillbiome " + box + " minecraft:crimson_forest");
                if (x1 < 0) {
                    // glowstone in a checkerboard: mobs never spawn on a block giving light 14 or more (1.21.1), so
                    // they can stand only on the blackstone between, lit 13 by the glowstone beside it
                    for (int x = x1 + 1; x <= x1 + 15; x++) {
                        for (int z = -7; z <= 7; z++) {
                            if (((x + z) & 1) == 0) {
                                netherBlock(t, x, NETHER_Y - 1, z, Blocks.GLOWSTONE.defaultBlockState());
                            }
                        }
                    }
                }
            }
            t.ticks(10);
            int lightLit = t.onServer(level -> {
                int darkest = 15;
                for (int x = -45; x <= -31; x++) {
                    for (int z = -7; z <= 7; z++) {
                        darkest = Math.min(darkest, nether(level).getBrightness(LightLayer.BLOCK,
                            new BlockPos(x, NETHER_Y, z)));
                    }
                }
                return darkest;
            });
            int lightDark = t.onServer(level -> nether(level).getBrightness(LightLayer.BLOCK,
                new BlockPos(38, NETHER_Y, 0)));
            t.note("light from blocks where mobs would stand: the darkest spot of the lit room, the dark room",
                List.of(lightLit, lightDark));
            t.check("the lit room is lit above the limit everywhere", lightLit).atLeast(11);
            int dark = naturalSpawns(t, 30, -8, 400);
            int lit = naturalSpawns(t, -46, -8, 400);
            t.note("piglins and hoglins the natural spawner made (400 tries each): dark room, lit room", List.of(dark,
                lit));
            t.check("on: piglins and hoglins spawn on dark ground", dark).atLeast(1);
            t.check("on: none on lit ground", lit).isEqualTo(0);
            MobTweaksFeature.getConfig().netherSpawnInDark = false;
            int litOff = naturalSpawns(t, -46, -8, 400);
            t.note("option off: piglins and hoglins on lit ground (400 tries)", litOff);
            t.check("off: they spawn on lit ground too (1.21.1)", litOff).atLeast(1);
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    private void netherExtremes(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            netherLight(t, 15);
            for (int seconds : new int[] {5, 120}) {
                MobTweaksFeature.getConfig().netherAngerSeconds = seconds;
                int hoglin = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
                lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
                t.ticks(20);
                netherHit(t, hoglin, false);
                int[] span = netherSpans(t, seconds == 5 ? 200 : 600, hoglin)[0];
                t.note("anger " + seconds + " s: first and last tick attacking after the hit", List.of(span[0],
                    span[1]));
                if (seconds == 5) {
                    t.check("anger 5 s: stops after about 5 s", span[1]).between(80, 130);
                } else {
                    t.check("anger 120 s: still attacking after 30 s", span[1]).atLeast(590);
                }
                clearNetherRoom(t);
            }

            int bastion = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5, MobSpawnType.STRUCTURE, false);
            int copy = t.onServer(level -> {
                ServerLevel nether = nether(level);
                Mob original = (Mob) nether.getEntity(bastion);
                CompoundTag saved = original.saveWithoutId(new CompoundTag());
                original.discard();
                Mob loaded = EntityType.HOGLIN.create(nether);
                if (loaded == null) {
                    throw new IllegalStateException("Cannot create a hoglin");
                }
                loaded.load(saved);
                nether.addFreshEntity(loaded);
                return loaded.getId();
            });
            lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
            t.check("a bastion hoglin saved and loaded again: still attacks in the light",
                netherSpans(t, 120, copy)[0][0]).between(0, 120);
            clearNetherRoom(t);

            int first = netherRoomMob(t, EntityType.ZOMBIFIED_PIGLIN, 4.5, 0.5);
            int second = netherRoomMob(t, EntityType.ZOMBIFIED_PIGLIN, 5.5, -2.5);
            lookAt(t, 4.5, NETHER_Y + 1.5, 0.5);
            t.check("zombified piglins in the light: calm at first (1.21.1)", netherSpans(t, 60, first, second)[0][0])
                .isEqualTo(-1);
            netherHit(t, first, false);
            int[][] angry = netherSpans(t, 100, first, second);
            t.check("hit one in the light: both attack (1.21.1)", angry[0][0] >= 0 && angry[1][0] >= 0).isTrue();
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }


    private void netherTwoPlayers(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            netherLight(t, 15);
            int hoglin = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
            t.ticks(20);
            boolean otherFirst = t.onServer(level -> {
                ServerLevel nether = nether(level);
                Mob mob = (Mob) nether.getEntity(hoglin);
                FakePlayer other = FakePlayer.get(nether);
                mob.hurt(nether.damageSources().playerAttack(other), 0.5F);
                return MobTweaksInternals.isAngryAt(mob, other);
            });
            t.ticks(15); // after a hit a mob ignores the next one for half a second (1.21.1)
            boolean[] angry = t.onServer(level -> {
                ServerLevel nether = nether(level);
                Mob mob = (Mob) nether.getEntity(hoglin);
                ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
                mob.hurt(nether.damageSources().playerAttack(player), 0.5F);
                return new boolean[] {otherFirst, MobTweaksInternals.isAngryAt(mob, FakePlayer.get(nether)),
                    MobTweaksInternals.isAngryAt(mob, player)};
            });
            t.check("the other player hits it: angry at them", angry[0]).isTrue();
            t.check("then this player hits it: still angry at the other player", angry[1]).isTrue();
            t.check("and angry at this player too", angry[2]).isTrue();
            t.check("it attacks this player", netherSpans(t, 60, hoglin)[0][0]).between(0, 60);
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    private static boolean hasNetherData(TestContext t, int id) {
        return t.onServer(level -> nether(level).getEntity(id) instanceof Mob mob
            && MobTweaksInternals.hasNetherData(mob));
    }

    private void netherNoTrace(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            netherLight(t, 15);
            MobTweaksFeature.getConfig().netherSpawnInDark = false;
            MobTweaksFeature.getConfig().netherAttackInDark = false;
            int hoglin = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            int piglin = netherRoomMob(t, EntityType.PIGLIN, 5.5, -3.5);
            int bastion = netherRoomMob(t, EntityType.PIGLIN, 5.5, 3.5, MobSpawnType.STRUCTURE, false);
            lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
            netherHit(t, hoglin, false);
            netherHit(t, piglin, false);
            t.ticks(100);
            t.check("both options off: nothing noted or saved on the mobs (as without the feature)",
                hasNetherData(t, hoglin) || hasNetherData(t, piglin) || hasNetherData(t, bastion)).isFalse();
            String saved = t.onServer(level -> ((Mob) nether(level).getEntity(hoglin)).saveWithoutId(
                new CompoundTag()).toString());
            t.check("both options off: the hoglin's save holds nothing of the mod", saved.contains("firstone"))
                .isFalse();
            clearNetherRoom(t);

            int chaser = netherRoomMob(t, EntityType.HOGLIN, 5.5, 0.5);
            t.check("option off: attacks on sight in the light", netherSpans(t, 100, chaser)[0][0]).between(0, 100);
            MobTweaksFeature.getConfig().netherAttackInDark = true;
            int[] span = netherSpans(t, 100, chaser)[0];
            t.check("switched on during the chase (the player in the light, no anger): it stops at once",
                span[1]).atMost(5);
            MobTweaksFeature.getConfig().netherAttackInDark = false;
            t.check("switched off again: attacks on sight", netherSpans(t, 100, chaser)[0][0]).between(0, 100);
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    private void netherOtherMobs(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            List<EntityType<?>> others = List.of(EntityType.ZOMBIFIED_PIGLIN, EntityType.GHAST, EntityType.MAGMA_CUBE,
                EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.SKELETON, EntityType.ENDERMAN,
                EntityType.STRIDER, EntityType.PIGLIN_BRUTE, EntityType.ZOGLIN);
            List<String> differ = new ArrayList<>();
            for (EntityType<?> type : others) {
                for (int light : new int[] {0, 15}) {
                    for (MobSpawnType reason : new MobSpawnType[] {MobSpawnType.NATURAL, MobSpawnType.SPAWNER}) {
                        boolean[] results = new boolean[2];
                        for (int index = 0; index < 2; index++) {
                            MobTweaksFeature.getConfig().netherSpawnInDark = index == 1;
                            netherBlock(t, 8, NETHER_Y + 1, 8, light > 0 ? Blocks.LIGHT.defaultBlockState()
                                .setValue(LightBlock.LEVEL, light) : Blocks.AIR.defaultBlockState());
                            t.ticks(3);
                            results[index] = t.onServer(level -> SpawnPlacements.checkSpawnRules(type, nether(level),
                                reason, new BlockPos(8, NETHER_Y + 1, 8), nether(level).getRandom()));
                        }
                        if (results[0] != results[1]) {
                            differ.add(type.toShortString() + " " + reason + " light " + light);
                        }
                    }
                }
            }
            t.check("every other Nether mob's spawn rules: the same with the option on and off " + differ,
                differ.size()).isEqualTo(0);
            netherBlock(t, 8, NETHER_Y + 1, 8, Blocks.AIR.defaultBlockState());

            netherLight(t, 15);
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOGLIN, EntityType.PIGLIN_BRUTE)) {
                int mob = netherRoomMob(t, type, 5.5, 0.5);
                lookAt(t, 5.5, NETHER_Y + 1.5, 0.5);
                t.check(type.toShortString() + " in the light: attacks on sight (1.21.1)",
                    netherSpans(t, 120, mob)[0][0]).between(0, 120);
                t.check(type.toShortString() + ": nothing of the mod noted on it", hasNetherData(t, mob)).isFalse();
                clearNetherRoom(t);
            }
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }

    private void otherBehavior(TestContext t) {
        try {
            for (boolean on : new boolean[] {true, false}) {
                MobTweaksFeature.getConfig().netherAttackInDark = on;
                MobTweaksFeature.getConfig().netherSpawnInDark = on;
                arena(t, 15);
                t.gameMode(GameType.CREATIVE); // not a target, so only breeding is compared
                int first = netherMob(t, EntityType.HOGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
                int second = netherMob(t, EntityType.HOGLIN, PX + 5.5, PZ + 1.5, MobSpawnType.NATURAL);
                t.onServer(level -> {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayers().getFirst();
                    for (int id : new int[] {first, second}) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CRIMSON_FUNGUS));
                        ((Mob) level.getEntity(id)).interact(player, InteractionHand.MAIN_HAND);
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    return null;
                });
                boolean bred = false;
                for (int tick = 0; tick < 400 && !bred; tick += 10) {
                    t.ticks(10);
                    bred = t.onServer(level -> level.getEntitiesOfClass(Hoglin.class, new AABB(
                        TestSite.ORIGIN.offset(PX - 12, -2, PZ - 12).getCenter(), TestSite.ORIGIN.offset(PX + 12, 4,
                        PZ + 12).getCenter()), Hoglin::isBaby).size() > 0);
                }
                t.check("option " + (on ? "on" : "off") + ": two hoglins fed crimson fungus in the light have a baby",
                    bred).isTrue();
                removeMobs(t);

                t.gameMode(GameType.SURVIVAL);
                t.difficulty(Difficulty.EASY);
                int piglin = t.spawn(EntityType.PIGLIN, PX + 4.5, 0.0, PZ + 0.5, 90.0F, created -> {
                    created.setBaby(false);
                    created.setPersistenceRequired();
                }).id();
                // a calm piglin wanders while it waits to convert, so the whole site is searched
                boolean zombified = false;
                for (int tick = 0; tick < 500 && !zombified; tick += 10) {
                    t.ticks(10);
                    zombified = t.onServer(level -> level.getEntity(piglin) == null && !level.getEntitiesOfClass(
                        Mob.class, new AABB(TestSite.ORIGIN.offset(TestSite.MIN, -4, TestSite.MIN).getCenter(),
                        TestSite.ORIGIN.offset(TestSite.MAX, 16, TestSite.MAX).getCenter()),
                        mob -> mob.getType() == EntityType.ZOMBIFIED_PIGLIN).isEmpty());
                }
                t.check("option " + (on ? "on" : "off") + ": a piglin in the Overworld turns into a zombified piglin",
                    zombified).isTrue();
                leaveArena(t);
                removeMobs(t);
            }
        } finally {
            leaveArena(t);
            removeMobs(t);
        }
    }

    /** Returns the server's average time per tick over a watch, in milliseconds */
    private static double tickMillis(TestContext t, int ticks) {
        long[] before = t.onServer(level -> level.getServer().getTickTimesNanos().clone());
        t.ticks(ticks);
        long[] after = t.onServer(level -> level.getServer().getTickTimesNanos().clone());
        long sum = 0;
        int count = 0;
        for (int index = 0; index < after.length; index++) {
            if (after[index] != before[index]) {
                sum += after[index];
                count++;
            }
        }
        return count == 0 ? 0 : sum / (double) count / 1_000_000.0;
    }

    private void netherLoad(TestContext t) {
        try {
            t.difficulty(Difficulty.EASY);
            netherRoom(t);
            netherLight(t, 15);
            for (int index = 0; index < 30; index++) {
                double x = -16.5 + (index % 6) * 6;
                double z = -16.5 + (index / 6) * 7;
                netherRoomMob(t, EntityType.PIGLIN, x, z);
                netherRoomMob(t, EntityType.HOGLIN, x + 3, z + 2);
            }
            t.ticks(40);
            List<Double> results = new ArrayList<>();
            for (int round = 0; round < 2; round++) {
                for (boolean on : new boolean[] {true, false}) {
                    MobTweaksFeature.getConfig().netherAttackInDark = on;
                    t.ticks(20);
                    results.add(tickMillis(t, 100));
                }
            }
            t.note("server ms per tick with 60 piglins and hoglins: on, off, on, off", results);
            t.check("well within a tick's 50 ms with the option on", Math.max(results.get(0), results.get(2)))
                .atMost(25);
        } finally {
            clearNetherRoom(t);
            leaveNether(t);
        }
    }


    /** Times a million runs of the per-tick check of a mob, in nanoseconds per run */
    private static double nanosPerCheck(TestContext t, int id) {
        return t.onServer(level -> {
            Mob mob = (Mob) level.getEntity(id);
            long begin = System.nanoTime();
            for (int run = 0; run < 1_000_000; run++) {
                MobTweaksInternals.afterAiStep(mob);
            }
            return (System.nanoTime() - begin) / 1_000_000.0;
        });
    }

    private void cost(TestContext t) {
        try {
            arena(t, 15);
            int idle = netherMob(t, EntityType.PIGLIN, PX + 4.5, PZ + 0.5, MobSpawnType.NATURAL);
            t.ticks(20);
            t.require(!targetsPlayer(t, idle), "The piglin attacked");
            double idleNanos = nanosPerCheck(t, idle);
            hit(t, idle);
            t.require(waitTarget(t, idle, true, 40) >= 0, "The piglin did not attack");
            double attackingNanos = nanosPerCheck(t, idle);
            t.note("per tick, an idle piglin (ns)", idleNanos);
            t.note("per tick, a piglin attacking the player in the light (ns)", attackingNanos);
            t.check("idle: well under a microsecond", idleNanos).atMost(1000);
            t.check("attacking: well under a microsecond", attackingNanos).atMost(1000);
        } finally {
            leaveArena(t);
            removeMobs(t);
        }
    }

    private void settings(TestContext t) throws Exception {
        t.openScreen(() -> new MainConfigScreen(null));
        List<String> tiles = t.onClientThread(() -> {
            List<String> names = new ArrayList<>();
            for (GuiEventListener child : t.mc().screen.children()) {
                if (child instanceof ConfigList list) {
                    for (ConfigList.Row row : list.children()) {
                        if (row instanceof ConfigList.TileRow tileRow) {
                            tileRow.tiles().forEach(tile -> names.add(tile.name().getString()));
                        }
                    }
                }
            }
            return names;
        });
        t.check("the main settings screen has a Mob Tweaks tile " + tiles, tiles.contains("Mob Tweaks")).isTrue();
        t.saveScreenshot("main_settings");
        Path file = FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/mob_tweaks.json");
        t.require(t.openSettings(id()), "The settings screen did not open");
        t.saveScreenshot("settings");
        List<AbstractWidget> controls = t.onClientThread(() -> {
            List<AbstractWidget> found = new ArrayList<>();
            collect(t.mc().screen, found);
            return found;
        });
        List<AbstractWidget> toggles = controls.stream().filter(widget -> widget instanceof CycleButton<?>).toList();
        List<AbstractWidget> sliders = controls.stream().filter(widget -> widget instanceof AbstractSliderButton)
            .toList();
        t.require(toggles.size() == 3 && sliders.size() == 3,
            "Expected 3 toggles and 3 sliders, found " + toggles.size() + " and " + sliders.size());
        String[] fields = {"endermanBlocksStay", "netherSpawnInDark", "netherAttackInDark"};
        int right = 0;
        for (int index = 0; index < toggles.size(); index++) {
            AbstractWidget toggle = toggles.get(index);
            t.onClientThread(() -> {
                ((AbstractButton) toggle).onPress();
                return null;
            });
            MobTweaksConfig config = MobTweaksFeature.getConfig();
            boolean[] values = {config.endermanBlocksStay, config.netherSpawnInDark, config.netherAttackInDark};
            String json = Files.readString(file);
            boolean ok = true;
            for (int other = 0; other < fields.length; other++) {
                boolean expected = other > index;
                ok &= values[other] == expected && json.contains("\"" + fields[other] + "\": " + expected);
            }
            right += ok ? 1 : 0;
        }
        t.check("each toggle turns off its own option and saves it", right).isEqualTo(3);
        t.ticks(2);
        t.check("rules off: their sliders are grayed out (spawn light, attack light, anger time)",
            t.onClientThread(() -> sliders.stream().map(slider -> slider.active).toList()))
            .isEqualTo(List.of(false, false, false));
        t.require(press(t, key("firstone-framework.config.reset")), "No Reset button");
        press(t, key("gui.yes"));
        MobTweaksConfig defaults = new MobTweaksConfig();
        MobTweaksConfig config = MobTweaksFeature.getConfig();
        t.check("Reset restores the defaults", config.endermanBlocksStay == defaults.endermanBlocksStay
            && config.netherSpawnInDark == defaults.netherSpawnInDark && config.netherAttackInDark
            == defaults.netherAttackInDark && config.netherSpawnMaxLight == defaults.netherSpawnMaxLight
            && config.netherAttackMaxLight == defaults.netherAttackMaxLight
            && config.netherAngerSeconds == defaults.netherAngerSeconds).isTrue();
        t.check("defaults: on, light 10, anger 30 s", defaults.netherSpawnInDark && defaults.netherAttackInDark
            && defaults.netherSpawnMaxLight == 10 && defaults.netherAttackMaxLight == 10
            && defaults.netherAngerSeconds == 30).isTrue();
    }

    /** Collects the widgets of a screen, also inside its lists and rows, in their order */
    private static void collect(ContainerEventHandler parent, List<AbstractWidget> found) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof ContainerEventHandler container) {
                collect(container, found);
            } else if (child instanceof AbstractWidget widget) {
                found.add(widget);
            }
        }
    }
}
