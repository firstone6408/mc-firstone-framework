package io.github.firstone.framework.features.packmanager.preset;

import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tells whether a pack file is still the one a preset was saved with
 *
 * <p>A preset keeps its packs by file name, so another file with the same name would count as the same pack. When a
 * pack is put into a preset and the preset is saved, the size and SHA-1 of its zip file are kept
 * ({@link PackPreset#files}); later they are compared with the file now ({@link #changed}). A differing file is
 * "Changed": it is skipped when the preset is applied, until the player accepts the new file in the preset form
 * ({@link #accept}, Review Changed) and saves. Saving never accepts a changed file by itself. A file put back as it was
 * is no longer changed. Folder packs and packs of the game or of mods are not checked.</p>
 *
 * <p>Reading a big file takes time, so files are read on a background thread of their own, once per file state
 * (again only after the file's size or modification time changed). What is only shown (the presets list, the preset
 * button) uses what is known and updates itself when a read finishes ({@link #version}); what acts (applying,
 * asking before leaving a preset, the form, saving) waits for the reads it needs ({@link #changed},
 * {@link #awaitAll}), so its result is always exact. Nothing is read while playing.</p>
 */
public final class PackFiles {

    /** Id prefix of the packs in the resource pack folder */
    private static final String FILE_PREFIX = "file/";

    /** Bytes read at a time */
    private static final int BUFFER_SIZE = 64 * 1024;

    /** Reads the files, one at a time, without slowing the game's own threads */
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Pack Manager file check");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    /** Fingerprints worked out or being worked out, by file */
    private static final Map<Path, Known> KNOWN = new HashMap<>();

    /** Goes up each time a background read finishes */
    private static final AtomicInteger VERSION = new AtomicInteger();

    /** A file state and its fingerprint (null while it is read, or if it could not be read) */
    private record Known(long size, long modified, CompletableFuture<String> fingerprint) {}

    private PackFiles() {}

    /**
     * Takes the fingerprints of the preset's zip packs that have none yet, and forgets those of packs no longer in it
     * (called when the preset is saved)
     *
     * <p>Packs that already have one keep it: a changed file stays changed until it is accepted, and a missing pack
     * can be checked when its file comes back.</p>
     *
     * @param preset the preset
     */
    public static void remember(PackPreset preset) {
        preset.files.keySet().retainAll(preset.packs);
        for (String id : preset.packs) {
            if (!preset.files.containsKey(id)) {
                String fingerprint = fingerprint(id, true);
                if (fingerprint != null) {
                    preset.files.put(id, fingerprint);
                }
            }
        }
    }

    /**
     * Accepts the files of some packs as they are now, so they are no longer changed (Review Changed)
     *
     * @param preset the preset (the form's draft; saved with Save)
     * @param ids    the packs whose new files are accepted
     */
    public static void accept(PackPreset preset, List<String> ids) {
        for (String id : ids) {
            String fingerprint = fingerprint(id, true);
            if (fingerprint != null) {
                preset.files.put(id, fingerprint);
            }
        }
    }

    /**
     * Returns the preset's packs whose file is not the one the preset was saved with, waiting for the reads it needs
     *
     * @param preset the preset
     * @return the pack ids, in the preset's order
     */
    public static List<String> changed(PackPreset preset) {
        return changed(preset, true);
    }

    /**
     * Returns the preset's packs known to be changed now, without waiting: files not read yet count as unchanged
     * and are read in the background (for what is only shown; see {@link #version})
     *
     * @param preset the preset
     * @return the pack ids, in the preset's order
     */
    public static List<String> changedIfKnown(PackPreset preset) {
        return changed(preset, false);
    }

    /**
     * Waits until the files of these presets that have to be compared are read (before acting on what they say)
     *
     * @param presets the presets
     */
    public static void awaitAll(Collection<PackPreset> presets) {
        for (PackPreset preset : presets) {
            changed(preset, true);
        }
    }

    /**
     * Returns a number that goes up each time a background read finishes, so a screen knows when to show the
     * results again
     *
     * @return the number
     */
    public static int version() {
        return VERSION.get();
    }

    /**
     * Returns the size a pack's file had when the preset was saved
     *
     * @param preset the preset
     * @param id     the pack id
     * @return the size in bytes, or -1 if unknown
     */
    public static long savedSize(PackPreset preset, String id) {
        return sizeOf(preset.files.get(id));
    }

    /**
     * Returns the size of a pack's file now
     *
     * @param id the pack id
     * @return the size in bytes, or -1 if unknown
     */
    public static long currentSize(String id) {
        return sizeOf(fingerprint(id, true));
    }

    /** Returns the packs whose saved fingerprint differs from the file now (only packs with a saved one are read) */
    private static List<String> changed(PackPreset preset, boolean wait) {
        return preset.packs.stream().filter(id -> {
            String saved = preset.files.get(id);
            String now = saved == null ? null : fingerprint(id, wait);
            return now != null && !now.equals(saved);
        }).toList();
    }

    /** Returns the size part of a fingerprint, or -1 */
    private static long sizeOf(@Nullable String fingerprint) {
        int colon = fingerprint == null ? -1 : fingerprint.indexOf(':');
        try {
            return colon > 0 ? Long.parseLong(fingerprint.substring(0, colon)) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Returns "size:sha1" of a zip pack of the resource pack folder, or null for other packs, a missing file, a file
     * that cannot be read, a name outside the folder or invalid here, or (without waiting) a file still being read
     */
    @Nullable
    private static String fingerprint(String id, boolean wait) {
        if (!id.startsWith(FILE_PREFIX)) {
            return null;
        }
        Path folder = Minecraft.getInstance().getResourcePackDirectory().toAbsolutePath().normalize();
        Path file;
        try {
            file = folder.resolve(id.substring(FILE_PREFIX.length())).normalize();
        } catch (InvalidPathException e) {
            return null; // a name no file can have on this system (e.g. a presets file from another one)
        }
        if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
            return null;
        }
        CompletableFuture<String> read;
        try {
            long size = Files.size(file);
            long modified = Files.getLastModifiedTime(file).toMillis();
            synchronized (KNOWN) {
                Known known = KNOWN.get(file);
                if (known == null || known.size() != size || known.modified() != modified) {
                    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> read(file, size), READER);
                    future.whenComplete((fingerprint, error) -> VERSION.incrementAndGet());
                    known = new Known(size, modified, future);
                    KNOWN.put(file, known);
                }
                read = known.fingerprint();
            }
        } catch (IOException e) {
            return null;
        }
        if (!wait && !read.isDone()) {
            return null;
        }
        try {
            return read.join();
        } catch (CompletionException e) {
            return null;
        }
    }

    /** Reads a file: "size:sha1" */
    private static String read(Path file, long size) {
        try {
            return size + ":" + sha1(file);
        } catch (IOException e) {
            throw new CompletionException(e);
        }
    }

    /** Returns the SHA-1 of a file, in hex */
    private static String sha1(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
