package io.github.firstone.framework.devtest.features.appearance;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.WindowInfo;
import io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenEdgeTests;
import io.github.firstone.framework.devtest.features.appearance.loading.LoadingScreenTests;
import io.github.firstone.framework.features.appearance.AppearanceConfig;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests of Appearance: window title, window icon and the quit confirmation
 *
 * <p>The title and icon are read back from the window system ({@link WindowInfo}); where that is not possible the
 * checks are noted as skipped. The quit confirmation is tested by asking the window to close, as the close button
 * does: with the option on the game must stay open. Its tests run last, as a failure would close the game.</p>
 */
public final class AppearanceTests implements FeatureTests {

    /** File name of the test icon in the icons folder */
    private static final String ICON_FILE = "devtest_icon.png";

    @Override
    public String id() {
        return "appearance";
    }

    @Override
    public List<Object> configs() {
        return List.of(AppearanceFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        List<TestCase> tests = new ArrayList<>(List.of(
            TestCase.of("Window title: a custom title is shown; empty: the game's title", this::windowTitle),
            TestCase.of("Window icon: an icon from the icons folder is shown; empty or missing: the game's icon",
                this::windowIcon),
            TestCase.of("Confirm Quit off: closing the window is not taken over", this::confirmQuitOff),
            TestCase.of("Confirm Quit: closing the window opens the dialog over the game; Escape cancels",
                this::confirmQuitEscape),
            TestCase.of("Confirm Quit: Enter and Space also cancel (Cancel is the default answer)",
                this::confirmQuitEnterSpace)
        ));
        // parts with their own test classes, in the folders of the feature's parts
        tests.addAll(LoadingScreenTests.tests());
        tests.addAll(LoadingScreenEdgeTests.tests());
        return tests;
    }

    private static AppearanceConfig config() {
        return AppearanceFeature.getConfig();
    }

    // ---- title and icon -------------------------------------------------------------------------------------------

    /** Lets the game set its title again and returns what the window system shows */
    private static Optional<String> updatedTitle(TestContext t) {
        t.onClientThread(() -> {
            t.mc().updateTitle();
            return null;
        });
        t.ticks(5);
        return WindowInfo.title(t.mc());
    }

    private void windowTitle(TestContext t) {
        config().windowTitle = "FirstOne DevTest Window";
        Optional<String> custom = updatedTitle(t);
        if (custom.isEmpty()) {
            t.note("window title", "not checked: the window system cannot be read here (needs X11 and xprop)");
            return;
        }
        t.check("custom title", custom.get()).isEqualTo("FirstOne DevTest Window");
        config().windowTitle = "";
        String vanilla = updatedTitle(t).orElse("");
        t.note("the game's title", vanilla);
        t.check("empty: the game's title starts with Minecraft", vanilla.startsWith("Minecraft")).isTrue();
        t.check("empty: the game's title names 1.21.1", vanilla.contains("1.21.1")).isTrue();
    }

    /** Applies the configured icon and returns the window's icon sizes as the window system lists them */
    private static Optional<String> appliedIcon(TestContext t) {
        t.onClientThread(() -> {
            AppearanceInternals.applyIcon(t.mc());
            return null;
        });
        t.ticks(5);
        return WindowInfo.x11Property(t.mc(), "_NET_WM_ICON");
    }

    private void windowIcon(TestContext t) throws Exception {
        Path icon = AppearanceInternals.iconsDir().resolve(ICON_FILE);
        Files.createDirectories(icon.getParent());
        try (NativeImage image = new NativeImage(16, 16, false)) {
            image.fillRect(0, 0, 16, 16, 0xFF3060E0);
            image.writeToFile(icon);
        }
        try {
            config().selectedIcon = ICON_FILE;
            Optional<String> custom = appliedIcon(t);
            if (custom.isEmpty()) {
                t.note("window icon", "not checked: the window system cannot be read here (needs X11 and xprop)");
                return;
            }
            t.check("custom icon: a 16 x 16 icon", custom.get().contains("Icon (16 x 16)")).isTrue();
            t.check("custom icon: only that one (no 32 x 32)", custom.get().contains("Icon (32 x 32)")).isFalse();
            config().selectedIcon = "";
            String vanilla = appliedIcon(t).orElse("");
            t.check("empty: the game's icon (has 32 x 32)", vanilla.contains("Icon (32 x 32)")).isTrue();
            config().selectedIcon = "devtest_missing.png";
            String missing = appliedIcon(t).orElse("");
            t.check("missing file: icon unchanged", missing.equals(vanilla)).isTrue();
        } finally {
            config().selectedIcon = "";
            appliedIcon(t);
            Files.deleteIfExists(icon);
        }
    }

    // ---- quit confirmation ----------------------------------------------------------------------------------------

    /** Asks the window to close, as its close button does */
    private static void requestClose(TestContext t) {
        t.onClientThread(() -> {
            GLFW.glfwSetWindowShouldClose(t.mc().getWindow().getWindow(), true);
            return null;
        });
        t.ticks(3);
    }

    private static boolean windowShouldClose(TestContext t) {
        return t.onClientThread(() -> GLFW.glfwWindowShouldClose(t.mc().getWindow().getWindow()));
    }

    private void confirmQuitOff(TestContext t) {
        config().confirmQuit = false;
        boolean takenOver = t.onClientThread(() -> AppearanceInternals.interceptClose(t.mc()));
        t.check("close request taken over", takenOver).isFalse();
        t.check("dialog open", AppearanceInternals.quitDialogOpen()).isFalse();
    }

    private void confirmQuitEscape(TestContext t) {
        t.check("no screen before", t.mc().screen == null).isTrue();
        requestClose(t);
        t.check("dialog open", AppearanceInternals.quitDialogOpen()).isTrue();
        t.check("the window stays open (close request undone)", windowShouldClose(t)).isFalse();
        t.check("a screen holds the dialog over the game", t.mc().screen != null).isTrue();
        t.saveScreenshot("quit_dialog_in_game");
        t.pressKey(GLFW.GLFW_KEY_ESCAPE);
        t.ticks(2);
        t.check("Escape: dialog closed", AppearanceInternals.quitDialogOpen()).isFalse();
        t.check("Escape: back to the game (no screen)", t.mc().screen == null).isTrue();
    }

    private void confirmQuitEnterSpace(TestContext t) {
        for (int key : new int[] {GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE}) {
            String name = key == GLFW.GLFW_KEY_ENTER ? "Enter" : "Space";
            requestClose(t);
            t.check(name + ": dialog open", AppearanceInternals.quitDialogOpen()).isTrue();
            t.pressKey(key);
            t.ticks(2);
            t.check(name + ": dialog closed (Cancel)", AppearanceInternals.quitDialogOpen()).isFalse();
            t.check(name + ": the window stays open", windowShouldClose(t)).isFalse();
        }
    }
}
