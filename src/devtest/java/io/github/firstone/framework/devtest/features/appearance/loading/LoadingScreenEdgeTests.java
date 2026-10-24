package io.github.firstone.framework.devtest.features.appearance.loading;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestResourcePack;
import io.github.firstone.framework.features.appearance.AppearanceConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.MOJANG_RED;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.closeScreen;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.color;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.config;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.dirtTexels;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.frameAfter;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.guiScale;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.hasLogoRed;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.hasWhite;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.isDirt;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.near;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.reload;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.textWidth;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.waitLoaded;
import static io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests.whiteWidth;

/**
 * Odd cases of the 1.7.10 startup and loading screens, part of the Appearance tests: extreme GUI scales, odd logo
 * files, the game's monochrome logo option, each option on its own, values past their range, reloads on top of each
 * other and options changed while loading, and a resource pack's dirt
 *
 * <p>Read from the frame on the screen like {@link LoadingScreenTests}, whose helpers they share.</p>
 */
public final class LoadingScreenEdgeTests {

    /** Colors as {@code NativeImage} stores them (alpha, blue, green, red) */
    private static final int BLUE_ABGR = 0xFFFF0000;
    private static final int GREEN_ABGR = 0xFF00FF00;

    /** The same colors as red, green, blue */
    private static final int[] BLUE = {0, 0, 255};
    private static final int[] GREEN = {0, 255, 0};
    private static final int[] WHITE_RGB = {255, 255, 255};
    private static final int[] BLACK_RGB = {0, 0, 0};

    /** Colors of the 1.7.10 bar */
    private static final int[] BAR_GRAY = {0x80, 0x80, 0x80};
    private static final int[] BAR_GREEN = {0x80, 0xFF, 0x80};

    private LoadingScreenEdgeTests() {}

