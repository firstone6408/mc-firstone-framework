package io.github.firstone.framework.devtest.core;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.client.FeatureScreenRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What a test works with: time, the world, the player, entities, checks and screenshots
 *
 * <p>Test code runs on the test thread while the game waits between two client ticks ({@link TickBridge}): it may
 * read and change client state (the player, keys, options, the client's entities) directly. Server state is read and
 * changed on the server thread with {@link #onServer} and {@link #command}.</p>
 *
 * <p>Positions are relative to the {@link TestSite} origin.</p>
 */
public final class TestContext {

    /** Where the player waits between tests, relative to the site origin */
    private static final double HOME_X = -6.5;
    private static final double HOME_Z = -6.5;
    private static final double HOME_Y = 2.0;

    private final Minecraft minecraft;
    private final TickBridge bridge;
    private final TestReport report;
    private final String featureId;
    private final Screenshots screenshots;
    private final TestSite site = new TestSite(this);
    private final List<SoundRecorder> soundRecorders = new ArrayList<>();
    private final List<TestResourcePack> resourcePacks = new ArrayList<>();

    TestContext(Minecraft minecraft, TickBridge bridge, TestReport report, String featureId,
                Screenshots screenshots) {
        this.minecraft = minecraft;
        this.bridge = bridge;
        this.report = report;
        this.featureId = featureId;
        this.screenshots = screenshots;
    }

    // ---- game access ----------------------------------------------------------------------------------------------

    /**
     * Returns the game
     *
     * @return the game
     */
    public Minecraft mc() {
        return this.minecraft;
    }

    /**
     * Returns the local player
     *
     * @return the player
     */
    public LocalPlayer player() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            throw new TestFailure("The player is not in a world");
        }
        return player;
    }

    /**
     * Returns the client's level
     *
     * @return the level, or null outside a world
     */
    @Nullable
    public ClientLevel level() {
        return this.minecraft.level;
    }

    /**
     * Returns the site where tests build
     *
     * @return the test site
     */
    public TestSite site() {
        return this.site;
    }

    // ---- time -----------------------------------------------------------------------------------------------------

    /** Lets the game run one client tick */
    public void tick() {
        this.bridge.waitTicks(1);
    }

    /**
     * Lets the game run some client ticks (20 per second)
     *
     * @param ticks how many
     */
    public void ticks(int ticks) {
        this.bridge.waitTicks(ticks);
    }

    /**
     * Lets the game run until a condition is true, checking it after each tick
     *
     * @param what      what is waited for, for the failure message
     * @param condition the condition
     * @param maxTicks  ticks after which the test fails
     * @throws TestFailure if the condition is still false after {@code maxTicks}
     */
    public void waitUntil(String what, BooleanSupplier condition, int maxTicks) {
        for (int tick = 0; !condition.getAsBoolean(); tick++) {
            if (tick >= maxTicks) {
                throw new TestFailure("Timed out after " + maxTicks + " ticks waiting until " + what);
            }
            tick();
        }
    }

    // ---- threads and commands -------------------------------------------------------------------------------------

    /**
     * Runs code on the client thread (needed for rendering and screen changes) and returns its result
     *
     * @param task the code
     * @param <T>  type of the result
     * @return the result
     */
    public <T> T onClientThread(Supplier<T> task) {
        return this.bridge.onClientThread(task);
    }

    /**
     * Runs code on the server thread with the overworld and returns its result
     *
     * @param task the code
     * @param <T>  type of the result
     * @return the result
     */
    public <T> T onServer(Function<ServerLevel, T> task) {
        MinecraftServer server = server();
        return TickBridge.join(server.submit(() -> task.apply(server.overworld())));
    }

    /**
     * Runs code on the server thread with the server's copy of the player
     *
     * @param task the code
     */
    public void withServerPlayer(Consumer<ServerPlayer> task) {
        onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(player().getUUID());
            if (player == null) {
                throw new TestFailure("The server has no player");
            }
            task.accept(player);
            return null;
        });
    }

    /**
     * Runs commands on the server, as an operator at the world spawn (absolute coordinates)
     *
     * @param commands the commands, without {@code /}
     * @throws TestFailure if a command fails
     */
    public void command(String... commands) {
        onServer(level -> {
            for (String command : commands) {
                AtomicBoolean succeeded = new AtomicBoolean();
                CommandSourceStack source = level.getServer().createCommandSourceStack().withSuppressedOutput()
                    .withCallback((success, result) -> succeeded.set(success));
                level.getServer().getCommands().performPrefixedCommand(source, command);
                if (!succeeded.get()) {
                    throw new TestFailure("Command failed: /" + command);
                }
            }
            return null;
        });
    }

    private MinecraftServer server() {
        MinecraftServer server = this.minecraft.getSingleplayerServer();
        if (server == null) {
            throw new TestFailure("No singleplayer server");
        }
        return server;
    }

    // ---- entities and the player ----------------------------------------------------------------------------------

    /**
     * Spawns an entity on the server and waits until the client has it
     *
     * @param type  the entity type
     * @param x     x, relative to the site origin
     * @param y     y
     * @param z     z
     * @param yaw   yaw in degrees (0 faces +z)
     * @param setup changes the entity before it is added (variant, motion, items), may be null
     * @param <T>   the entity's class
     * @return the spawned entity
     */
    public <T extends Entity> Spawned<T> spawn(EntityType<T> type, double x, double y, double z, float yaw,
                                               @Nullable Consumer<T> setup) {
        BlockPos origin = TestSite.ORIGIN;
        int id = onServer(level -> {
            @Nullable T entity = type.create(level);
            if (entity == null) {
                throw new TestFailure("Cannot create " + type);
            }
            entity.moveTo(origin.getX() + x, origin.getY() + y, origin.getZ() + z, yaw, 0.0F);
            entity.setYHeadRot(yaw);
            if (setup != null) {
                setup.accept(entity);
            }
            level.addFreshEntity(entity);
            return entity.getId();
        });
        Spawned<T> spawned = new Spawned<>(this, id);
        waitUntil("the client sees the new " + type.getDescription().getString(), () -> spawned.client() != null,
            40);
        return spawned;
    }

    /**
     * Removes every entity except players at once (unlike {@code /kill}: no death animation, no drops)
     */
    public void removeEntities() {
        removeEntities(Entity.class);
    }

    /**
     * Removes every entity of a class at once (unlike {@code /kill}: no death animation, no drops); players are kept
     *
     * @param type the class, e.g. {@code Boat.class}
     */
    public void removeEntities(Class<? extends Entity> type) {
        onServer(level -> {
            List<Entity> removed = new ArrayList<>();
            level.getAllEntities().forEach(entity -> {
                if (type.isInstance(entity) && !(entity instanceof Player)) {
                    removed.add(entity);
                }
            });
            for (Entity entity : removed) {
                entity.discard();
            }
            return null;
        });
        tick();
    }

    /**
     * Teleports the player
     *
     * @param x     x, relative to the site origin
     * @param y     y
     * @param z     z
     * @param yaw   yaw in degrees (0 faces +z)
     * @param pitch pitch in degrees (positive looks down)
     */
    public void teleport(double x, double y, double z, float yaw, float pitch) {
        BlockPos origin = TestSite.ORIGIN;
        withServerPlayer(player -> player.teleportTo(player.serverLevel(), origin.getX() + x, origin.getY() + y,
            origin.getZ() + z, yaw, pitch));
        waitUntil("the player arrives", () -> Math.abs(player().getX() - (origin.getX() + x)) < 0.01
            && Math.abs(player().getZ() - (origin.getZ() + z)) < 0.01, 40);
        player().setYRot(yaw);
        player().setXRot(pitch);
    }

    /**
     * Puts the player into a vehicle and waits until the client sees it
     *
     * @param vehicle the vehicle
     */
    public void ride(Spawned<?> vehicle) {
        withServerPlayer(player -> {
            Entity entity = player.serverLevel().getEntity(vehicle.id());
            if (entity == null || !player.startRiding(entity, true)) {
                throw new TestFailure("The player cannot get into entity " + vehicle.id());
            }
        });
        waitUntil("the player sits in the vehicle", () -> player().getVehicle() != null
            && player().getVehicle().getId() == vehicle.id(), 40);
    }

    /** Gets the player out of their vehicle like a player does: sneak for two ticks */
    public void dismount() {
        hold(this.minecraft.options.keyShift);
        ticks(2);
        release(this.minecraft.options.keyShift);
        waitUntil("the player gets out", () -> player().getVehicle() == null, 20);
    }

    /**
     * Turns the player (the game reads it on the next tick)
     *
     * @param yaw yaw in degrees (0 faces +z, 90 faces -x)
     */
    public void face(float yaw) {
        player().setYRot(yaw);
    }

    /**
     * Holds a key down until {@link #release} or the end of the test
     *
     * @param key the key, e.g. {@code mc().options.keyUp}
     */
    public void hold(KeyMapping key) {
        key.setDown(true);
    }

    /**
     * Lets go of a key
     *
     * @param key the key
     */
    public void release(KeyMapping key) {
        key.setDown(false);
    }

    /**
     * Presses and lets go of a keyboard key, through the game's keyboard handler (as a real key press)
     *
     * @param key the GLFW key code, e.g. {@code GLFW.GLFW_KEY_ESCAPE}
     */
    public void pressKey(int key) {
        onClientThread(() -> {
            long window = this.minecraft.getWindow().getWindow();
            this.minecraft.keyboardHandler.keyPress(window, key, 0, GLFW.GLFW_PRESS, 0);
            this.minecraft.keyboardHandler.keyPress(window, key, 0, GLFW.GLFW_RELEASE, 0);
            return null;
        });
        tick();
    }

    /**
     * Clicks a key once, as the game counts clicks (attack, drop and use count clicks; holding only repeats some of
     * them)
     *
     * @param key the key, e.g. {@code mc().options.keyAttack}
     */
    public void click(KeyMapping key) {
        onClientThread(() -> {
            KeyMapping.click(InputConstants.getKey(key.saveString()));
            return null;
        });
        tick();
    }

    /**
     * Makes an item with an enchantment
     *
     * @param item        the item
     * @param enchantment the enchantment, e.g. {@code Enchantments.SWEEPING_EDGE}
     * @param level       the enchantment level
     * @return the item
     */
    public ItemStack enchanted(Item item, ResourceKey<Enchantment> enchantment, int level) {
        return onServer(serverLevel -> {
            ItemStack stack = new ItemStack(item);
            stack.enchant(serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment),
                level);
            return stack;
        });
    }

    /**
     * Puts an item into the player's main hand (on the server; the client gets it a tick later)
     *
     * @param stack the item, or {@code ItemStack.EMPTY}
     */
    public void holdItem(ItemStack stack) {
        withServerPlayer(player -> player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy()));
        ticks(3);
    }

    /**
     * Sets the world's difficulty (like {@code /difficulty}, which fails when the difficulty is already set)
     *
     * @param difficulty the difficulty
     */
    public void difficulty(Difficulty difficulty) {
        onServer(level -> {
            level.getServer().setDifficulty(difficulty, true);
            return null;
        });
    }

    /**
     * Sets the player's game mode
     *
     * @param mode the game mode
     */
    public void gameMode(GameType mode) {
        withServerPlayer(player -> player.setGameMode(mode));
        waitUntil("the game mode changes", () -> this.minecraft.gameMode != null
            && this.minecraft.gameMode.getPlayerMode() == mode, 20);
    }

    /**
     * Turns creative flying on or off (also stops the player from falling while waiting)
     *
     * @param flying fly
     */
    public void setFlying(boolean flying) {
        player().getAbilities().flying = flying;
    }

    /**
     * Opens a screen and lets it draw a few frames
     *
     * @param screen makes the screen
     */
    public void openScreen(Supplier<Screen> screen) {
        onClientThread(() -> {
            this.minecraft.setScreen(screen.get());
            return null;
        });
        ticks(5);
    }

    /**
     * Opens a feature's settings screen the way the main settings screen does ({@code FeatureScreenRegistry}) and
     * lets it draw a few frames
     *
     * @param featureId the feature id, e.g. {@code legacy_vehicles}
     * @return true if the feature has a settings screen and it is open now
     */
    public boolean openSettings(String featureId) {
        Screen screen = onClientThread(() -> {
            Screen created = FeatureScreenRegistry.createScreen(featureId, null);
            if (created != null) {
                this.minecraft.setScreen(created);
            }
            return created;
        });
        ticks(5);
        return screen != null && this.minecraft.screen == screen;
    }

    /** Closes the open screen, like pressing Escape */
    public void closeScreen() {
        onClientThread(() -> {
            if (this.minecraft.screen != null) {
                this.minecraft.screen.onClose();
            }
            return null;
        });
        ticks(2);
    }

    // ---- checks ---------------------------------------------------------------------------------------------------

    /**
     * Starts a check of a value; finish it with a method of {@link Check}, e.g. {@code near(0.35, 0.001)}
     *
     * @param name  what is checked, shown in the report
     * @param value the measured value
     * @return the check
     */
    public Check check(String name, Object value) {
        return new Check(this.report, name, value);
    }

    /**
     * Records a value in the report without checking it (to see it when looking into a failure)
     *
     * @param name  what the value is
     * @param value the value
     */
    public void note(String name, Object value) {
        this.report.note(name, value instanceof Double number ? Check.format(number) : String.valueOf(value));
    }

    /**
     * Stops the test if a condition is false (when the rest of the test would make no sense)
     *
     * @param condition the condition
     * @param message   what went wrong
     * @throws TestFailure if {@code condition} is false
     */
    public void require(boolean condition, String message) {
        if (!condition) {
            throw new TestFailure(message);
        }
    }

    // ---- sound, particles, screenshots, resource packs ------------------------------------------------------------

    /**
     * Starts recording the sounds the client plays, until the end of the test
     *
     * @return the recorder
     */
    public SoundRecorder recordSounds() {
        SoundRecorder recorder = new SoundRecorder();
        this.minecraft.getSoundManager().addListener(recorder);
        this.soundRecorders.add(recorder);
        return recorder;
    }

    /** Removes every particle the client shows, so the next count starts from nothing */
    public void clearParticles() {
        onClientThread(() -> {
            clearParticlesNow();
            return null;
        });
    }

    /** Clears the particles: setting the same level again clears them ({@code ParticleEngine.setLevel}) */
    private void clearParticlesNow() {
        if (this.minecraft.level != null) {
            this.minecraft.particleEngine.setLevel(this.minecraft.level);
        }
    }

    /**
     * Counts the particles of one kind the client shows now
     *
     * <p>Water blocks keep spawning {@code underwater} particles, so counting one kind (e.g.
     * {@code SplashParticle}) is more exact than {@code ParticleEngine.countParticles}.</p>
     *
     * @param type the particle class, e.g. {@code SplashParticle.class}
     * @return how many there are
     */
    public int particleCount(Class<? extends Particle> type) {
        return particles(type).size();
    }

    /**
     * Returns the live particles of one kind the client shows now
     *
     * @param type the particle class
     * @param <P>  the particle class
     * @return the particles (a copy of the list; read their state before the next tick)
     */
    public <P extends Particle> List<P> particles(Class<P> type) {
        List<P> found = new ArrayList<>();
        for (Collection<?> particles : particleQueues()) {
            for (Object particle : particles) {
                if (type.isInstance(particle)) {
                    found.add(type.cast(particle));
                }
            }
        }
        return found;
    }

    /**
     * Returns the particle engine's lists of live particles, found by type ({@code ParticleEngine.particles}, a map
     * of render type to queue; its name differs in production, its type does not)
     */
    private List<Collection<?>> particleQueues() {
        ParticleEngine engine = this.minecraft.particleEngine;
        for (Field field : ParticleEngine.class.getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType()) || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) field.get(engine);
                if (!map.isEmpty() && map.values().iterator().next() instanceof Queue<?>) {
                    List<Collection<?>> queues = new ArrayList<>();
                    map.values().forEach(value -> queues.add((Collection<?>) value));
                    return queues;
                }
            } catch (IllegalAccessException e) {
                throw new TestFailure("Cannot read the particles: " + e);
            }
        }
        return List.of();
    }

    /**
     * Takes a screenshot without the HUD, saves it in {@code results/screenshots/<feature>/} and checks that it
     * differs from its baseline by at most {@code maxDifferingPercent} of the pixels
     *
     * @param name                file name without extension
     * @param maxDifferingPercent allowed share of differing pixels, in percent
     */
    public void screenshot(String name, double maxDifferingPercent) {
        double differing = takeScreenshot(name, true);
        if (differing < 0.0) {
            note("screenshot " + name, "new baseline saved");
        } else {
            check("screenshot " + name + ": differing pixels %", differing).atMost(maxDifferingPercent);
        }
    }

    /**
     * Captures the current frame as it is shown (with the HUD), without saving it
     *
     * @return the frame; the caller closes it
     */
    public NativeImage captureFrame() {
        ticks(2);
        return onClientThread(() -> Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget()));
    }

    /**
     * Saves a frame captured with {@link #captureFrame} in {@code results/screenshots/<feature>/}
     *
     * @param name  file name without extension
     * @param frame the frame; not closed here
     */
    public void saveFrame(String name, NativeImage frame) {
        NativeImage copy = new NativeImage(frame.getWidth(), frame.getHeight(), false);
        copy.copyFrom(frame);
        try {
            this.screenshots.save(name, copy);
        } catch (IOException e) {
            throw new TestFailure("Cannot save screenshot " + name + ": " + e);
        }
    }

    /**
     * Takes a screenshot without the HUD and only saves it in {@code results/screenshots/<feature>/}, for views that
     * depend on something the test does not control (e.g. a screen that highlights the row under the mouse)
     *
     * @param name file name without extension
     */
    public void saveScreenshot(String name) {
        takeScreenshot(name, false);
        note("screenshot " + name, "saved, not compared");
    }

    private double takeScreenshot(String name, boolean compare) {
        boolean hudHidden = this.minecraft.options.hideGui;
        this.minecraft.options.hideGui = true;
        ticks(3);
        NativeImage image = onClientThread(() -> Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget()));
        this.minecraft.options.hideGui = hudHidden;
        try {
            return compare ? this.screenshots.saveAndCompare(name, image) : this.screenshots.save(name, image);
        } catch (IOException e) {
            throw new TestFailure("Cannot save screenshot " + name + ": " + e);
        }
    }

    /**
     * Makes a resource pack for this test (turned off and deleted at the end of the test)
     *
     * @param name folder name of the pack
     * @return the pack, still off
     * @throws IOException if the pack cannot be written
     */
    public TestResourcePack resourcePack(String name) throws IOException {
        TestResourcePack pack = new TestResourcePack(this,
            this.minecraft.getResourcePackDirectory().resolve("devtest_" + this.featureId + "_" + name));
        this.resourcePacks.add(pack);
        return pack;
    }

    // ---- between tests --------------------------------------------------------------------------------------------

    /**
     * Before each test: player home in creative and flying, without items or effects, no other entities or
     * particles, peaceful difficulty, the site cleared
     */
    void resetForTest() {
        KeyMapping.releaseAll();
        onClientThread(() -> {
            this.minecraft.getToasts().clear();
            clearParticlesNow();
            return null;
        });
        this.minecraft.options.hideGui = false;
        this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        withServerPlayer(player -> {
            player.stopRiding();
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
        });
        removeEntities();
        difficulty(Difficulty.PEACEFUL);
        command("gamerule doEntityDrops true", "time set noon", "weather clear");
        teleport(HOME_X, HOME_Y, HOME_Z, 0.0F, 0.0F);
        setFlying(true);
        this.site.reset();
        ticks(10);
    }

    /** After each test: screen closed, keys up, HUD and camera back, recorders and resource packs removed */
    void cleanUpAfterTest() {
        if (this.minecraft.level != null && this.minecraft.screen != null) {
            onClientThread(() -> {
                this.minecraft.setScreen(null);
                return null;
            });
        }
        KeyMapping.releaseAll();
        this.minecraft.options.hideGui = false;
        this.minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        for (SoundRecorder recorder : this.soundRecorders) {
            this.minecraft.getSoundManager().removeListener(recorder);
        }
        this.soundRecorders.clear();
        for (TestResourcePack pack : this.resourcePacks) {
            try {
                pack.remove();
            } catch (IOException | RuntimeException e) {
                this.report.error(e);
            }
        }
        this.resourcePacks.clear();
    }
}
