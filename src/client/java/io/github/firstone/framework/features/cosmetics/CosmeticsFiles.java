package io.github.firstone.framework.features.cosmetics;

import io.github.firstone.framework.FirstOneFramework;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds the player's files of the Cosmetics feature
 *
 * <pre>
 * config/firstone-framework/cosmetics/
 * ├── death/
 * │   ├── particle/   0.png, 1.png, …   frames, played in number order
 * │   └── sound.ogg
 * ├── item_break/
 * │   ├── particle/   0.png, 1.png, …
 * │   └── sound.ogg
 * └── music_discs/    cat.ogg, pigstep.ogg, …   file name = disc name
 * </pre>
 *
 * <p>Used by {@link CosmeticsPack} (what the game loads) and by the config screen (what is in the folders).
 * Only files with the expected names are used; anything else in the folders is ignored.</p>
 */
public final class CosmeticsFiles {

    /** Folder name of the death effect */
    public static final String DEATH = "death";

    /** Folder name of the item break effect */
    public static final String ITEM_BREAK = "item_break";

    /** Most frames used per effect */
    public static final int MAX_FRAMES = 64;

    /** Largest frame width or height in pixels; bigger images could make the particle texture fail to build */
    public static final int MAX_FRAME_SIZE = 256;

    /** Frame file name: a number and ".png" (e.g. {@code 0.png}, {@code 12.png}) */
    private static final Pattern FRAME_NAME = Pattern.compile("\\d{1,4}\\.png");

    /** Disc name allowed in a resource location (lower case letters, digits, {@code _ - .}) */
    private static final Pattern DISC_NAME = Pattern.compile("[a-z0-9_.-]+");

    /** First 8 bytes of every PNG file */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private CosmeticsFiles() {}

    /**
     * Returns the root folder of the Cosmetics files
     *
     * @return path of {@code config/firstone-framework/cosmetics/}
     */
    public static Path root() {
        return FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/cosmetics");
    }

    /**
     * Returns the sound file of an effect
     *
     * @param effect {@link #DEATH} or {@link #ITEM_BREAK}
     * @return path of {@code <effect>/sound.ogg} (it may not exist)
     */
    public static Path sound(String effect) {
        return root().resolve(effect).resolve("sound.ogg");
    }

    /**
     * Creates the folders, so the player can see where the files go
     */
    public static void createFolders() {
        try {
            Files.createDirectories(root().resolve(DEATH).resolve("particle"));
            Files.createDirectories(root().resolve(ITEM_BREAK).resolve("particle"));
            Files.createDirectories(root().resolve("music_discs"));
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to create the cosmetics folders: {}", e.getMessage());
        }
    }

    /**
     * Returns the frames of an effect, in play order
     *
     * <p>Frames are the PNG files of {@code <effect>/particle/} named with a number, sorted by that number.
     * Files that are not PNG images, or are larger than {@link #MAX_FRAME_SIZE} pixels, are skipped; at most
     * {@link #MAX_FRAMES} frames are used.</p>
     *
     * @param effect {@link #DEATH} or {@link #ITEM_BREAK}
     * @return the frame files, or an empty list if there are none
     */
    public static List<Path> frames(String effect) {
        Path folder = root().resolve(effect).resolve("particle");
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files
                .filter(file -> FRAME_NAME.matcher(file.getFileName().toString().toLowerCase(Locale.ROOT)).matches())
                .filter(CosmeticsFiles::isUsableImage)
                .sorted(Comparator.comparingInt(CosmeticsFiles::frameNumber))
                .limit(MAX_FRAMES)
                .toList();
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to list the frames of {}: {}", folder, e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns the song files of {@code music_discs/}, by disc name
     *
     * @return disc name (file name without {@code .ogg}, lower case, e.g. {@code "cat"}) → file, sorted by name
     */
    public static Map<String, Path> musicDiscs() {
        Path folder = root().resolve("music_discs");
        Map<String, Path> discs = new TreeMap<>();
        if (!Files.isDirectory(folder)) {
            return discs;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".ogg")) {
                    String disc = name.substring(0, name.length() - ".ogg".length());
                    if (DISC_NAME.matcher(disc).matches()) {
                        discs.put(disc, file);
                    }
                }
            });
        } catch (IOException e) {
            FirstOneFramework.LOGGER.error("Failed to list the music discs of {}: {}", folder, e.getMessage());
        }
        return discs;
    }

    /** Returns the number in a frame file name ({@code "12.png"} → 12) */
    private static int frameNumber(Path file) {
        String name = file.getFileName().toString();
        return Integer.parseInt(name.substring(0, name.indexOf('.')));
    }

    /**
     * Tells whether a file is a PNG image no larger than {@link #MAX_FRAME_SIZE}, reading only its header
     * (signature and the size in the IHDR chunk)
     */
    private static boolean isUsableImage(Path file) {
        byte[] header = new byte[24];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(header, 0, header.length) < header.length
                || !Arrays.equals(Arrays.copyOf(header, PNG_SIGNATURE.length), PNG_SIGNATURE)) {
                FirstOneFramework.LOGGER.warn("Skipped {}: not a PNG image", file);
                return false;
            }
        } catch (IOException e) {
            FirstOneFramework.LOGGER.warn("Skipped {}: {}", file, e.getMessage());
            return false;
        }
        int width = ByteBuffer.wrap(header, 16, 4).getInt();
        int height = ByteBuffer.wrap(header, 20, 4).getInt();
        if (width <= 0 || height <= 0 || width > MAX_FRAME_SIZE || height > MAX_FRAME_SIZE) {
            FirstOneFramework.LOGGER.warn("Skipped {}: {}x{} is larger than {} pixels", file, width, height,
                MAX_FRAME_SIZE);
            return false;
        }
        return true;
    }
}
