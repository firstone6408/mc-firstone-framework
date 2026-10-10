package io.github.firstone.framework.devtest.features.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.TestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * The files the Cosmetics tests put into the cosmetics folder, and the resource reloads that load them
 *
 * <p>Particle frames are made as small one-color images (the tests check counts, sizes and times, not looks); the
 * sounds are copies of a stereo sound of the game itself, read from its resources while the tests run (no game files
 * are kept with the tests). Resources are reloaded only when
 * the set of files changes.</p>
 */
final class CosmeticsFixture {

    /** File name of the test song in {@code music_discs/} */
    static final String SONG_FILE = "devtest_song.ogg";

    /** Number of death particle frames the tests make */
    static final int DEATH_FRAMES = 3;

    /** Number of item break particle frames the tests make */
    static final int ITEM_BREAK_FRAMES = 2;

    /** A short stereo sound of the game (48000 Hz), copied as the test sounds: 3D Sound must mix it to mono */
    private static final ResourceLocation SOURCE_SOUND =
        ResourceLocation.withDefaultNamespace("sounds/ui/toast/in.ogg");

    /**
     * Which files are in the cosmetics folder
     *
     * @param deathFrames     death particle frames
     * @param deathSound      death sound
     * @param itemBreakFrames item break particle frames
     * @param itemBreakSound  item break sound
     * @param song            the test song
     */
    record FileSet(boolean deathFrames, boolean deathSound, boolean itemBreakFrames, boolean itemBreakSound,
                 boolean song) {

        /** Every file */
        static final FileSet ALL = new FileSet(true, true, true, true, true);

        /** No file */
        static final FileSet NONE = new FileSet(false, false, false, false, false);
    }

    /** What is in the folder now, or null when unknown (start of a run) */
    private static FileSet installed;

    private CosmeticsFixture() {}

    /**
     * Puts exactly these files into the cosmetics folder and reloads the resources if anything changed
     *
     * @param t     the test
     * @param files the files to have
     * @throws IOException if a file cannot be written
     */
    static void use(TestContext t, FileSet files) throws IOException {
        if (files.equals(installed)) {
            return;
        }
        Path root = CosmeticsInternals.filesRoot();
        byte[] sound = files.equals(FileSet.NONE) ? new byte[0] : gameSound(t.mc());
        frames(root.resolve("death/particle"), files.deathFrames() ? DEATH_FRAMES : 0, 0xFF2040E0);
        frames(root.resolve("item_break/particle"), files.itemBreakFrames() ? ITEM_BREAK_FRAMES : 0, 0xFF20C040);
        file(root.resolve("death/sound.ogg"), files.deathSound() ? sound : null);
        file(root.resolve("item_break/sound.ogg"), files.itemBreakSound() ? sound : null);
        file(root.resolve("music_discs").resolve(SONG_FILE), files.song() ? sound : null);
        reload(t);
        installed = files;
    }

    /** Copies the bytes of a sound file of the game */
    private static byte[] gameSound(Minecraft minecraft) throws IOException {
        try (InputStream in = minecraft.getResourceManager().open(SOURCE_SOUND)) {
            return in.readAllBytes();
        }
    }

    /** Makes {@code count} one-color frames named 0.png, 1.png, … (and removes any others) */
    private static void frames(Path folder, int count, int abgr) throws IOException {
        deleteTree(folder);
        Files.createDirectories(folder);
        for (int i = 0; i < count; i++) {
            try (NativeImage image = new NativeImage(8, 8, false)) {
                image.fillRect(0, 0, 8, 8, abgr + i * 0x101010);
                image.writeToFile(folder.resolve(i + ".png"));
            }
        }
    }

    /** Writes a file, or deletes it when {@code bytes} is null */
    private static void file(Path file, byte[] bytes) throws IOException {
        if (bytes == null) {
            Files.deleteIfExists(file);
        } else {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        }
    }

    private static void reload(TestContext t) {
        Minecraft minecraft = t.mc();
        CompletableFuture<Void> reload = t.onClientThread(minecraft::reloadResourcePacks);
        t.waitUntil("the resources reload", () -> reload.isDone() && minecraft.getOverlay() == null, 20 * 60);
        t.ticks(5);
    }

    private static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
