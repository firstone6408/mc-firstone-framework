package io.github.firstone.framework.features.packmanager;

/**
 * Holds the settings of the Pack Manager feature
 *
 * <p>Every value in this class is saved to {@code pack_manager.json} and loaded when the game starts. The presets
 * themselves are kept in their own file ({@code PresetStore}), so Reset never deletes them.</p>
 *
 * <p>This feature works on the client only</p>
 */
public class PackManagerConfig {

    /**
     * Show the preset button above the Selected list of the Resource Packs screen ({@code PresetListScreen})
     *
     * <p>The button shows the preset the Selected list matches ("Preset: name", or "Preset: Custom") and opens the
     * presets. A preset keeps a list of resource packs in order. Applying it fills the Resource Packs screen with
     * those packs; the game saves and loads them when Done is pressed, as after choosing them by hand.</p>
     */
    public boolean presets = true;

    /**
     * Ask before a change in the Resource Packs screen leaves the preset in use ({@code PresetGuard})
     *
     * <p>While the Selected list matches a preset, the first change (turning a pack on or off, moving it, dropping
     * it) asks first; the preset itself never changes. Only with Presets on.</p>
     */
    public boolean askBeforeLeavingPreset = true;

    /**
     * Move packs by dragging them in the Resource Packs and data pack screens ({@code PackDrag})
     *
     * <p>A pack is dragged by its name: within the Selected list to change the order, from Available to a place in
     * Selected to turn it on there, or back to Available to turn it off. The arrow buttons keep working.</p>
     */
    public boolean dragToReorder = true;

    /**
     * Show a search box above the pack lists of the Resource Packs and data pack screens ({@code PackLists})
     *
     * <p>Both lists show only the packs whose name or file name contains the text. While searching, packs cannot be
     * moved up or down (some are hidden between them); turning packs on and off still works.</p>
     */
    public boolean packSearch = true;
}
