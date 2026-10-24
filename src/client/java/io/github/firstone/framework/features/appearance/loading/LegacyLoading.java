package io.github.firstone.framework.features.appearance.loading;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.features.appearance.AppearanceConfig;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * The loading screens of Minecraft 1.7.10: the startup screen and the dark dirt background with its texts
 *
 * <p>Only what is drawn changes; when a screen opens and closes, what the game loads meanwhile and what the narrator
 * says stay 1.21.1's. The mixins of {@code client/mixin/appearance/loading/} call this class:</p>
 * <ul>
 *   <li><b>Legacy Startup Screen</b> ({@code Minecraft.loadScreen} of 1.7.10): white (or black with Startup
 *       Background on Dark), the logo in the middle, no
 *       progress bar, no fading; also while the game loads its resources again (resource packs, F3+T), where 1.7.10
 *       simply froze. The logo is the game's own (Mojang Studios, drawn in its red on the white) or a PNG the player
 *       puts in {@code config/firstone-framework/appearance/logos/}, drawn at most 256 wide like the 1.7.10 logo. The
 *       overlay still decides when loading is done; at the moment 1.21.1 starts fading out, the screen behind shows
 *       at once, as 1.7.10 switched without fading.</li>
 *   <li><b>Legacy Loading Screens</b> ({@code LoadingScreenRenderer}, {@code GuiDownloadTerrain} and
 *       {@code GuiConnecting} of 1.7.10): the dirt texture, tiled every 32 pixels and darkened to 0x404040, behind
 *       the loading screens; "Loading world" and "Building terrain" where 1.21.1 draws the chunk map, "Downloading
 *       terrain" where it says "Loading terrain...". The dirt is the game's block texture, so a resource pack (like
 *       Programmer Art) changes it as it changes the block.</li>
 *   <li><b>Legacy Loading Bar</b>: the 1.7.10 progress bar (gray, filled in green, 100 by 2) under the texts, with
 *       the world's real progress; <b>Startup Bar</b>: the same bar on the startup screen, where 1.21.1 has its bar,
 *       with the progress of the loading resources.</li>
 *   <li><b>Legacy Message Screens</b>: 1.21.1's message screens without their box; "Loading world" (1.7.10's
 *       title) while a world opens or is made, nothing while it is saved on leaving (1.7.10 showed only the
 *       background), other messages as they are.</li>
 * </ul>
 */
public final class LegacyLoading {

    /** The dirt texture of the background (1.7.10 used the same picture as {@code options_background.png}) */
    private static final ResourceLocation DIRT = ResourceLocation.withDefaultNamespace("textures/block/dirt.png");

    /** How dark the dirt is drawn: 1.7.10 colored it 0x404040 */
    private static final float DIRT_SHADE = 0x40 / 255.0F;

    /** Size of one dirt tile on the screen */
    private static final int DIRT_TILE = 32;

    /** Color of the texts */
    private static final int WHITE = 0xFFFFFF;

    /** Messages of 1.21.1 shown while a world opens or is made, which 1.7.10 showed as "Loading world" */
    private static final Set<String> WORLD_OPENING = Set.of("selectWorld.data_read", "selectWorld.resource_load",
        "createWorld.preparing");

    /** The message of 1.21.1 shown while the world is saved on leaving, where 1.7.10 showed nothing */
    private static final String SAVING = "menu.savingLevel";

    /** Space kept free on each side of a message wider than the screen */
    private static final int MESSAGE_MARGIN = 20;

    /** Height of the startup bar from the top, as a share of the screen (where 1.21.1 puts its bar) */
    private static final double STARTUP_BAR_HEIGHT = 0.8325;

    /** Size of the 1.7.10 progress bar and its colors (gray, and green for the part done) */
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 2;
    private static final int BAR_COLOR = 0xFF808080;
    private static final int BAR_DONE_COLOR = 0xFF80FF80;

    /** Backgrounds of the startup screen: white (1.7.10) or black */
    private static final int STARTUP_LIGHT = 0xFFFFFFFF;
    private static final int STARTUP_DARK = 0xFF000000;

