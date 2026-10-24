package io.github.firstone.framework.devtest.features.appearance.loading;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.CategoryMenuChecks;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.mixin.ConnectScreenInvoker;
import io.github.firstone.framework.devtest.mixin.LoadingOverlayAccessor;
import io.github.firstone.framework.features.appearance.AppearanceConfig;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Tests of the 1.7.10 startup and loading screens, part of the Appearance tests
 *
 * <p>The screens are opened as the game opens them and read back from the frame on the screen: the dirt background
 * is compared pixel by pixel with the game's dirt texture darkened like 1.7.10, texts are found as white pixels where
 * 1.7.10 drew them, and the startup screen is seen while the game really loads its resources again. Each check runs
 * with the option on and off; off must look like 1.21.1.</p>
 */
public final class LoadingScreenTests {

    /** The dirt texture and how dark 1.7.10 drew it (0x404040) */
    private static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("textures/block/dirt.png");
    private static final double DIRT_SHADE = 0x40 / 255.0;

    /** Allowed difference of a color channel drawn the same way */
    private static final int COLOR_TOLERANCE = 3;

    /** Lightest channel of a pixel counted as white text */
    private static final int WHITE = 230;

    /** Mojang red, the 1.21.1 startup background and the color of the game's logo on the 1.7.10 screen */
    static final int[] MOJANG_RED = {239, 50, 61};

    /** File names of the test logos: in the logos folder, too big, and next to the folder */
    private static final String TEST_LOGO = "devtest_logo.png";
    private static final String BIG_LOGO = "devtest_big_logo.png";
    private static final String OUTSIDE_LOGO = "devtest_outside_logo.png";

    private LoadingScreenTests() {}

