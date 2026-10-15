package io.github.firstone.framework.features.packmanager.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the presets ({@link PackPreset}) and their file, {@code config/firstone-framework/pack_manager/presets.json}
 *
 * <p>The presets are kept apart from the options ({@code pack_manager.json}), so resetting the options never deletes
 * them. A file that cannot be read is never written over: it is renamed to {@code presets.json.broken} and the list
 * starts empty. Every change is saved at once, through a temporary file, so a crash cannot leave half a file.</p>
 */
public final class PresetStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("firstone-framework");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Name of the presets file */
    private static final String FILE = "presets.json";

    /** Name given to a presets file that could not be read */
    private static final String BROKEN_FILE = "presets.json.broken";

    /** Name used for a preset whose name is empty */
    private static final String DEFAULT_NAME = "Preset";

    /** The presets, in the order of the list */
    private static final List<PackPreset> PRESETS = new ArrayList<>();

    /** Goes up each time the presets are read or saved */
    private static int version;

    private PresetStore() {}

    /**
     * One saved list of resource packs, in order
     *
     * <p>Saved in {@code presets.json} by {@link PresetStore}. The packs are kept by their ids as the game knows
     * them (e.g. {@code file/Old Sounds.zip}, {@code vanilla}), top first: the order of the Selected list, where a pack
     * higher up wins. A pack whose file is gone stays in the list, so the preset works again when the file comes
     * back.</p>
     */
    public static final class PackPreset {

        /** Fixed id, made by {@link PresetStore}; never shown, so the name can change freely */
        public String id = "";

        /** Name shown in the list */
        public String name = "";

        /** Description shown under the name (may be empty) */
        public String description = "";

        /** Id of the pack whose icon is shown, or empty to show the icons of the top packs */
        public String icon = "";

        /** Pack ids, top first (a pack higher up wins), without the packs a server sends */
        public List<String> packs = new ArrayList<>();

        /** "size:sha1" of each zip pack when the preset was saved, by pack id ({@link PackFiles}) */
        public Map<String, String> files = new LinkedHashMap<>();

        /**
         * Makes a copy with its own pack list and the same id (the preset form edits such a copy)
         *
         * @return the copy
         */
        public PackPreset copy() {
            PackPreset copy = new PackPreset();
            copy.id = this.id;
            copy.name = this.name;
            copy.description = this.description;
            copy.icon = this.icon;
            copy.packs = new ArrayList<>(this.packs);
            copy.files = new LinkedHashMap<>(this.files);
            return copy;
        }

        /**
         * Tells whether another preset has the same name, description, icon, packs and accepted files (the id is not
         * compared)
         *
         * @param other the other preset
         * @return true if the form would save nothing new
         */
        public boolean sameValues(PackPreset other) {
            return this.name.equals(other.name) && this.description.equals(other.description)
                && this.icon.equals(other.icon) && this.packs.equals(other.packs) && this.files.equals(other.files);
        }
    }

    /** Layout of the presets file */
    private static final class PresetFile {
        List<PackPreset> presets = new ArrayList<>();
    }

    /**
     * Returns the folder of the presets file
     *
     * @return path of {@code config/firstone-framework/pack_manager/}
     */
    public static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve("firstone-framework/pack_manager");
    }

    /**
     * Reads the presets file again (a missing file means no presets)
     *
     * <p>Called when the game starts and when the presets screen opens, so changes made to the file by hand show up.
     * Damaged entries are repaired: an empty name gets a default, a missing id a new one, unknown or repeated packs
     * are dropped.</p>
     */
    public static void load() {
        version++;
        PRESETS.clear();
        Path file = folder().resolve(FILE);
        if (!Files.exists(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            PresetFile data = read(reader, PresetFile.class);
            if (data != null && data.presets != null) {
                Set<String> ids = new HashSet<>();
                for (PackPreset preset : data.presets) {
                    if (preset != null) {
                        PRESETS.add(repaired(preset, ids));
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            PRESETS.clear();
            LOGGER.error("Failed to load {}, kept as {}: {}", file, BROKEN_FILE, e.getMessage());
            try {
                Files.move(file, file.resolveSibling(BROKEN_FILE), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException moveError) {
                LOGGER.error("Failed to rename {}: {}", file, moveError.getMessage());
            }
        }
    }

    /**
     * Returns a number that goes up each time the presets change (read again or saved), so what shows them knows
     * when to look again
     *
     * @return the number
     */
    public static int version() {
        return version;
    }

    /**
     * Returns the presets, in the order of the list
     *
     * <p>The list cannot be changed directly; use {@link #add}, {@link #update}, {@link #delete} and
     * {@link #move}.</p>
     *
     * @return the presets
     */
    public static List<PackPreset> getPresets() {
        return Collections.unmodifiableList(PRESETS);
    }

    /**
     * Adds a new preset (made in the preset form) at the end of the list and saves
     *
     * @param preset the preset; it gets a new id
     */
    public static void add(PackPreset preset) {
        preset.id = newId();
        PackFiles.remember(preset);
        PRESETS.add(preset);
        save();
    }

    /**
     * Writes the values of the preset form into a saved preset and saves; packs new in it get the fingerprints of
     * their files, changed files stay changed unless they were accepted in the form
     *
     * @param preset the saved preset
     * @param values the values from the form (name, description, icon, packs)
     */
    public static void update(PackPreset preset, PackPreset values) {
        preset.name = values.name;
        preset.description = values.description;
        preset.icon = values.icon;
        preset.packs = new ArrayList<>(values.packs);
        preset.files = new LinkedHashMap<>(values.files);
        PackFiles.remember(preset);
        save();
    }

    /**
     * Removes a preset and saves
     *
     * @param preset the preset to remove
     */
    public static void delete(PackPreset preset) {
        if (PRESETS.remove(preset)) {
            save();
        }
    }

    /**
     * Moves a preset to another place in the list and saves
     *
     * @param preset the preset to move
     * @param index  its new index (clamped to the list)
     */
    public static void move(PackPreset preset, int index) {
        if (PRESETS.remove(preset)) {
            PRESETS.add(Math.clamp(index, 0, PRESETS.size()), preset);
            save();
        }
    }

    /**
     * Saves every preset to the file
     *
     * <p>Called after a change; a preset without a name is saved with a default one.</p>
     */
    public static void save() {
        version++;
        Path folder = folder();
        Path file = folder.resolve(FILE);
        Path temporary = folder.resolve(FILE + ".tmp");
        PresetFile data = new PresetFile();
        for (PackPreset preset : PRESETS) {
            PackPreset saved = preset.copy();
            saved.name = preset.name.isBlank() ? DEFAULT_NAME : preset.name;
            data.presets.add(saved);
        }
        try {
            Files.createDirectories(folder);
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(data, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save {}: {}", file, e.getMessage());
        }
    }

    /** Reads JSON into a type (null for an empty file) */
    private static <T> T read(Reader reader, Class<T> type) {
        return GSON.fromJson(reader, type);
    }

    /** Returns a preset read from the file with every missing or damaged value repaired */
    private static PackPreset repaired(PackPreset preset, Set<String> ids) {
        if (preset.id == null || preset.id.isBlank() || !ids.add(preset.id)) {
            preset.id = newId();
            ids.add(preset.id);
        }
        if (preset.name == null || preset.name.isBlank()) {
            preset.name = DEFAULT_NAME;
        }
        if (preset.description == null) {
            preset.description = "";
        }
        if (preset.icon == null) {
            preset.icon = "";
        }
        Set<String> packs = new LinkedHashSet<>();
        if (preset.packs != null) {
            for (String pack : preset.packs) {
                if (pack != null && !pack.isBlank()) {
                    packs.add(pack);
                }
            }
        }
        preset.packs = new ArrayList<>(packs);
        if (preset.files == null) {
            preset.files = new LinkedHashMap<>();
        }
        preset.files.values().removeIf(fingerprint -> fingerprint == null);
        return preset;
    }

    /** Returns a new random preset id */
    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
