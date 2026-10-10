package io.github.firstone.framework.devtest.core;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Reads what the operating system shows for the game window (its title and icon)
 *
 * <p>The game can set the title and icon but not read them back (LWJGL 3.3.3 has no {@code glfwGetWindowTitle}), so
 * they are read from the window system with {@code xprop}. This works on X11 (also XWayland) when {@code xprop} is
 * installed; elsewhere the result is empty and tests note that the check was skipped.</p>
 */
public final class WindowInfo {

    private WindowInfo() {}

    /**
     * Reads one X11 property of the game window, as {@code xprop} prints it
     *
     * @param minecraft the game
     * @param property  the property, e.g. {@code _NET_WM_NAME} or {@code _NET_WM_ICON}
     * @return the printed property, or empty when it cannot be read here
     */
    public static Optional<String> x11Property(Minecraft minecraft, String property) {
        long window;
        try {
            if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_X11) {
                return Optional.empty();
            }
            window = GLFWNativeX11.glfwGetX11Window(minecraft.getWindow().getWindow());
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
        if (window == 0) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder("xprop", "-id", Long.toString(window), property)
                .redirectErrorStream(true).start();
            // read first: a large property (icons) fills the pipe and xprop waits until it is read
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            return process.exitValue() == 0 ? Optional.of(output) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Reads the window title the window system shows
     *
     * @param minecraft the game
     * @return the title, or empty when it cannot be read here
     */
    public static Optional<String> title(Minecraft minecraft) {
        return x11Property(minecraft, "_NET_WM_NAME").map(output -> {
            int start = output.indexOf('"');
            int end = output.lastIndexOf('"');
            return start >= 0 && end > start ? output.substring(start + 1, end) : output;
        });
    }
}