    /**
     * Returns the loading screen tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Loading screens: the 1.7.10 dirt behind every loading screen (message, world, receiving with "
                + "each portal, connecting, progress); off: 1.21.1's backgrounds", LoadingScreenTests::dirt),
            TestCase.of("Loading screens: \"Loading world\" and \"Building terrain\" instead of the chunk map and the "
                + "percentage; the Loading Bar shows the progress; off: 1.21.1", LoadingScreenTests::worldLoading),
            TestCase.of("Loading screens: \"Downloading terrain\" while a world arrives; off: \"Loading terrain...\"",
                LoadingScreenTests::downloadingTerrain),
            TestCase.of("Loading screens: a real trip to the Nether and back opens and closes the screen as before",
                LoadingScreenTests::dimensionChange),
            TestCase.of("Startup screen: reloading resources shows white with the logo in red, no fading, then the "
                + "game at once; off: the 1.21.1 red screen fading", LoadingScreenTests::startupScreen),
            TestCase.of("Startup screen: a logo from the logos folder is drawn at most 256 wide; a missing file, a "
                + "name leaving the folder or a picture over 2048 pixels shows the game's logo",
                LoadingScreenTests::customLogo),
            TestCase.of("Startup screen: the Startup Bar shows the loading in the 1.7.10 bar style under the logo; "
                + "off: no bar", LoadingScreenTests::startupBar),
            TestCase.of("Startup screen: Startup Background Dark is black with the logo still red and the bar seen; "
                + "an unknown value in the file is Light", LoadingScreenTests::darkStartup),
            TestCase.of("Message screens: no box; \"Loading world\" while a world opens, nothing while it is saved, "
                + "other messages as they are; off: 1.21.1's box and texts", LoadingScreenTests::messageScreens),
            TestCase.of("Settings: two categories (Window, Loading Screens), every option once, each opens, writes "
                + "and resets on its own; drawing and reload time on and off", LoadingScreenTests::settings));
    }

    static AppearanceConfig config() {
        return AppearanceFeature.getConfig();
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private static void dirt(TestContext t) throws IOException {
        List<Supplier<Screen>> screens = List.of(
            () -> new GenericMessageScreen(Component.translatable("menu.savingLevel")),
            () -> new LevelLoadingScreen(StoringChunkProgressListener.create(2)),
            () -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER),
            () -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.NETHER_PORTAL),
            () -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.END_PORTAL),
            () -> ConnectScreenInvoker.devtest$create(null, Component.translatable("connect.connecting")),
            () -> new ProgressScreen(false));
        String[] names = {"message", "world", "receiving", "receiving (Nether portal)", "receiving (End portal)",
            "connecting", "progress"};
        int[][] dirt = dirtTexels(t);
        for (boolean on : new boolean[] {true, false}) {
            config().legacyLoadingScreens = on;
            int matching = 0;
            List<String> wrong = new ArrayList<>();
            for (int i = 0; i < screens.size(); i++) {
                t.openScreen(screens.get(i));
                NativeImage frame = t.captureFrame();
                if (on) {
                    t.saveFrame("loading_" + i, frame);
                }
                boolean dirtShown = isDirt(t, frame, dirt);
                frame.close();
                matching += dirtShown ? 1 : 0;
                if (dirtShown != on) {
                    wrong.add(names[i]);
                }
                closeScreen(t);
            }
            t.check((on ? "on: dirt behind every loading screen " : "off: no dirt behind any of them ") + wrong,
                matching).near(on ? screens.size() : 0, 0);
        }
    }

    private static void worldLoading(TestContext t) {
        StoringChunkProgressListener listener = StoringChunkProgressListener.create(1);
        listener.start();
        listener.updateSpawnPos(new ChunkPos(0, 0));
        // 3 of the 9 chunks around the spawn are done: 33 %
        for (int i = 0; i < 3; i++) {
            listener.onStatusChange(new ChunkPos(i - 1, 0), ChunkStatus.FULL);
        }
        int progress = listener.getProgress();
        int[][] dirt = dirtTexels(t);
        for (int mode = 0; mode < 3; mode++) {
            boolean on = mode > 0;
            boolean bar = mode == 2;
            String name = !on ? "off: " : bar ? "with the bar: " : "on: ";
            config().legacyLoadingScreens = on;
            config().legacyLoadingBar = bar;
            t.openScreen(() -> new LevelLoadingScreen(listener));
            NativeImage frame = t.captureFrame();
            t.saveFrame("world_loading_" + mode, frame);
            int scale = guiScale(t);
            int middleX = frame.getWidth() / scale / 2;
            int middleY = frame.getHeight() / scale / 2;
            boolean title = hasWhite(frame, scale, middleY - 20, middleY - 13);
            boolean message = hasWhite(frame, scale, middleY + 4, middleY + 11);
            boolean middleIsDirt = pixelIs(frame, middleX * scale, middleY * scale,
                dirtAt(dirt, middleX, middleY));
            if (on) {
                t.check(name + "\"Loading world\" where 1.7.10 drew it", title).isTrue();
                t.check(name + "\"Building terrain\" where 1.7.10 drew it", message).isTrue();
                t.check(name + "no chunk map (dirt in the middle)", middleIsDirt).isTrue();
            } else {
                t.check(name + "the chunk map in the middle", middleIsDirt).isFalse();
                t.check(name + "no \"Building terrain\" text under the middle", message).isFalse();
            }
            int barTop = (middleY + 16) * scale;
            int barLeft = (middleX - 50) * scale;
            int[] done = color(frame, barLeft + scale, barTop);
            int[] rest = color(frame, barLeft + 98 * scale, barTop);
            boolean green = near(done, new int[] {0x80, 0xFF, 0x80});
            boolean gray = near(rest, new int[] {0x80, 0x80, 0x80});
            if (bar) {
                t.check(name + progress + " % done: green at the start of the bar", green).isTrue();
                t.check(name + "gray at the end of the bar", gray).isTrue();
                int greenWidth = 0;
                for (int x = 0; x < 100; x++) {
                    greenWidth += near(color(frame, barLeft + x * scale, barTop), new int[] {0x80, 0xFF, 0x80})
                        ? 1 : 0;
                }
                t.check(name + "green width in pixels = the progress", greenWidth).near(progress, 1);
            } else if (on) {
                t.check(name + "no bar", green || gray).isFalse();
            }
            frame.close();
            closeScreen(t);
        }
        config().legacyLoadingBar = false;
    }

    private static void downloadingTerrain(TestContext t) {
        int scale = guiScale(t);
        for (boolean on : new boolean[] {true, false}) {
            config().legacyLoadingScreens = on;
            t.openScreen(() -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER));
            NativeImage frame = t.captureFrame();
            int middleY = frame.getHeight() / scale / 2;
            int width = whiteWidth(frame, scale, middleY - 50, middleY - 43);
            frame.close();
            closeScreen(t);
            String text = I18n.get(on ? "firstone-framework.appearance.downloading_terrain"
                : "multiplayer.downloadingTerrain");
            int expected = t.onClientThread(() -> t.mc().font.width(text)) - 1;
            t.check((on ? "on: " : "off: ") + "the text is as wide as \"" + text + "\" (GUI pixels)", width)
                .near(expected, 1);
        }
    }

    private static void dimensionChange(TestContext t) {
        watchReceivingScreens();
        // a spectator takes no harm inside the Nether's rock
        t.gameMode(GameType.SPECTATOR);
        for (boolean on : new boolean[] {true, false}) {
            String name = on ? "on: " : "off: ";
            config().legacyLoadingScreens = on;
            for (String dimension : new String[] {"the_nether", "overworld"}) {
                RECEIVING_OPENED.set(0);
                RECEIVING_DRAWN.set(0);
                RECEIVING_CLOSED.set(0);
                t.command("execute in minecraft:" + dimension + " run tp @p 0 100 0");
                // the screens may open and close within one tick when the chunks are ready at once
                t.waitUntil("the receiving screens opened and closed", () -> RECEIVING_OPENED.get() > 0
                    && RECEIVING_CLOSED.get() == RECEIVING_OPENED.get()
                    && t.onClientThread(() -> t.mc().screen == null), 700);
                t.ticks(2);
                String level = t.onClientThread(() -> t.mc().level.dimension().location().getPath());
                t.check(name + "arrived in " + dimension + " (" + level + ")", level.equals(dimension)).isTrue();
                int opened = RECEIVING_OPENED.get();
                t.note(name + dimension + ": receiving screens opened / frames drawn / closed",
                    opened + " / " + RECEIVING_DRAWN.get() + " / " + RECEIVING_CLOSED.get());
                t.check(name + dimension + ": every receiving screen that opened closed, none is left",
                    opened > 0 && RECEIVING_CLOSED.get() == opened
                        && t.onClientThread(() -> t.mc().screen == null)).isTrue();
            }
        }
        t.gameMode(GameType.SURVIVAL);
        t.teleport(0.5, 0.0, 0.5, 0.0F, 0.0F);
    }

    /** Receiving screens opened, drawn (frames) and closed since the counters were last set to 0 */
    private static final AtomicInteger RECEIVING_OPENED = new AtomicInteger();
    private static final AtomicInteger RECEIVING_DRAWN = new AtomicInteger();
    private static final AtomicInteger RECEIVING_CLOSED = new AtomicInteger();

