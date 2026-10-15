package io.github.firstone.framework.features.packmanager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reads, arranges and filters the two pack lists of a pack screen, only through the screen's own functions
 *
 * <p>Every change goes through {@link PackSelectionModel.Entry}: {@code select()}, {@code unselect()},
 * {@code moveUp()} and {@code moveDown()}, the functions the screen's arrow buttons call. So the game's rules stay in
 * charge (required packs stay on, fixed packs keep their place, the High Contrast option follows its pack), and the
 * game saves the choice and loads the resources itself when Done is pressed. Nothing here reloads resources.</p>
 *
 * <p>The Selected list is read top first: a pack higher up wins.</p>
 *
 * <p>Search: while a text is typed in a pack screen's search box ({@code PackScreenMixin}), both lists show only the
 * packs that match it ({@link #matchesSearch}); the packs stay on or off as they are. Packs cannot be moved up or down
 * then ({@code PackEntryMixin}), because the packs hidden between them would make a step look like nothing
 * happened.</p>
 */
public final class PackLists {

    /** The game's icon of a pack without one (and of a missing pack) */
    public static final ResourceLocation UNKNOWN_ICON =
        ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png");

    private PackLists() {}

    /**
     * What Pack Manager reads from a pack screen; every {@code PackSelectionScreen} has it ({@code PackScreenMixin})
     */
    public interface Parts {

        /**
         * Returns the screen's model, which holds the two pack lists and selects and moves packs
         *
         * @return the model
         */
        PackSelectionModel packManager$model();

        /**
         * Returns the Available list widget (left)
         *
         * @return the list widget
         */
        TransferableSelectionList packManager$availableList();

        /**
         * Returns the Selected list widget (right)
         *
         * @return the list widget
         */
        TransferableSelectionList packManager$selectedList();

        /**
         * Returns the folder the screen shows packs of (the resource pack folder, or a world's data pack folder)
         *
         * @return the folder
         */
        Path packManager$packDir();

        /**
         * Tells whether the screen shows only the packs that match a typed text
         *
         * @return true while the search box holds a text
         */
        boolean packManager$isSearching();

        /**
         * Empties the search box, so both lists show every pack again
         */
        void packManager$clearSearch();
    }

    /**
     * Returns what Pack Manager reads from a pack screen
     *
     * @param screen a pack screen
     * @return its parts
     */
    public static Parts parts(PackSelectionScreen screen) {
        return (Parts) screen;
    }

    /**
     * Tells whether the open screen is a pack screen that is searching
     *
     * @return true while the open pack screen's search box holds a text
     */
    public static boolean isSearching() {
        return Minecraft.getInstance().screen instanceof Parts parts && parts.packManager$isSearching();
    }

    /**
     * Tells whether search is on and a text is typed
     *
     * @param text the text of the search box
     * @return true if the lists should be filtered
     */
    public static boolean isSearchActive(String text) {
        return PackManagerFeature.getConfig().packSearch && !text.isBlank();
    }

    /**
     * Tells whether a pack matches the typed text: its name or its id (the file name) contains it, ignoring case
     * and color codes
     *
     * @param entry a pack of the screen
     * @param text  the typed text
     * @return true if the pack is shown
     */
    public static boolean matchesSearch(PackSelectionModel.Entry entry, String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        String title = ChatFormatting.stripFormatting(entry.getTitle().getString());
        return (title != null && title.toLowerCase(Locale.ROOT).contains(query))
            || entry.getId().toLowerCase(Locale.ROOT).contains(query);
    }

    /**
     * Tells whether a pack screen is the game's Resource Packs screen (not a data pack screen)
     *
     * @param screen a pack screen
     * @return true if it shows the resource pack folder
     */
    public static boolean isResourcePackScreen(PackSelectionScreen screen) {
        return parts(screen).packManager$packDir()
            .equals(Minecraft.getInstance().getResourcePackDirectory());
    }

    /**
     * Returns the model of a pack screen
     *
     * @param screen a pack screen
     * @return its model
     */
    public static PackSelectionModel model(PackSelectionScreen screen) {
        return parts(screen).packManager$model();
    }

    /**
     * Returns the ids of the Selected list, top first, without fixed packs (the packs a server sends)
     *
     * <p>The same packs the game saves in {@code options.txt} when Done is pressed.</p>
     *
     * @param model the screen's model
     * @return the pack ids
     */
    public static List<String> selectedIds(PackSelectionModel model) {
        return model.getSelected().filter(entry -> !entry.isFixedPosition())
            .map(entry -> entry.getId()).toList();
    }

    /**
     * Returns every pack the screen lists, Selected first, by id
     *
     * @param model the screen's model
     * @return the packs by id
     */
    public static Map<String, PackSelectionModel.Entry> entries(PackSelectionModel model) {
        Map<String, PackSelectionModel.Entry> entries = new LinkedHashMap<>();
        Stream.concat(model.getSelected(), model.getUnselected())
            .forEach(entry -> entries.putIfAbsent(entry.getId(), entry));
        return entries;
    }

    /**
     * Returns a pack of the Selected list
     *
     * @param model the screen's model
     * @param id    the pack id
     * @return the pack, or null if it is not selected
     */
    @Nullable
    public static PackSelectionModel.Entry selected(PackSelectionModel model, String id) {
        return model.getSelected().filter(entry -> entry.getId().equals(id)).findFirst().orElse(null);
    }

    /**
     * Returns where a pack is in the Selected list
     *
     * @param model the screen's model
     * @param id    the pack id
     * @return its index (0 = top), or -1 if it is not selected
     */
    public static int selectedIndex(PackSelectionModel model, String id) {
        return model.getSelected().map(pack -> pack.getId()).toList().indexOf(id);
    }

    /**
     * Fills the Selected list with the given packs in the given order, as a player would with the arrow buttons
     *
     * <p>Packs not in the list are turned off (required ones cannot be, and stay), missing and fixed packs are
     * skipped, the rest are turned on and then moved into order. Packs not in the list keep their places, so required
     * and fixed packs stay where the game put them.</p>
     *
     * @param model the screen's model
     * @param packs pack ids, top first
     */
    public static void apply(PackSelectionModel model, List<String> packs) {
        Map<String, PackSelectionModel.Entry> entries = entries(model);
        List<String> wanted = packs.stream().filter(id -> entries.containsKey(id)
            && !entries.get(id).isFixedPosition()).distinct().toList();
        Set<String> wantedSet = new HashSet<>(wanted);

        for (PackSelectionModel.Entry entry : model.getSelected().toList()) {
            if (!wantedSet.contains(entry.getId()) && entry.canUnselect()) {
                entry.unselect();
            }
        }
        for (String id : wanted) {
            PackSelectionModel.Entry entry = entries.get(id);
            if (entry.canSelect()) {
                entry.select();
            }
        }

        // the places of the wanted packs are filled in the wanted order; every other pack keeps its place
        List<String> current = model.getSelected().map(entry -> entry.getId()).toList();
        Iterator<String> next = wanted.iterator();
        List<String> target = new ArrayList<>();
        for (String id : current) {
            target.add(wantedSet.contains(id) && next.hasNext() ? next.next() : id);
        }
        for (int index = 0; index < target.size(); index++) {
            moveTo(model, target.get(index), index);
        }
    }

    /**
     * Moves a pack of the Selected list to an index, one step at a time with the screen's move up / move down
     *
     * <p>Stops early where the game does not allow the next step (next to a fixed pack); a fixed pack itself never
     * moves.</p>
     *
     * @param model the screen's model
     * @param id    the pack id
     * @param index the index to move to (0 = top)
     */
    public static void moveTo(PackSelectionModel model, String id, int index) {
        PackSelectionModel.Entry entry = selected(model, id);
        if (entry == null || entry.isFixedPosition()) {
            return;
        }
        int at = selectedIndex(model, id);
        while (at > index && entry.canMoveUp()) {
            entry.moveUp();
            at--;
        }
        while (at < index && entry.canMoveDown()) {
            entry.moveDown();
            at++;
        }
    }
}
