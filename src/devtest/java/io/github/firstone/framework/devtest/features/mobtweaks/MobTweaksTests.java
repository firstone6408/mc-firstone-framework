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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.AABB;
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
        t.removeEntities(EnderMan.class);
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
        t.require(press(t, widget -> widget instanceof CycleButton<?>), "No Enderman Blocks Stay toggle");
        t.check("the toggle turns the option off", MobTweaksFeature.getConfig().endermanBlocksStay).isFalse();
        t.check("and saves it", Files.readString(file).contains("\"endermanBlocksStay\": false")).isTrue();
        t.require(press(t, key("firstone-framework.config.reset")), "No Reset button");
        press(t, key("gui.yes"));
        MobTweaksConfig defaults = new MobTweaksConfig();
        t.check("Reset restores the default", MobTweaksFeature.getConfig().endermanBlocksStay)
            .isEqualTo(defaults.endermanBlocksStay);
    }
}
