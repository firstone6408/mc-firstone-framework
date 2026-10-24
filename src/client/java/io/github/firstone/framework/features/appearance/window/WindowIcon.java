package io.github.firstone.framework.features.appearance.window;

import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.Window;
import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The game window's icon: the PNG chosen from {@code config/firstone-framework/appearance/icons/}, or Minecraft's
 * own icon
 *
 * <p>Applied once the window is ready ({@code CLIENT_STARTED}, registered by {@link AppearanceFeature}) and when the
 * icon is changed on the settings screen. The window title is in {@code client/mixin/appearance/window/}.</p>
 */
public final class WindowIcon {

    private WindowIcon() {}

    /**
     * Sets the game icon from the selected config
     *
     * <p>If {@code selectedIcon} is empty, Minecraft's own icon is restored; if the file is not found, the icon is
     * not changed</p>
     *
     * @param client Minecraft instance used to get the window handle
     */
    public static void applyIcon(Minecraft client) {
        String iconFile = AppearanceFeature.getConfig().selectedIcon;
        if (iconFile.isEmpty()) {
            restoreDefaultIcon(client);
            return;
        }

        Path iconPath = getIconsDir().resolve(iconFile);
        if (!Files.exists(iconPath)) {
            FirstOneFramework.LOGGER.warn("Icon file not found: {}", iconPath);
            return;
        }

        try {
            setWindowIcon(client.getWindow(), iconPath);
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to set game icon: {}", e.getMessage());
        }
    }

    /**
     * Sets Minecraft's own window icon again, the same way Minecraft does at startup
     *
     * @param client Minecraft instance
     */
    private static void restoreDefaultIcon(Minecraft client) {
        try {
            client.getWindow().setIcon(client.getVanillaPackResources(),
                SharedConstants.getCurrentVersion().isStable() ? IconSet.RELEASE : IconSet.SNAPSHOT);
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to restore the default game icon: {}", e.getMessage());
        }
    }

    /**
     * Loads a PNG and sets it as the window icon through GLFW
     *
     * <p>Uses STBImage to decode and GLFWImage to pass the image to GLFW.
     * All memory is freed after GLFW has copied the data</p>
     *
     * @param window   window to set the icon on
     * @param iconPath path of the PNG file
     * @throws IOException if the file cannot be decoded
     */
    private static void setWindowIcon(Window window, Path iconPath) throws IOException {
        long handle = window.getWindow();
        byte[] bytes = Files.readAllBytes(iconPath);
        ByteBuffer fileBuffer = MemoryUtil.memAlloc(bytes.length);
        try {
            fileBuffer.put(bytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w        = stack.mallocInt(1);
                var h        = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(fileBuffer, w, h, channels, 4);
                if (pixels == null) {
                    throw new IOException("Cannot decode image: " + STBImage.stbi_failure_reason());
                }
                try {
                    try (GLFWImage.Buffer images = GLFWImage.malloc(1)) {
                        images.position(0).width(w.get(0)).height(h.get(0)).pixels(pixels);
                        GLFW.glfwSetWindowIcon(handle, images);
                    }
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } finally {
            MemoryUtil.memFree(fileBuffer);
        }
    }

    /**
     * Returns the path of the folder that holds the icon files
     *
     * @return path of {@code config/firstone-framework/appearance/icons/}
     */
    public static Path getIconsDir() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("firstone-framework/appearance/icons");
    }

    /**
     * Returns the names of all PNG files in the icons folder, sorted by name
     *
     * @return list of file names, or an empty list if none are found
     */
    public static List<String> getAvailableIcons() {
        return AppearanceFeature.pngFiles(getIconsDir());
    }

}