    /** Colors of the game's logo on the white screen: Mojang red, or black with the monochrome logo option */
    private static final float[] LOGO_RED = {239 / 255.0F, 50 / 255.0F, 61 / 255.0F};
    private static final float[] LOGO_BLACK = {0.0F, 0.0F, 0.0F};
    private static final float[] LOGO_WHITE = {1.0F, 1.0F, 1.0F};

    /** Largest size of a logo from the logos folder (the 1.7.10 logo was 256 by 256) */
    private static final int CUSTOM_LOGO_SIZE = 256;

    /** Largest width or height of a logo file that is read (a bigger picture only costs memory) */
    private static final int MAX_LOGO_FILE_SIZE = 2048;

    /** How long 1.21.1 keeps the overlay after loading before it fades it out, in milliseconds */
    private static final long FADE_OUT_START = 1000L;

    /** Where the logo from the logos folder is kept as a texture */
    private static final ResourceLocation CUSTOM_LOGO =
        ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "appearance/startup_logo");

    /** The file of the logo loaded as {@link #CUSTOM_LOGO}, or null if none is loaded */
    @Nullable
    private static String loadedLogo;

    /** Size of the loaded logo, or 0 when it could not be read */
    private static int logoWidth;
    private static int logoHeight;

    private LegacyLoading() {}

    private static AppearanceConfig config() {
        return AppearanceFeature.getConfig();
    }

    /**
     * Returns the folder of the logos the player can choose from
     *
     * @return {@code config/firstone-framework/appearance/logos/}
     */
    public static Path getLogosDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/appearance/logos");
    }

    /**
     * Opens the logos folder in the system's file manager, making it first if it is not there yet
     *
     * <p>Called by the Logos Folder button of the settings. The folder is only made here, so a game that never uses a
     * logo of its own has no such folder.</p>
     */
    public static void openLogosDir() {
        try {
            Files.createDirectories(getLogosDir());
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to create the logos directory: {}", e.getMessage());
        }
        Util.getPlatform().openPath(getLogosDir());
    }

    /**
     * Returns the PNG files of the logos folder
     *
     * @return the file names, sorted, or an empty list
     */
    public static List<String> getAvailableLogos() {
        return AppearanceFeature.pngFiles(getLogosDir());
    }

    // ---- loading screens ------------------------------------------------------------------------------------------

    /**
     * Tells whether a screen gets the 1.7.10 dirt background
     *
     * @param screen the screen being drawn
     * @return true for a loading screen while Legacy Loading Screens is on
     */
    public static boolean dirtBackground(Screen screen) {
        return config().legacyLoadingScreens && (screen instanceof GenericMessageScreen
            || screen instanceof LevelLoadingScreen || screen instanceof ReceivingLevelScreen
            || screen instanceof ConnectScreen || screen instanceof ProgressScreen);
    }

    /**
     * Draws the 1.7.10 dirt background over the whole screen ({@code GuiScreen.drawBackground} of 1.7.10)
     *
     * @param graphics where to draw
     * @param width    width of the screen
     * @param height   height of the screen
     */
    public static void renderDirt(GuiGraphics graphics, int width, int height) {
        graphics.setColor(DIRT_SHADE, DIRT_SHADE, DIRT_SHADE, 1.0F);
        graphics.blit(DIRT, 0, 0, 0, 0.0F, 0.0F, width, height, DIRT_TILE, DIRT_TILE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Tells whether the world loading screen draws the 1.7.10 texts in place of the chunk map and the percentage
     *
     * @return true while Legacy Loading Screens is on
     */
    public static boolean legacyWorldLoading() {
        return config().legacyLoadingScreens;
    }

    /**
     * Draws "Loading world" and "Building terrain", and the progress bar if it is on, where 1.7.10 drew them
     * ({@code LoadingScreenRenderer.setLoadingProgress})
     *
     * @param graphics where to draw
     * @param font     the game's font
     * @param width    width of the screen
     * @param height   height of the screen
     * @param progress how much of the world is made, 0 to 100
     */
    public static void renderWorldLoading(GuiGraphics graphics, Font font, int width, int height, int progress) {
        if (config().legacyLoadingBar) {
            renderBar(graphics, width / 2 - BAR_WIDTH / 2, height / 2 + 16, progress);
        }
        Component title = Component.translatable("menu.loadingLevel");
        Component message = Component.translatable("menu.generatingTerrain");
        graphics.drawString(font, title, (width - font.width(title)) / 2, height / 2 - 4 - 16, WHITE);
        graphics.drawString(font, message, (width - font.width(message)) / 2, height / 2 - 4 + 8, WHITE);
    }

    /** Draws the 1.7.10 progress bar: gray, with {@code done} of its 100 pixels in green */
    private static void renderBar(GuiGraphics graphics, int left, int top, int done) {
        graphics.fill(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, BAR_COLOR);
        graphics.fill(left, top, left + Math.clamp(done, 0, BAR_WIDTH), top + BAR_HEIGHT, BAR_DONE_COLOR);
    }

    /**
     * Tells whether message screens are drawn the 1.7.10 way (no box)
     *
     * @return true while Legacy Message Screens is on
     */
    public static boolean legacyMessages() {
        return config().legacyMessageScreens;
    }

    /**
     * Draws the message of a message screen the 1.7.10 way: "Loading world" while a world opens or is made, nothing
     * while it is saved, else the message itself, without a box, where 1.7.10 wrote its title (on more lines if it is
     * wider than the screen)
     *
     * @param graphics where to draw
     * @param font     the game's font
     * @param message  the screen's message
     * @param width    width of the screen
     * @param height   height of the screen
     */
    public static void renderMessage(GuiGraphics graphics, Font font, Component message, int width, int height) {
        String key = message.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
        if (SAVING.equals(key)) {
            return;
        }
        Component text = WORLD_OPENING.contains(key) ? Component.translatable("menu.loadingLevel") : message;
        // one line like 1.7.10; a message wider than the screen goes on as many lines as it needs
        int y = height / 2 - 4 - 16;
        for (FormattedCharSequence line : font.split(text, Math.max(1, width - 2 * MESSAGE_MARGIN))) {
            graphics.drawString(font, line, (width - font.width(line)) / 2, y, WHITE);
            y += font.lineHeight;
        }
    }

    /**
     * Returns the text shown while a world or dimension is received
     *
     * @param vanilla the 1.21.1 text ("Loading terrain...")
     * @return "Downloading terrain" (1.7.10) while Legacy Loading Screens is on, else {@code vanilla}
     */
    public static Component receivingText(Component vanilla) {
        return config().legacyLoadingScreens
            ? Component.translatable("firstone-framework.appearance.downloading_terrain") : vanilla;
    }

    // ---- startup screen -------------------------------------------------------------------------------------------

    /**
     * Tells whether the startup screen is drawn the 1.7.10 way
     *
     * @return true while Legacy Startup Screen is on
     */
    public static boolean legacyStartup() {
        return config().legacyStartupScreen;
    }

    /**
     * Tells whether the overlay has reached the moment 1.21.1 starts fading it out, after which 1.7.10 shows the
     * screen behind at once
     *
     * @param fadeOutStart when loading was done ({@code LoadingOverlay.fadeOutStart}), or -1 while loading
     * @return true from that moment on
     */
    public static boolean startupFinished(long fadeOutStart) {
        return fadeOutStart > -1L && Util.getMillis() - fadeOutStart >= FADE_OUT_START;
    }

    /**
     * Returns the background color of the startup screen
     *
     * @return opaque white, or opaque black with Startup Background on Dark
     */
    public static int startupBackground() {
        return darkStartup() ? STARTUP_DARK : STARTUP_LIGHT;
    }

    /** Whether the startup screen is black (an unknown value in the file counts as white) */
    private static boolean darkStartup() {
        return config().startupBackground == AppearanceConfig.StartupBackground.DARK;
    }

    /**
     * Draws the 1.7.10 progress bar on the startup screen if Startup Bar is on
     *
     * @param graphics where to draw
     * @param progress how far the resources are loaded, 0 to 1
     */
    public static void renderStartupBar(GuiGraphics graphics, float progress) {
        if (config().legacyStartupBar) {
            renderBar(graphics, graphics.guiWidth() / 2 - BAR_WIDTH / 2,
                (int) (graphics.guiHeight() * STARTUP_BAR_HEIGHT), Math.round(progress * BAR_WIDTH));
        }
    }

    /**
     * Sets the color the game's logo is drawn with on the white screen, or white for a logo from the logos folder
     *
     * @param graphics where the logo is drawn
     * @param visible  false once the screen behind shows (the logo is then not drawn)
     */
    public static void setLogoColor(GuiGraphics graphics, boolean visible) {
        float alpha = visible ? 1.0F : 0.0F;
        if (customLogo()) {
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            return;
        }
        // the game's monochrome logo option: black on white, white on black
        boolean monochrome = Minecraft.getInstance().options.darkMojangStudiosBackground().get();
        float[] color = !monochrome ? LOGO_RED : darkStartup() ? LOGO_WHITE : LOGO_BLACK;
        graphics.setColor(color[0], color[1], color[2], alpha);
    }

    /**
     * Draws the logo from the logos folder in the middle of the screen, at most 256 wide or high like the 1.7.10 logo,
     * keeping its shape
     *
     * @param graphics where to draw
     * @return false if no such logo is chosen or it cannot be read (the game's logo is drawn then)
     */
    public static boolean drawCustomLogo(GuiGraphics graphics) {
        if (!customLogo()) {
            return false;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float scale = Math.min(CUSTOM_LOGO_SIZE, Math.min(width, height)) / (float) Math.max(logoWidth, logoHeight);
        int drawnWidth = Math.round(logoWidth * scale);
        int drawnHeight = Math.round(logoHeight * scale);
        graphics.blit(CUSTOM_LOGO, (width - drawnWidth) / 2, (height - drawnHeight) / 2, drawnWidth, drawnHeight,
            0.0F, 0.0F, logoWidth, logoHeight, logoWidth, logoHeight);
        return true;
    }

    /**
     * Tells whether a logo from the logos folder is chosen and could be read (the game's logo is not drawn then)
     *
     * @return true if {@link #drawCustomLogo} draws the logo
     */
    public static boolean hasCustomLogo() {
        return customLogo();
    }

    /**
     * Makes sure the chosen logo is loaded as a texture; a file is read once, when it is chosen
     *
     * <p>Only a PNG inside the logos folder is read (a hand-edited name like {@code ../x.png} is refused), and only up
     * to {@value #MAX_LOGO_FILE_SIZE} pixels wide and high; otherwise the game's logo is shown.</p>
     *
     * @return true if a logo from the logos folder is chosen and could be read
     */
    private static boolean customLogo() {
        String file = config().startupLogo;
        if (file == null || file.isEmpty()) {
            return false;
        }
        if (!file.equals(loadedLogo)) {
            loadedLogo = file;
            logoWidth = 0;
            logoHeight = 0;
            Path folder = getLogosDir().toAbsolutePath().normalize();
            Path path = folder.resolve(file).normalize();
            if (!path.getParent().equals(folder)) {
                FirstOneFramework.LOGGER.warn("The startup logo must be a file of {}: {}", folder, file);
                return false;
            }
            try (InputStream input = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(input);
                if (image.getWidth() > MAX_LOGO_FILE_SIZE || image.getHeight() > MAX_LOGO_FILE_SIZE) {
                    image.close();
                    FirstOneFramework.LOGGER.warn("The startup logo {} is bigger than {} pixels", path,
                        MAX_LOGO_FILE_SIZE);
                    return false;
                }
                logoWidth = image.getWidth();
                logoHeight = image.getHeight();
                Minecraft.getInstance().getTextureManager().register(CUSTOM_LOGO, new DynamicTexture(image));
            } catch (IOException | RuntimeException e) {
                FirstOneFramework.LOGGER.warn("Cannot read the startup logo {}: {}", path, e.getMessage());
            }
        }
        return logoWidth > 0 && logoHeight > 0;
    }
}
