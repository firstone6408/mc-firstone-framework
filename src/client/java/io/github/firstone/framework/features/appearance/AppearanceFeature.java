package io.github.firstone.framework.features.appearance;

import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.config.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Feature that customizes the game's appearance (client only)
 *
 * <p>This feature only works on the client, so it lives in {@code src/client/java}
 * and is registered from {@link io.github.firstone.framework.client.FirstOneFrameworkClient}
 * instead of {@link io.github.firstone.framework.FirstOneFramework}</p>
 *
 * <p>Uses a Fabric event and a mixin:</p>
 * <ul>
 *   <li>{@code CLIENT_STARTED} — sets the game icon once the window is ready</li>
 *   <li>{@code WindowTitleMixin} — replaces the window title whenever Minecraft calls {@code Window.setTitle()}</li>
 * </ul>
 *
 * <p>Put icons (PNG) in {@code config/firstone-framework/appearance/icons/}
 * and select one from the config screen</p>
 */
public class AppearanceFeature implements Feature {

    private static final String CONFIG_FILE = "appearance.json";
    private static AppearanceConfig config = new AppearanceConfig();

    @Override
    public String getId() {
        return "appearance";
    }

    @Override
    public String getDisplayName() {
        return "Appearance";
    }

    @Override
    public String getDescription() {
        return "Customize game icon and window title (client only)";
    }

    /**
     * Initializes the Appearance feature on the client
     *
     * <p>Loads the config, creates the icons folder and registers the Fabric event
     * that applies the icon (the window title is handled by {@code WindowTitleMixin})</p>
     */
    @Override
    public void initializeClient() {
        config = ConfigManager.load(CONFIG_FILE, AppearanceConfig.class, new AppearanceConfig());

        try {
            Files.createDirectories(getIconsDir());
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to create icons directory: {}", e.getMessage());
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(AppearanceFeature::applyIcon);
    }

    /**
     * Sets the game icon from the selected config
     *
     * <p>If {@code selectedIcon} is empty or the file is not found, the icon is not changed</p>
     *
     * @param client Minecraft instance used to get the window handle
     */
    public static void applyIcon(Minecraft client) {
        String iconFile = config.selectedIcon;
        if (iconFile.isEmpty()) return;

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
        Path dir = getIconsDir();
        if (!Files.exists(dir)) return Collections.emptyList();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Returns the current Appearance config
     *
     * @return the current config, never null
     */
    public static AppearanceConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to its JSON file
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }
}
