package io.github.firstone.framework.features.packmanager.preset;

import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * How a preset compares with the packs the Resource Packs screen knows
 *
 * @param missing      ids of the preset's packs whose files are gone (skipped when applying)
 * @param changed      ids of the preset's packs whose file is not the one the preset was saved with and was not
 *                     accepted yet ({@link PackFiles}; skipped when applying)
 * @param incompatible the packs that would be applied but were made for another version of Minecraft
 * @param toApply      ids of the packs applying turns on, in the preset's order (neither missing, changed nor
 *                     fixed)
 * @param active       true if the Selected list holds exactly the packs to apply, in their order (required packs the
 *                     preset does not name are left out of the comparison: they are always on)
 */
public record PresetStatus(List<String> missing, List<String> changed, List<PackSelectionModel.Entry> incompatible,
                           List<String> toApply, boolean active) {

    /** Id prefix of the packs in the resource pack folder */
    private static final String FILE_PREFIX = "file/";

    /**
     * Compares a preset with the packs of a pack screen
     *
     * <p>Changed files count as known so far ({@link PackFiles#changedIfKnown}); before acting on the result, call
     * {@link PackFiles#awaitAll}.</p>
     *
     * @param preset the preset
     * @param model  the Resource Packs screen's model
     * @return the preset's status
     */
    public static PresetStatus of(PackPreset preset, PackSelectionModel model) {
        Map<String, PackSelectionModel.Entry> entries = PackLists.entries(model);
        List<String> missing = preset.packs.stream().filter(id -> !entries.containsKey(id)).toList();
        List<String> changed = PackFiles.changedIfKnown(preset).stream().filter(entries::containsKey).toList();
        // fixed packs (sent by a server) stay where the game put them; only a hand-edited preset can name one
        List<String> toApply = preset.packs.stream().filter(id -> entries.containsKey(id) && !changed.contains(id)
            && !entries.get(id).isFixedPosition()).toList();
        List<PackSelectionModel.Entry> incompatible = toApply.stream().map(entries::get)
            .filter(entry -> !entry.getCompatibility().isCompatible()).toList();
        // required packs the preset does not name are on anyway (applying cannot turn them off), so they are left out
        List<String> selected = PackLists.selectedIds(model).stream()
            .filter(id -> toApply.contains(id) || !entries.get(id).isRequired()).toList();
        boolean active = !toApply.isEmpty() && toApply.equals(selected);
        return new PresetStatus(missing, changed, incompatible, toApply, active);
    }

    /**
     * Returns the preset the Selected list matches (the first one, if several match)
     *
     * @param model the Resource Packs screen's model
     * @return the preset in use, or null if the packs match no preset (custom packs)
     */
    @Nullable
    public static PackPreset current(PackSelectionModel model) {
        for (PackPreset preset : PresetStore.getPresets()) {
            if (of(preset, model).active()) {
                return preset;
            }
        }
        return null;
    }

    /**
     * Returns the name to show for a pack that may be missing: the file name for a pack of the resource pack folder
     * ({@code file/Old Sounds.zip} → {@code Old Sounds.zip}), else the id
     *
     * @param id a pack id
     * @return the name to show
     */
    public static String fileName(String id) {
        return id.startsWith(FILE_PREFIX) ? id.substring(FILE_PREFIX.length()) : id;
    }
}