    /** Whether the screen events below are listened to (once per game) */
    private static boolean watching;

    /** Counts the receiving screens the game opens, draws and closes, through the Fabric screen events */
    private static void watchReceivingScreens() {
        if (watching) {
            return;
        }
        watching = true;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ReceivingLevelScreen) {
                RECEIVING_OPENED.incrementAndGet();
                ScreenEvents.afterRender(screen).register((shown, graphics, mouseX, mouseY, partialTick)
                    -> RECEIVING_DRAWN.incrementAndGet());
                ScreenEvents.remove(screen).register(removed -> RECEIVING_CLOSED.incrementAndGet());
            }
        });
    }

    private static void startupScreen(TestContext t) {
        for (boolean on : new boolean[] {true, false}) {
            String name = on ? "on: " : "off: ";
            config().legacyStartupScreen = on;
            // a screen that does not move (the dirt) behind the overlay, so "the game shows" is compared exactly
            t.openScreen(() -> new GenericMessageScreen(Component.literal("")));
            long started = reload(t);
            NativeImage loading = frameAfter(t, started, 700L);
            t.saveFrame("startup_" + (on ? "on" : "off"), loading);
            int[] corner = color(loading, 2, 2);
            boolean red = hasLogoRed(loading);
            loading.close();
            if (on) {
                t.check(name + "white background", near(corner, new int[] {255, 255, 255})).isTrue();
                t.check(name + "the logo in Mojang red", red).isTrue();
            } else {
                t.check(name + "Mojang red background", near(corner, MOJANG_RED)).isTrue();
            }
            long done = waitLoaded(t);
            NativeImage leaving = frameAfter(t, done, 1300L);
            boolean overlayStill = t.onClientThread(() -> t.mc().getOverlay() instanceof LoadingOverlay);
            int[] leavingCorner = color(leaving, 2, 2);
            leaving.close();
            t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);
            t.ticks(5);
            NativeImage after = t.captureFrame();
            int[] afterCorner = color(after, 2, 2);
            after.close();
            t.check(name + "1.3 s after loading the overlay is still there (1.21.1 timing)", overlayStill).isTrue();
            if (on) {
                t.check(name + "the game shows at once, no white or red left",
                    near(leavingCorner, afterCorner)).isTrue();
            } else {
                t.check(name + "the red still fades over the game", near(leavingCorner, afterCorner)).isFalse();
            }
            closeScreen(t);
        }
    }

    private static void customLogo(TestContext t) throws IOException {
        Path folder = FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/appearance/logos");
        Path file = folder.resolve(TEST_LOGO);
        Path bigFile = folder.resolve(BIG_LOGO);
        Path outsideFile = folder.getParent().resolve(OUTSIDE_LOGO);
        Files.createDirectories(file.getParent());
        // a blue logo twice as wide as high
        try (NativeImage logo = new NativeImage(64, 32, false)) {
            logo.fillRect(0, 0, 64, 32, 0xFFFF0000);
            logo.writeToFile(file);
        }
        try {
            config().legacyStartupScreen = true;
            config().startupLogo = TEST_LOGO;
            long started = reload(t);
            NativeImage frame = frameAfter(t, started, 700L);
            t.saveFrame("startup_custom_logo", frame);
            int scale = guiScale(t);
            int guiWidth = frame.getWidth() / scale;
            int guiHeight = frame.getHeight() / scale;
            int size = Math.min(256, Math.min(guiWidth, guiHeight));
            int blue = 0;
            for (int x = 0; x < frame.getWidth(); x++) {
                blue += near(color(frame, x, frame.getHeight() / 2), new int[] {0, 0, 255}) ? 1 : 0;
            }
            boolean red = hasLogoRed(frame);
            frame.close();
            t.check("the logo from the folder in the middle, " + size + " wide (screen pixels)", blue)
                .near(size * scale, scale);
            t.check("the game's logo is not drawn with it", red).isFalse();
            waitLoaded(t);
            t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);

            // a too big picture: written next to the folder too, to try a name that leaves the folder
            try (NativeImage big = new NativeImage(2049, 8, false)) {
                big.fillRect(0, 0, 2049, 8, 0xFFFF0000);
                big.writeToFile(bigFile);
            }
            Files.copy(file, outsideFile);
            String[] refused = {"missing_logo.png", "../" + OUTSIDE_LOGO, BIG_LOGO};
            String[] why = {"a missing file", "a name leaving the logos folder", "a picture over 2048 pixels"};
            for (int i = 0; i < refused.length; i++) {
                config().startupLogo = refused[i];
                started = reload(t);
                frame = frameAfter(t, started, 700L);
                red = hasLogoRed(frame);
                frame.close();
                t.check(why[i] + ": the game's logo", red).isTrue();
                waitLoaded(t);
                t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(bigFile);
            Files.deleteIfExists(outsideFile);
        }
    }

    private static void startupBar(TestContext t) {
        for (boolean on : new boolean[] {true, false}) {
            config().legacyStartupScreen = true;
            config().legacyStartupBar = on;
            long started = reload(t);
            NativeImage frame = frameAfter(t, started, 700L);
            if (on) {
                t.saveFrame("startup_bar", frame);
            }
            int scale = guiScale(t);
            int guiWidth = frame.getWidth() / scale;
            int barTop = (int) (frame.getHeight() / scale * 0.8325);
            int left = guiWidth / 2 - 50;
            int barPixels = 0;
            for (int x = 0; x < 100; x++) {
                int[] c = color(frame, (left + x) * scale, barTop * scale);
                barPixels += near(c, new int[] {0x80, 0x80, 0x80}) || near(c, new int[] {0x80, 0xFF, 0x80}) ? 1 : 0;
            }
            int[] outside = color(frame, (left - 2) * scale, barTop * scale);
            frame.close();
            String name = on ? "on: " : "off: ";
            t.check(name + "bar pixels (gray or green) under the logo", barPixels).near(on ? 100 : 0, 0);
            t.check(name + "white around the bar", near(outside, new int[] {255, 255, 255})).isTrue();
            waitLoaded(t);
            t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);
        }
        config().legacyStartupBar = false;
    }

    private static void darkStartup(TestContext t) {
        config().legacyStartupScreen = true;
        config().legacyStartupBar = true;
        AppearanceConfig.StartupBackground[] backgrounds = {AppearanceConfig.StartupBackground.DARK, null};
        int[][] expected = {{0, 0, 0}, {255, 255, 255}};
        String[] names = {"Dark: ", "unknown value: "};
        for (int i = 0; i < backgrounds.length; i++) {
            config().startupBackground = backgrounds[i];
            long started = reload(t);
            NativeImage frame = frameAfter(t, started, 700L);
            if (i == 0) {
                t.saveFrame("startup_dark", frame);
            }
            int scale = guiScale(t);
            int barTop = (int) (frame.getHeight() / scale * 0.8325);
            int[] corner = color(frame, 2, 2);
            int[] barStart = color(frame, (frame.getWidth() / scale / 2 - 50) * scale, barTop * scale);
            boolean red = hasLogoRed(frame);
            frame.close();
            t.check(names[i] + "background", near(corner, expected[i])).isTrue();
            t.check(names[i] + "the logo in Mojang red", red).isTrue();
            t.check(names[i] + "the bar (gray or green) is seen", near(barStart, new int[] {0x80, 0x80, 0x80})
                || near(barStart, new int[] {0x80, 0xFF, 0x80})).isTrue();
            waitLoaded(t);
            t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);
        }
        config().startupBackground = AppearanceConfig.StartupBackground.LIGHT;
        config().legacyStartupBar = false;
    }

    private static void messageScreens(TestContext t) {
        int scale = guiScale(t);
        String loadingWorld = I18n.get("menu.loadingLevel");
        String other = "A message 1.7.10 did not have";
        for (boolean on : new boolean[] {true, false}) {
            String name = on ? "on: " : "off: ";
            config().legacyMessageScreens = on;
            int[] opening = message(t, Component.translatable("selectWorld.data_read"), scale);
            int[] saving = message(t, Component.translatable("menu.savingLevel"), scale);
            int[] another = message(t, Component.literal(other), scale);
            if (on) {
                t.check(name + "opening a world: no box", opening[0]).near(0, 0);
                t.check(name + "opening a world: \"" + loadingWorld + "\" where 1.7.10 wrote it (width)", opening[1])
                    .near(textWidth(t, loadingWorld), 1);
                t.check(name + "saving: no box", saving[0]).near(0, 0);
                t.check(name + "saving: no text at all (1.7.10 showed only the background)", saving[2]).near(0, 0);
                t.check(name + "another message: no box, its own text (width)", another[0] == 0
                    && Math.abs(another[1] - textWidth(t, other)) <= 1).isTrue();
            } else {
                t.check(name + "opening a world: the 1.21.1 box", opening[0]).near(1, 0);
                t.check(name + "saving: the 1.21.1 box", saving[0]).near(1, 0);
                t.check(name + "\"" + I18n.get("menu.savingLevel") + "\" written in the middle", saving[2])
                    .near(textWidth(t, I18n.get("menu.savingLevel")), 1);
            }
        }
    }

    /**
     * Opens a message screen and reads it: [1 if the 1.21.1 box is there, width of the text in the 1.7.10 title row,
     * width of all text in the middle band]
     */
    private static int[] message(TestContext t, Component text, int scale) {
        t.openScreen(() -> new GenericMessageScreen(text));
        NativeImage frame = t.captureFrame();
        int middleY = frame.getHeight() / scale / 2;
        // the box is black, 12 pixels around the text in the middle
        boolean box = near(color(frame, frame.getWidth() / 2, (middleY - 4 - 6) * scale), new int[] {0, 0, 0});
        int titleRow = whiteWidth(frame, scale, middleY - 20, middleY - 13);
        int middle = whiteWidth(frame, scale, middleY - 4, middleY + 4);
        frame.close();
        closeScreen(t);
        return new int[] {box ? 1 : 0, titleRow, Math.max(titleRow, middle)};
    }

    /** The width the font draws a text with, without the space after the last letter */
    static int textWidth(TestContext t, String text) {
        return t.onClientThread(() -> t.mc().font.width(text)) - 1;
    }

    private static void settings(TestContext t) {
        CategoryMenuChecks.check(t, "appearance", "firstone-framework.appearance.", AppearanceFeature::getConfig,
            AppearanceConfig::new, "appearance.json");

        // time of a whole reload, off and on in turns (the fastest of each)
        long reloadOn = Long.MAX_VALUE;
        long reloadOff = Long.MAX_VALUE;
        for (int round = 0; round < 2; round++) {
            for (boolean on : new boolean[] {false, true}) {
                config().legacyStartupScreen = on;
                long started = reload(t);
                long took = waitLoaded(t) - started;
                t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);
                if (on) {
                    reloadOn = Math.min(reloadOn, took);
                } else {
                    reloadOff = Math.min(reloadOff, took);
                }
            }
        }
        t.note("ms from a reload to loaded, Legacy Startup Screen on", reloadOn);
        t.note("ms from a reload to loaded, off (1.21.1)", reloadOff);

        // drawing time of the loading screens, on and off in turns
        List<Supplier<Screen>> screens = List.of(
            () -> new LevelLoadingScreen(StoringChunkProgressListener.create(11)),
            () -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER),
            () -> new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        String[] names = {"world", "receiving", "message"};
        for (int i = 0; i < screens.size(); i++) {
            double on = Double.MAX_VALUE;
            double off = Double.MAX_VALUE;
            for (int round = 0; round < 3; round++) {
                config().legacyLoadingScreens = false;
                off = Math.min(off, drawNanos(t, screens.get(i)));
                config().legacyLoadingScreens = true;
                on = Math.min(on, drawNanos(t, screens.get(i)));
            }
            t.note("µs to draw the " + names[i] + " screen, on (1.7.10)", Math.round(on / 1000.0));
            t.note("µs to draw the " + names[i] + " screen, off (1.21.1)", Math.round(off / 1000.0));
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** Closes the open screen without asking it (a connecting screen would go back to its parent) */
    static void closeScreen(TestContext t) {
        t.onClientThread(() -> {
            t.mc().setScreen(null);
            return null;
        });
        t.ticks(2);
    }

    /** The GUI scale of the window */
    static int guiScale(TestContext t) {
        return t.onClientThread(() -> (int) t.mc().getWindow().getGuiScale());
    }

    /** The colors of the dirt texture as 1.7.10 draws it (darkened), by texel */
    static int[][] dirtTexels(TestContext t) {
        return t.onClientThread(() -> {
            try (InputStream input = t.mc().getResourceManager().open(DIRT);
                 NativeImage texture = NativeImage.read(input)) {
                int[][] texels = new int[texture.getWidth() * texture.getHeight()][];
                for (int y = 0; y < texture.getHeight(); y++) {
                    for (int x = 0; x < texture.getWidth(); x++) {
                        int pixel = texture.getPixelRGBA(x, y);
                        texels[y * texture.getWidth() + x] = new int[] {
                            (int) Math.round((pixel & 0xFF) * DIRT_SHADE),
                            (int) Math.round((pixel >> 8 & 0xFF) * DIRT_SHADE),
                            (int) Math.round((pixel >> 16 & 0xFF) * DIRT_SHADE)};
                    }
                }
                return texels;
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read the dirt texture", e);
            }
        });
    }

    /** The darkened dirt color at a GUI position (a 16-texel tile every 32 GUI pixels) */
    static int[] dirtAt(int[][] dirt, int guiX, int guiY) {
        int side = (int) Math.round(Math.sqrt(dirt.length));
        int u = Math.floorMod(guiX * side / 32, side);
        int v = Math.floorMod(guiY * side / 32, side);
        return dirt[v * side + u];
    }

    /** Whether three background spots away from any text show the darkened dirt where 1.7.10 put it */
    static boolean isDirt(TestContext t, NativeImage frame, int[][] dirt) {
        int scale = guiScale(t);
        int[][] spots = {{1, 1}, {17, 1}, {1, 17}};
        for (int[] spot : spots) {
            if (!pixelIs(frame, spot[0] * scale + scale / 2, spot[1] * scale + scale / 2,
                dirtAt(dirt, spot[0], spot[1]))) {
                return false;
            }
        }
        return true;
    }

    static boolean pixelIs(NativeImage frame, int x, int y, int[] expected) {
        return near(color(frame, x, y), expected);
    }

    /** A pixel of the frame as red, green, blue */
    static int[] color(NativeImage frame, int x, int y) {
        int pixel = frame.getPixelRGBA(Math.min(x, frame.getWidth() - 1), Math.min(y, frame.getHeight() - 1));
        return new int[] {pixel & 0xFF, pixel >> 8 & 0xFF, pixel >> 16 & 0xFF};
    }

    static boolean near(int[] color, int[] expected) {
        for (int i = 0; i < 3; i++) {
            if (Math.abs(color[i] - expected[i]) > COLOR_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /** Whether a band of GUI rows has white (text) pixels */
    static boolean hasWhite(NativeImage frame, int scale, int fromGuiY, int toGuiY) {
        return whiteWidth(frame, scale, fromGuiY, toGuiY) > 0;
    }

    /** Width in GUI pixels from the first to the last white pixel in a band of GUI rows, or 0 */
    static int whiteWidth(NativeImage frame, int scale, int fromGuiY, int toGuiY) {
        int first = -1;
        int last = -1;
        for (int y = fromGuiY * scale; y <= toGuiY * scale + scale - 1 && y < frame.getHeight(); y++) {
            for (int x = 0; x < frame.getWidth(); x++) {
                int[] c = color(frame, x, y);
                if (c[0] >= WHITE && c[1] >= WHITE && c[2] >= WHITE) {
                    first = first < 0 ? x : Math.min(first, x);
                    last = Math.max(last, x);
                }
            }
        }
        return first < 0 ? 0 : (last - first + 1) / scale;
    }

    /** Whether the middle of the frame has pixels of the game's logo in Mojang red */
    static boolean hasLogoRed(NativeImage frame) {
        int count = 0;
        int y = frame.getHeight() / 2;
        for (int x = 0; x < frame.getWidth(); x++) {
            count += near(color(frame, x, y), MOJANG_RED) ? 1 : 0;
        }
        return count > 10;
    }

    /** Starts loading the resources again, waits for the overlay and returns when it appeared */
    static long reload(TestContext t) {
        t.onClientThread(() -> {
            t.mc().reloadResourcePacks();
            return null;
        });
        t.waitUntil("the loading overlay shows", () -> t.onClientThread(
            () -> t.mc().getOverlay() instanceof LoadingOverlay), 100);
        return Util.getMillis();
    }

    /** Waits until loading is done and returns when it was */
    static long waitLoaded(TestContext t) {
        long[] done = {-1L};
        t.waitUntil("the resources are loaded", () -> t.onClientThread(() -> {
            if (t.mc().getOverlay() instanceof LoadingOverlayAccessor overlay) {
                done[0] = overlay.devtest$getFadeOutStart();
            }
            return done[0] > -1L;
        }), 1200);
        return done[0];
    }

    /** Waits until some time after a moment and captures the frame */
    static NativeImage frameAfter(TestContext t, long moment, long millis) {
        while (Util.getMillis() < moment + millis) {
            t.tick();
        }
        return t.captureFrame();
    }

    /** Fastest time of drawing a screen 200 times, per drawing, in nanoseconds */
    private static double drawNanos(TestContext t, Supplier<Screen> make) {
        t.openScreen(make);
        double nanos = t.onClientThread(() -> {
            Screen screen = t.mc().screen;
            GuiGraphics graphics = new GuiGraphics(t.mc(), t.mc().renderBuffers().bufferSource());
            for (int i = 0; i < 50; i++) {
                screen.render(graphics, 0, 0, 0.0F);
                graphics.flush();
            }
            long start = System.nanoTime();
            for (int i = 0; i < 200; i++) {
                screen.render(graphics, 0, 0, 0.0F);
                graphics.flush();
            }
            return (System.nanoTime() - start) / 200.0;
        });
        closeScreen(t);
        return nanos;
    }
}