    /**
     * Returns the odd case tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Odd cases: GUI scale 1 and 4: dirt, \"Loading world\" and a logo from the folder that fits a "
                + "screen lower than 256", LoadingScreenEdgeTests::guiScales),
            TestCase.of("Odd cases: logos tall, of 1 pixel, half transparent, broken, rewritten while chosen",
                LoadingScreenEdgeTests::logoShapes),
            TestCase.of("Odd cases: the game's monochrome logo option: black on Light, white on Dark",
                LoadingScreenEdgeTests::monochrome),
            TestCase.of("Odd cases: every option on its own (bar or logo without the startup screen, loading bar or "
                + "message screens without the loading screens and back)", LoadingScreenEdgeTests::onTheirOwn),
            TestCase.of("Odd cases: progress past 100, a message wider than the screen, an empty message, the same "
                + "narration", LoadingScreenEdgeTests::edgeValues),
            TestCase.of("Odd cases: two reloads at once, and the options changed while loading",
                LoadingScreenEdgeTests::busyReloads),
            TestCase.of("Odd cases: the dirt of a resource pack is the dirt of the loading screens",
                LoadingScreenEdgeTests::packDirt),
            TestCase.of("All off: every startup and loading screen is 1.21.1's, a chosen logo is ignored, and no "
                + "logos folder or config change is made", LoadingScreenEdgeTests::allOff));
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private static void guiScales(TestContext t) throws IOException {
        int saved = t.onClientThread(() -> t.mc().options.guiScale().get());
        Path file = logo("devtest_scale_logo.png", 64, 64, BLUE_ABGR);
        int[][] dirt = dirtTexels(t);
        try {
            for (int setting : new int[] {1, 4}) {
                setGuiScale(t, setting);
                int scale = guiScale(t);
                String name = "GUI scale " + scale + ": ";
                config().legacyLoadingScreens = true;
                t.openScreen(() -> new LevelLoadingScreen(StoringChunkProgressListener.create(1)));
                NativeImage frame = t.captureFrame();
                int middleY = frame.getHeight() / scale / 2;
                boolean title = hasWhite(frame, scale, middleY - 20, middleY - 13);
                boolean dirtShown = isDirt(t, frame, dirt);
                frame.close();
                closeScreen(t);
                t.check(name + "dirt where 1.7.10 put it", dirtShown).isTrue();
                t.check(name + "\"Loading world\" where 1.7.10 wrote it", title).isTrue();

                config().legacyStartupScreen = true;
                config().startupLogo = file.getFileName().toString();
                frame = reloadFrame(t);
                int size = Math.min(256, Math.min(frame.getWidth() / scale, frame.getHeight() / scale));
                int blue = countRow(frame, frame.getHeight() / 2, BLUE);
                frame.close();
                finishReload(t);
                t.check(name + "the logo is " + size + " wide, inside the screen (screen pixels)", blue)
                    .near(size * scale, scale);
            }
        } finally {
            setGuiScale(t, saved);
            Files.deleteIfExists(file);
        }
    }

    private static void logoShapes(TestContext t) throws IOException {
        config().legacyStartupScreen = true;
        Path tall = logo("devtest_tall.png", 32, 64, BLUE_ABGR);
        Path tiny = logo("devtest_tiny.png", 1, 1, BLUE_ABGR);
        Path half = logo("devtest_half.png", 64, 64, BLUE_ABGR);
        Path broken = logosDir().resolve("devtest_broken.png");
        try {
            try (InputStream input = Files.newInputStream(half); NativeImage image = NativeImage.read(input)) {
                image.fillRect(0, 0, 32, 64, 0);
                image.writeToFile(half);
            }
            Files.writeString(broken, "not a picture");
            int scale = guiScale(t);

            config().startupLogo = tall.getFileName().toString();
            NativeImage frame = reloadFrame(t);
            int size = Math.min(256, Math.min(frame.getWidth() / scale, frame.getHeight() / scale));
            int high = countColumn(frame, frame.getWidth() / 2, BLUE);
            int wide = countRow(frame, frame.getHeight() / 2, BLUE);
            frame.close();
            finishReload(t);
            t.check("tall logo: as high as the logo size (screen pixels)", high).near(size * scale, scale);
            t.check("tall logo: half as wide, its shape kept", wide).near(size * scale / 2.0, scale);

            config().startupLogo = tiny.getFileName().toString();
            frame = reloadFrame(t);
            int square = countRow(frame, frame.getHeight() / 2, BLUE);
            frame.close();
            finishReload(t);
            t.check("a logo of 1 pixel fills the logo size", square).near(size * scale, scale);

            config().startupLogo = half.getFileName().toString();
            frame = reloadFrame(t);
            int[] left = color(frame, frame.getWidth() / 2 - size * scale / 4, frame.getHeight() / 2);
            int[] right = color(frame, frame.getWidth() / 2 + size * scale / 4, frame.getHeight() / 2);
            frame.close();
            finishReload(t);
            t.check("the transparent half shows the background", near(left, WHITE_RGB)).isTrue();
            t.check("the other half is drawn", near(right, BLUE)).isTrue();

            config().startupLogo = broken.getFileName().toString();
            frame = reloadFrame(t);
            boolean red = hasLogoRed(frame);
            frame.close();
            finishReload(t);
            t.check("a broken file: the game's logo", red).isTrue();

            // the chosen file is read once: rewritten, it shows again only when chosen again
            config().startupLogo = tiny.getFileName().toString();
            frame = reloadFrame(t);
            frame.close();
            finishReload(t);
            logo("devtest_tiny.png", 1, 1, GREEN_ABGR);
            frame = reloadFrame(t);
            boolean stillBlue = near(color(frame, frame.getWidth() / 2, frame.getHeight() / 2), BLUE);
            frame.close();
            finishReload(t);
            config().startupLogo = tall.getFileName().toString();
            reloadFrame(t).close();
            finishReload(t);
            config().startupLogo = tiny.getFileName().toString();
            frame = reloadFrame(t);
            boolean nowGreen = near(color(frame, frame.getWidth() / 2, frame.getHeight() / 2), GREEN);
            frame.close();
            finishReload(t);
            t.check("rewritten while chosen: the picture read before stays", stillBlue).isTrue();
            t.check("chosen again: the new picture", nowGreen).isTrue();
        } finally {
            for (Path file : new Path[] {tall, tiny, half, broken}) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static void monochrome(TestContext t) {
        boolean saved = t.onClientThread(() -> t.mc().options.darkMojangStudiosBackground().get());
        try {
            t.onClientThread(() -> {
                t.mc().options.darkMojangStudiosBackground().set(true);
                return null;
            });
            config().legacyStartupScreen = true;
            AppearanceConfig.StartupBackground[] backgrounds = AppearanceConfig.StartupBackground.values();
            int[][] logoColors = {BLACK_RGB, WHITE_RGB};
            int[][] backgroundColors = {WHITE_RGB, BLACK_RGB};
            for (int i = 0; i < backgrounds.length; i++) {
                config().startupBackground = backgrounds[i];
                NativeImage frame = reloadFrame(t);
                int logo = countRow(frame, frame.getHeight() / 2, logoColors[i]);
                int[] corner = color(frame, 2, 2);
                frame.close();
                finishReload(t);
                String name = backgrounds[i].name() + ": ";
                t.check(name + "background", near(corner, backgroundColors[i])).isTrue();
                t.check(name + "the monochrome logo is seen (its pixels in the middle row)", logo).atLeast(10);
            }
        } finally {
            t.onClientThread(() -> {
                t.mc().options.darkMojangStudiosBackground().set(saved);
                return null;
            });
        }
    }

    private static void onTheirOwn(TestContext t) throws IOException {
        Path file = logo("devtest_alone_logo.png", 64, 64, BLUE_ABGR);
        int[][] dirt = dirtTexels(t);
        int scale = guiScale(t);
        try {
            config().legacyStartupScreen = false;
            config().legacyStartupBar = true;
            config().startupLogo = file.getFileName().toString();
            NativeImage frame = reloadFrame(t);
            int[] corner = color(frame, 2, 2);
            int barTop = (int) (frame.getHeight() / scale * 0.8325);
            int[] barStart = color(frame, (frame.getWidth() / scale / 2 - 50) * scale, barTop * scale);
            int blue = countRow(frame, frame.getHeight() / 2, BLUE);
            frame.close();
            finishReload(t);
            t.check("startup screen off: the 1.21.1 red, though the bar and a logo are chosen",
                near(corner, MOJANG_RED)).isTrue();
            t.check("startup screen off: no 1.7.10 bar", near(barStart, BAR_GRAY) || near(barStart, BAR_GREEN))
                .isFalse();
            t.check("startup screen off: no logo from the folder", blue).near(0, 0);
            config().legacyStartupBar = false;
            config().startupLogo = "";

            config().legacyLoadingScreens = false;
            config().legacyLoadingBar = true;
            t.openScreen(() -> new LevelLoadingScreen(StoringChunkProgressListener.create(1)));
            frame = t.captureFrame();
            int middleX = frame.getWidth() / scale / 2;
            int middleY = frame.getHeight() / scale / 2;
            int[] bar = color(frame, (middleX - 50) * scale, (middleY + 16) * scale);
            boolean worldDirt = isDirt(t, frame, dirt);
            frame.close();
            closeScreen(t);
            t.check("loading screens off: no dirt, though the loading bar is on", worldDirt).isFalse();
            t.check("loading screens off: no 1.7.10 bar", near(bar, BAR_GRAY) || near(bar, BAR_GREEN)).isFalse();
            config().legacyLoadingBar = false;

            config().legacyMessageScreens = true;
            int[] alone = messageLook(t, dirt);
            t.check("message screens on, loading screens off: 1.21.1 background, no box, \"Loading world\"",
                alone[0] == 0 && alone[1] == 0 && Math.abs(alone[2] - textWidth(t, I18n.get("menu.loadingLevel")))
                    <= 1).isTrue();
            config().legacyLoadingScreens = true;
            config().legacyMessageScreens = false;
            int[] boxed = messageLook(t, dirt);
            t.check("loading screens on, message screens off: dirt and the 1.21.1 box",
                boxed[0] == 1 && boxed[1] == 1).isTrue();
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void edgeValues(TestContext t) {
        int scale = guiScale(t);
        StoringChunkProgressListener listener = StoringChunkProgressListener.create(1);
        listener.start();
        listener.updateSpawnPos(new ChunkPos(0, 0));
        // 12 chunks reported done of the 9 counted: more than 100 %
        for (int i = 0; i < 12; i++) {
            listener.onStatusChange(new ChunkPos(i, i), ChunkStatus.FULL);
        }
        config().legacyLoadingScreens = true;
        config().legacyLoadingBar = true;
        t.openScreen(() -> new LevelLoadingScreen(listener));
        NativeImage frame = t.captureFrame();
        int middleX = frame.getWidth() / scale / 2;
        int barTop = (frame.getHeight() / scale / 2 + 16) * scale;
        int green = 0;
        for (int x = -10; x < 110; x++) {
            green += near(color(frame, (middleX - 50 + x) * scale, barTop), BAR_GREEN) ? 1 : 0;
        }
        frame.close();
        closeScreen(t);
        t.check(listener.getProgress() + " %: the bar is full and no longer than 100", green).near(100, 0);
        config().legacyLoadingBar = false;

        config().legacyMessageScreens = true;
        String longText = "A very long message ".repeat(20).trim();
        t.openScreen(() -> new GenericMessageScreen(Component.literal(longText)));
        frame = t.captureFrame();
        int middleY = frame.getHeight() / scale / 2;
        int firstLine = whiteWidth(frame, scale, middleY - 20, middleY - 13);
        boolean secondLine = hasWhite(frame, scale, middleY - 11, middleY - 4);
        int edge = 0;
        for (int y = (middleY - 20) * scale; y < (middleY + 20) * scale; y++) {
            for (int x : new int[] {0, frame.getWidth() - 1}) {
                int[] c = color(frame, x, y);
                edge += c[0] >= 230 && c[1] >= 230 && c[2] >= 230 ? 1 : 0;
            }
        }
        int screenWidth = frame.getWidth() / scale;
        frame.close();
        closeScreen(t);
        t.check("a long message: its first line fits the screen with a margin", firstLine)
            .atMost(screenWidth - 40);
        t.check("a long message: it goes on the next line", secondLine).isTrue();
        t.check("a long message: nothing written at the screen's edges", edge).near(0, 0);

        t.openScreen(() -> new GenericMessageScreen(Component.empty()));
        frame = t.captureFrame();
        boolean anything = hasWhite(frame, scale, middleY - 24, middleY + 24);
        frame.close();
        closeScreen(t);
        t.check("an empty message: nothing written", anything).isFalse();

        String[] narrated = new String[2];
        for (int i = 0; i < 2; i++) {
            config().legacyMessageScreens = i == 0;
            t.openScreen(() -> new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            narrated[i] = t.onClientThread(() -> t.mc().screen.getNarrationMessage().getString());
            closeScreen(t);
        }
        t.check("the narrator says the same with and without the option (" + narrated[1] + ")",
            narrated[0].equals(narrated[1])).isTrue();
    }

    private static void busyReloads(TestContext t) {
        config().legacyStartupScreen = true;
        t.onClientThread(() -> {
            t.mc().reloadResourcePacks();
            t.mc().reloadResourcePacks();
            return null;
        });
        t.waitUntil("both reloads end", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 1200);
        t.check("two reloads at once: the overlay closes, the game goes on", t.onClientThread(
            () -> t.mc().getOverlay() == null)).isTrue();

        long started = reload(t);
        frameAfter(t, started, 300L).close();
        config().legacyStartupScreen = false;
        config().startupBackground = AppearanceConfig.StartupBackground.DARK;
        NativeImage frame = frameAfter(t, started, 900L);
        boolean redNow = near(color(frame, 2, 2), MOJANG_RED);
        frame.close();
        config().legacyStartupScreen = true;
        finishReload(t);
        config().startupBackground = AppearanceConfig.StartupBackground.LIGHT;
        t.check("turned off while loading: the 1.21.1 screen from the next frame", redNow).isTrue();
        t.check("options changed while loading: the overlay still closes", t.onClientThread(
            () -> t.mc().getOverlay() == null)).isTrue();
    }

    private static void packDirt(TestContext t) throws IOException {
        // a dirt of one color: red 0xC0, green 0x40, blue 0x20
        TestResourcePack pack = t.resourcePack("dirt").image("assets/minecraft/textures/block/dirt.png", 16, 16,
            0xFF2040C0);
        pack.enable();
        config().legacyLoadingScreens = true;
        t.openScreen(() -> new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        NativeImage frame = t.captureFrame();
        int[] corner = color(frame, 2, 2);
        frame.close();
        closeScreen(t);
        t.check("the pack's dirt, darkened like 1.7.10 (" + corner[0] + ", " + corner[1] + ", " + corner[2] + ")",
            near(corner, new int[] {0xC0 / 4, 0x40 / 4, 0x20 / 4})).isTrue();
    }

    private static void allOff(TestContext t) throws IOException {
        Path folder = logosDir();
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/appearance.json");
        deleteFolder(folder);
        long configTime = Files.exists(configFile) ? Files.getLastModifiedTime(configFile).toMillis() : -1L;
        config().legacyStartupScreen = false;
        config().legacyStartupBar = true;
        config().startupLogo = "devtest_ignored.png";
        config().startupBackground = AppearanceConfig.StartupBackground.DARK;
        config().legacyLoadingScreens = false;
        config().legacyLoadingBar = true;
        config().legacyMessageScreens = false;
        int scale = guiScale(t);
        int[][] dirt = dirtTexels(t);

        NativeImage frame = reloadFrame(t);
        int[] corner = color(frame, 2, 2);
        int barTop = (int) (frame.getHeight() / scale * 0.8325);
        int[] barStart = color(frame, (frame.getWidth() / scale / 2 - 50) * scale, barTop * scale);
        frame.close();
        finishReload(t);
        t.check("startup screen: the 1.21.1 red", near(corner, MOJANG_RED)).isTrue();
        t.check("startup screen: no 1.7.10 bar", near(barStart, BAR_GRAY) || near(barStart, BAR_GREEN)).isFalse();

        t.openScreen(() -> new LevelLoadingScreen(StoringChunkProgressListener.create(1)));
        frame = t.captureFrame();
        int middleX = frame.getWidth() / scale / 2;
        int middleY = frame.getHeight() / scale / 2;
        boolean worldDirt = isDirt(t, frame, dirt);
        boolean building = hasWhite(frame, scale, middleY + 4, middleY + 11);
        int[] bar = color(frame, (middleX - 50) * scale, (middleY + 16) * scale);
        frame.close();
        closeScreen(t);
        t.check("world loading: no dirt, no \"Building terrain\", no bar (the chunk map)",
            !worldDirt && !building && !near(bar, BAR_GRAY) && !near(bar, BAR_GREEN)).isTrue();

        t.openScreen(() -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER));
        frame = t.captureFrame();
        int receiving = whiteWidth(frame, scale, middleY - 50, middleY - 43);
        boolean receivingDirt = isDirt(t, frame, dirt);
        frame.close();
        closeScreen(t);
        t.check("receiving: \"" + I18n.get("multiplayer.downloadingTerrain") + "\" without dirt",
            !receivingDirt && Math.abs(receiving - textWidth(t, I18n.get("multiplayer.downloadingTerrain"))) <= 1)
            .isTrue();

        int[] message = messageLook(t, dirt);
        t.check("message screen: the 1.21.1 box, no dirt", message[0] == 0 && message[1] == 1).isTrue();

        long configAfter = Files.exists(configFile) ? Files.getLastModifiedTime(configFile).toMillis() : -1L;
        t.check("no logos folder made", Files.exists(folder)).isFalse();
        t.check("the config file not written", configAfter).near(configTime, 0);
    }

    /** Deletes a folder and its files */
    private static void deleteFolder(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (var files = Files.list(folder)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(folder);
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static Path logosDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/appearance/logos");
    }

    /** Writes a logo of one color in the logos folder and returns its path */
    private static Path logo(String name, int width, int height, int abgr) throws IOException {
        Path file = logosDir().resolve(name);
        Files.createDirectories(file.getParent());
        try (NativeImage image = new NativeImage(width, height, false)) {
            image.fillRect(0, 0, width, height, abgr);
            image.writeToFile(file);
        }
        return file;
    }

    private static void setGuiScale(TestContext t, int scale) {
        t.onClientThread(() -> {
            t.mc().options.guiScale().set(scale);
            t.mc().resizeDisplay();
            return null;
        });
        t.ticks(3);
    }

    /** Reloads the resources and captures the startup screen once it shows fully */
    private static NativeImage reloadFrame(TestContext t) {
        long started = reload(t);
        return frameAfter(t, started, 700L);
    }

    /** Waits until the reload is done and the overlay is gone */
    private static void finishReload(TestContext t) {
        waitLoaded(t);
        t.waitUntil("the overlay closes", () -> t.onClientThread(() -> t.mc().getOverlay() == null), 200);
    }

    /** Pixels of one color in a row of the frame */
    private static int countRow(NativeImage frame, int y, int[] expected) {
        int count = 0;
        for (int x = 0; x < frame.getWidth(); x++) {
            count += near(color(frame, x, y), expected) ? 1 : 0;
        }
        return count;
    }

    /** Pixels of one color in a column of the frame */
    private static int countColumn(NativeImage frame, int x, int[] expected) {
        int count = 0;
        for (int y = 0; y < frame.getHeight(); y++) {
            count += near(color(frame, x, y), expected) ? 1 : 0;
        }
        return count;
    }

    /** Opens the "Reading world data" screen and reads it: [1 if dirt, 1 if the box, width of the 1.7.10 title] */
    private static int[] messageLook(TestContext t, int[][] dirt) {
        int scale = guiScale(t);
        t.openScreen(() -> new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        NativeImage frame = t.captureFrame();
        int middleY = frame.getHeight() / scale / 2;
        boolean dirtShown = isDirt(t, frame, dirt);
        boolean box = near(color(frame, frame.getWidth() / 2, (middleY - 4 - 6) * scale), BLACK_RGB);
        int title = whiteWidth(frame, scale, middleY - 20, middleY - 13);
        frame.close();
        closeScreen(t);
        return new int[] {dirtShown ? 1 : 0, box ? 1 : 0, title};
    }
}
