package io.github.firstone.framework.devtest.features.packmanager;

import io.github.firstone.framework.client.screen.RowDrag;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.preset.PackFiles;
import io.github.firstone.framework.features.packmanager.preset.PresetStatus;
import io.github.firstone.framework.features.packmanager.preset.PresetStore;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;

import java.nio.file.Path;

/**
 * The only place where the Pack Manager tests reach into the feature's inner classes
 *
 * <p>The tests work through the screens (buttons, mouse, keys) and read the presets file and the game's pack lists;
 * the folder of the presets file, the status a preset row shows, the row geometry used to aim the mouse and the search
 * state have no other way in, so they go through here. After a refactor only this file changes.</p>
 */
final class PackManagerInternals {

    private PackManagerInternals() {}

    /**
     * Returns the folder of the presets file ({@code PresetStore.folder})
     *
     * @return the folder
     */
    static Path presetsFolder() {
        return PresetStore.folder();
    }

    /**
     * Reads the presets file again, as the game does when it starts ({@code PresetStore.load})
     */
    static void loadPresets() {
        PresetStore.load();
    }

    /**
     * Tells whether the preset row at an index is marked Active ({@code PresetStatus.active}); call on the client
     * thread after the presets screen read the file
     *
     * @param packScreen the Resource Packs screen the presets belong to
     * @param index      the preset's index in the list
     * @return true if the Selected list holds exactly the preset's packs
     */
    static boolean isActive(PackSelectionScreen packScreen, int index) {
        return PresetStatus.of(PresetStore.getPresets().get(index), PackLists.model(packScreen)).active();
    }

    /**
     * Returns how many packs of the preset at an index are missing, for another version, and changed since it was
     * saved, as its row shows ({@code PresetStatus}, {@code PackFiles}); call on the client thread
     *
     * @param packScreen the Resource Packs screen the presets belong to
     * @param index      the preset's index in the list
     * @return {missing, other version, changed file ({@code PackFiles.changed})}
     */
    static int[] problemCounts(PackSelectionScreen packScreen, int index) {
        PackPreset preset = PresetStore.getPresets().get(index);
        PresetStatus status = PresetStatus.of(preset, PackLists.model(packScreen));
        return new int[] {status.missing().size(), status.incompatible().size(), PackFiles.changed(preset).size()};
    }

    /**
     * Returns the top of a row of a list widget on screen ({@code RowDrag.Rows}: the pack lists and the mod's lists)
     *
     * @param list  the list widget
     * @param index the row index
     * @return the y of the row's top
     */
    static int rowTop(AbstractSelectionList<?> list, int index) {
        return ((RowDrag.Rows) list).rowDrag$rowTop(index);
    }

    /**
     * Returns the index of the selected row of a list widget
     *
     * @param list the list widget
     * @return the row index, or -1 if no row is selected
     */
    static int selectedRow(AbstractSelectionList<?> list) {
        Object selected = list.getSelected();
        return list.children().indexOf(selected);
    }

    /**
     * Tells whether a pack screen lists a pack, in either list ({@code PackLists.entries}); call on the client thread
     *
     * @param screen a pack screen
     * @param id     the pack id
     * @return true if the screen's model has the pack
     */
    static boolean isListed(PackSelectionScreen screen, String id) {
        return PackLists.entries(PackLists.model(screen)).containsKey(id);
    }

    /**
     * Tells whether a pack screen is searching ({@code PackLists.Parts})
     *
     * @param screen a screen
     * @return true if it is a pack screen whose search box holds a text
     */
    static boolean isSearching(Screen screen) {
        return screen instanceof PackLists.Parts parts && parts.packManager$isSearching();
    }
}
