package io.github.firstone.framework.devtest.features.packmanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.mixin.PackScreenAccessor;
import io.github.firstone.framework.features.packmanager.PackManagerConfig;
import io.github.firstone.framework.features.packmanager.PackManagerFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tests of Pack Manager: presets, dragging and search in the pack screens
 *
 * <p>The tests use the screens as a player does: buttons are pressed by their text, rows are clicked and dragged with
 * the mouse at their place on screen, keys go through the game's keyboard handler. What changed is read from the
 * outside: the game's pack lists (the screen's model, {@code options.txt} and the loaded resources) and the presets
 * file. Test packs are made fresh in the resource pack folder; the game's pack choice is put back after each test. The
 * screens run at GUI scale 1, so every row is on screen.</p>
 *
 * <p>Pack Manager has no 1.7.10 counterpart; the expected values are the orders and choices a player would make by
 * hand with the arrow buttons.</p>
 */
public final class PackManagerTests implements FeatureTests {

    private static final String KEY = "firstone-framework.pack_manager.";
    private static final String PRESETS = KEY + "presets.";

    /** Folder name prefix of the test packs (as {@code TestContext.resourcePack} names them) */
    private static final String PREFIX = "devtest_pack_manager_";

    /** Data pack format of 1.21.1 */
    private static final int DATA_PACK_FORMAT = 48;

    /** Where the mouse aims inside a row: on the name, right of the icon */
    private static final int NAME_X = 100;
    private static final int ROW_MIDDLE = 16;

    /** Where the edit, copy and delete buttons of a preset row start (from the row's left), and their spacing */
    private static final int ROW_BUTTONS_X = 300 - 4 - 52;
    private static final int ROW_BUTTON_STEP = 18;
    private static final int EDIT = 0;
    private static final int COPY = 1;
    private static final int DELETE = 2;

    /** Time stored in the test zip files, so a file written again with the same content has the same bytes */
    private static final long ZIP_TIME = 1_700_000_000_000L;

    /** Mouse moves of a drag */
    private static final int DRAG_STEPS = 8;

    /** Where the user's own resource packs are kept (local only, not in git) */
    private static final String USER_PACKS = "../../.local/my/resourcepacks";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Pack screens a test opened; their folder watchers are stopped after the test ({@link #restore}) */
    private static final List<PackSelectionScreen> OPENED = new ArrayList<>();

    @Override
    public String id() {
        return "pack_manager";
    }

    @Override
    public List<Object> configs() {
        return List.of(PackManagerFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        return List.of(
            TestCase.of("Preset button: above Selected, shows the preset in use or Custom, opens the presets; "
                + "off: none", this::presetButton),
            TestCase.of("Apply: the Selected list takes the preset's packs in order; Done saves and loads them; "
                + "custom packs ask first", this::applyOrder),
            TestCase.of("Apply: other packs turn off, required stay, missing are skipped and named; Active",
                this::applyMissing),
            TestCase.of("Apply: a pack for another version asks first; No changes nothing", this::applyIncompatible),
            TestCase.of("Preset form: nothing saved before Create or Save, Cancel asks when changed, name needed, "
                + "Copy, Delete", this::presetForm),
            TestCase.of("Presets: dragging a preset by its name orders the list; a click and Escape do not",
                this::orderPresets),
            TestCase.of("Presets file: a broken file is kept, hand edits are repaired, Reset keeps the presets",
                this::presetsFile),
            TestCase.of("Edit Packs, Use Current, Remove Missing: into the form only, ask first; the game's packs "
                + "and High Contrast stay", this::editPacks),
            TestCase.of("Changed files: marked, skipped by Apply, accepted only when ticked in Review Changed; "
                + "put back, deleted, copied", this::changedFiles),
            TestCase.of("Leaving a preset: the first change asks (arrow, move, drop); Cancel changes nothing; off: "
                + "no question", this::leavePreset),
            TestCase.of("Drag: within Selected to the place under the mouse; a click and Escape do not move",
                this::dragWithinSelected),
            TestCase.of("Drag: Available to a place in Selected, back to Available; required stay; other version asks",
                this::dragAcrossLists),
            TestCase.of("Drag: off works like vanilla; the scrollbar still scrolls; the list scrolls near its edge",
                this::dragOffAndScrolling),
            TestCase.of("Search: both lists by name or id, no moving while searching, Done usable, kept on resize; "
                + "off: no box", this::search),
            TestCase.of("Data pack screen: drag and search work, no preset button", this::dataPackScreen),
            TestCase.of("Settings: each option writes its own field; Open Presets; Reset keeps the presets",
                this::settings),
            TestCase.of("Real packs (.local/my/resourcepacks): a preset of the user's packs loads in order",
                this::realPacks),
            TestCase.of("Odd presets files: empty, wrong shapes and types are read or kept aside; text keeps every "
                + "character; a pack id cannot reach outside the pack folder", this::oddFiles),
            TestCase.of("Odd presets: the same packs twice, the preset in use deleted, every pack missing, no packs, "
                + "long text, a missing icon pack, many presets", this::oddPresets),
            TestCase.of("Keyboard: arrows, Enter and Escape on the presets; Enter and Space on a pack ask before "
                + "leaving a preset", this::keyboard),
            TestCase.of("Screen sizes: from 455x256 up every screen keeps its parts on screen and apart",
                this::screenSizes),
            TestCase.of("Resize: the presets, the form, its packs, Review Changed and drags keep their state",
                this::resizeKeeps),
            TestCase.of("Server pack (fixed): never stored, never moved, other packs stay below it", this::serverPack),
            TestCase.of("Options menu in a world: Resource Packs, presets, Done and Escape save like the game",
                this::fromOptions),
            TestCase.of("Restart: the presets and the preset in use come back; reading never writes the file",
                this::restart),
            TestCase.of("Pack files: same size other content, the same content again, a folder named .zip; the "
                + "preset button updates by itself", this::oddPackFiles),
            TestCase.of("Data pack screen: never asks about leaving a preset, the resource packs stay",
                this::dataPackNeverAsks),
            TestCase.of("Drag: a drop outside the lists changes nothing, the list scrolls up near its top, a pack "
                + "deleted while dragged is not brought back", this::dragEdgeCases),
            TestCase.of("Wording: a preset row counts 1 pack, N packs; Use Current names both counts",
                this::wording)
        );
    }

    @Override
    public void cleanUp(TestContext t) throws IOException {
        deletePresetFiles();
    }

    // ---- state of a test ----------------------------------------------------------------------------------------

    /** The game's pack choice, High Contrast and GUI scale before a test */
    private record Saved(List<String> selected, List<String> options, boolean highContrast, int guiScale) {}

    /**
     * Saves the game's state, deletes the presets files (and the presets the game read from them) and sets GUI
     * scale 1 (every row on screen)
     */
    private static Saved start(TestContext t) throws IOException {
        deletePresetFiles();
        return t.onClientThread(() -> {
            PackManagerInternals.loadPresets();
            Minecraft minecraft = t.mc();
            Saved saved = new Saved(List.copyOf(minecraft.getResourcePackRepository().getSelectedIds()),
                List.copyOf(minecraft.options.resourcePacks), minecraft.options.highContrast().get(),
                minecraft.options.guiScale().get());
            minecraft.options.guiScale().set(1);
            minecraft.resizeDisplay();
            return saved;
        });
    }

    /**
     * Puts the game's packs, High Contrast and GUI scale back, waits if the resources reload, and stops the folder
     * watchers of the pack screens the test opened
     */
    private static void restore(TestContext t, Saved saved) {
        t.onClientThread(() -> {
            Minecraft minecraft = t.mc();
            minecraft.setScreen(null);
            OPENED.forEach(screen -> ((PackScreenAccessor) screen).devtest$closeWatcher());
            OPENED.clear();
            PackRepository repository = minecraft.getResourcePackRepository();
            repository.reload();
            repository.setSelected(saved.selected());
            minecraft.options.highContrast().set(saved.highContrast());
            minecraft.options.updateResourcePacks(repository);
            minecraft.options.guiScale().set(saved.guiScale());
            minecraft.resizeDisplay();
            return null;
        });
        waitForReload(t);
    }

    private static void waitForReload(TestContext t) {
        t.waitUntil("the resources reload", () -> t.mc().getOverlay() == null, 20 * 120);
        t.ticks(2);
    }

    // ---- packs ----------------------------------------------------------------------------------------------------

    private static String id(String name) {
        return "file/" + PREFIX + name;
    }

    /** Makes test packs for 1.21.1 (removed after the test) and returns their ids */
    private static List<String> makePacks(TestContext t, String... names) throws IOException {
        List<String> ids = new ArrayList<>();
        for (String name : names) {
            t.resourcePack(name);
            ids.add(id(name));
        }
        return ids;
    }

    /** Makes a test pack for an old version (format 15) and returns its id */
    private static String makeOldPack(TestContext t, String name) throws IOException {
        t.resourcePack(name).text("pack.mcmeta", "{\"pack\":{\"pack_format\":15,\"description\":\"old\"}}");
        return id(name);
    }

    /** Opens the Resource Packs screen as the Options screen does: Done saves the packs and loads them */
    private static PackSelectionScreen openPackScreen(TestContext t) {
        t.openScreen(() -> new PackSelectionScreen(t.mc().getResourcePackRepository(), repository -> {
            t.mc().options.updateResourcePacks(repository);
            t.mc().setScreen(null);
        }, t.mc().getResourcePackDirectory(), Component.translatable("resourcePack.title")));
        t.require(t.mc().screen instanceof PackSelectionScreen, "The Resource Packs screen did not open");
        PackSelectionScreen screen = (PackSelectionScreen) t.mc().screen;
        OPENED.add(screen);
        return screen;
    }

    private static PackSelectionModel model(PackSelectionScreen screen) {
        return ((PackScreenAccessor) screen).devtest$getModel();
    }

    private static TransferableSelectionList selectedList(PackSelectionScreen screen) {
        return ((PackScreenAccessor) screen).devtest$getSelectedPackList();
    }

    private static TransferableSelectionList availableList(PackSelectionScreen screen) {
        return ((PackScreenAccessor) screen).devtest$getAvailablePackList();
    }

    /** Turns packs on in the screen, one after the other, as a click on their arrow does */
    private static void select(TestContext t, PackSelectionScreen screen, String... ids) {
        t.onClientThread(() -> {
            for (String id : ids) {
                model(screen).getUnselected().filter(entry -> entry.getId().equals(id)).findFirst()
                    .ifPresent(entry -> entry.select());
            }
            return null;
        });
    }

    /** Returns the screen's Selected list, top first */
    private static List<String> selected(TestContext t, PackSelectionScreen screen) {
        return t.onClientThread(() -> model(screen).getSelected().map(entry -> entry.getId()).toList());
    }

    /** Returns the ids of a list that are among some ids, in the list's order */
    private static List<String> among(List<String> ids, Collection<String> wanted) {
        return ids.stream().filter(wanted::contains).toList();
    }

    /** Returns the ids a list widget shows, top first */
    private static List<String> shown(TestContext t, TransferableSelectionList list) {
        return t.onClientThread(() -> list.children().stream().map(entry -> entry.getPackId())
            .toList());
    }

    /** Returns how many places a pack is above another in the Selected list (1 = right above it) */
    private static int gap(TestContext t, PackSelectionScreen screen, String upper, String lower) {
        List<String> order = selected(t, screen);
        return order.indexOf(lower) - order.indexOf(upper);
    }

    private static int rowOf(TestContext t, TransferableSelectionList list, String id) {
        return shown(t, list).indexOf(id);
    }

    /** Returns the ids of the packs the game has loaded, lowest first */
    private static List<String> loaded(TestContext t) {
        return t.onClientThread(() -> t.mc().getResourceManager().listPacks().map(pack -> pack.packId()).toList());
    }

    // ---- widgets and mouse ----------------------------------------------------------------------------------------

    private static boolean hasKey(Component text, String key) {
        return text.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    /** Finds a widget of a screen (also inside lists and rows) by the key of its text */
    @Nullable
    private static AbstractWidget widget(@Nullable ContainerEventHandler parent, String key) {
        if (parent == null) {
            return null;
        }
        for (GuiEventListener child : parent.children()) {
            if (child instanceof AbstractWidget found && hasKey(found.getMessage(), key)) {
                return found;
            }
            if (child instanceof ContainerEventHandler container) {
                AbstractWidget found = widget(container, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean exists(TestContext t, String key) {
        return t.onClientThread(() -> widget(t.mc().screen, key) != null);
    }

    /** Presses a usable button of the open screen; returns false if there is none */
    private static boolean press(TestContext t, String key) {
        boolean pressed = t.onClientThread(() -> {
            if (widget(t.mc().screen, key) instanceof AbstractButton button && button.active) {
                button.onPress();
                return true;
            }
            return false;
        });
        t.ticks(2);
        return pressed;
    }

    /** Types a text into a text box of the open screen */
    private static void type(TestContext t, String key, String text) {
        boolean found = t.onClientThread(() -> {
            if (widget(t.mc().screen, key) instanceof EditBox box) {
                box.setValue(text);
                return true;
            }
            return false;
        });
        t.require(found, "No text box " + key);
        t.ticks(1);
    }

    private static boolean titled(TestContext t, String key) {
        return t.onClientThread(() -> t.mc().screen != null && hasKey(t.mc().screen.getTitle(), key));
    }

    /** Returns the first list widget of the open screen (the presets list) */
    private static AbstractSelectionList<?> firstList(TestContext t) {
        // found as a plain listener: the list's row type is not visible here
        GuiEventListener list = t.onClientThread(() -> {
            Screen screen = t.mc().screen;
            if (screen != null) {
                for (GuiEventListener child : screen.children()) {
                    if (child instanceof AbstractSelectionList<?>) {
                        return child;
                    }
                }
            }
            return null;
        });
        t.require(list != null, "The screen has no list");
        return (AbstractSelectionList<?>) list;
    }

    private static int rowCount(TestContext t, AbstractSelectionList<?> list) {
        return t.onClientThread(() -> ((ContainerEventHandler) list).children().size());
    }

    private static double nameX(AbstractSelectionList<?> list) {
        return list.getRowLeft() + NAME_X;
    }

    private static double iconX(AbstractSelectionList<?> list) {
        return list.getRowLeft() + ROW_MIDDLE;
    }

    private static double rowY(TestContext t, AbstractSelectionList<?> list, int index) {
        return t.onClientThread(() -> (double) PackManagerInternals.rowTop(list, index) + ROW_MIDDLE);
    }

    /** Height that drops into the gap above a row (index = row count: below the last row) */
    private static double slotY(TestContext t, AbstractSelectionList<?> list, int slot) {
        return t.onClientThread(() -> (double) PackManagerInternals.rowTop(list, slot) - 2);
    }

    /** Clicks the left mouse button at a point of the open screen */
    private static void click(TestContext t, double x, double y) {
        t.onClientThread(() -> {
            Screen screen = t.mc().screen;
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            return null;
        });
        t.ticks(2);
    }

    /** Presses the mouse at one point and moves it in steps to another, without letting go */
    private static void pressAndMove(TestContext t, double fromX, double fromY, double toX, double toY) {
        t.onClientThread(() -> {
            Screen screen = t.mc().screen;
            screen.mouseClicked(fromX, fromY, 0);
            double lastX = fromX;
            double lastY = fromY;
            for (int step = 1; step <= DRAG_STEPS; step++) {
                double x = fromX + (toX - fromX) * step / DRAG_STEPS;
                double y = fromY + (toY - fromY) * step / DRAG_STEPS;
                screen.mouseDragged(x, y, 0, x - lastX, y - lastY);
                lastX = x;
                lastY = y;
            }
            return null;
        });
    }

    private static void letGo(TestContext t, double x, double y) {
        t.onClientThread(() -> t.mc().screen.mouseReleased(x, y, 0));
        t.ticks(2);
    }

    /** Drags with the mouse from one point to another and lets go */
    private static void drag(TestContext t, double fromX, double fromY, double toX, double toY) {
        pressAndMove(t, fromX, fromY, toX, toY);
        letGo(t, toX, toY);
    }

    /** Drags a pack of a pack list by its name to a place of the Selected list */
    private static void dragToSlot(TestContext t, PackSelectionScreen screen, TransferableSelectionList from,
                                   String id, int slot) {
        TransferableSelectionList to = selectedList(screen);
        drag(t, nameX(from), rowY(t, from, rowOf(t, from, id)), nameX(to), slotY(t, to, slot));
    }

    // ---- presets file ---------------------------------------------------------------------------------------------

    private static Path presetsFile() {
        return PackManagerInternals.presetsFolder().resolve("presets.json");
    }

    private static Path brokenFile() {
        return PackManagerInternals.presetsFolder().resolve("presets.json.broken");
    }

    private static void deletePresetFiles() throws IOException {
        Files.deleteIfExists(presetsFile());
        Files.deleteIfExists(brokenFile());
        Files.deleteIfExists(PackManagerInternals.presetsFolder().resolve("presets.json.tmp"));
    }

    private static JsonObject preset(String name, String... packs) {
        JsonObject preset = new JsonObject();
        preset.addProperty("name", name);
        JsonArray list = new JsonArray();
        for (String pack : packs) {
            list.add(pack);
        }
        preset.add("packs", list);
        return preset;
    }

    private static void writePresets(TestContext t, JsonObject... presets) throws IOException {
        JsonObject root = new JsonObject();
        JsonArray list = new JsonArray();
        for (JsonObject preset : presets) {
            list.add(preset);
        }
        root.add("presets", list);
        Files.createDirectories(PackManagerInternals.presetsFolder());
        Files.writeString(presetsFile(), GSON.toJson(root));
        t.onClientThread(() -> {
            PackManagerInternals.loadPresets(); // as if the game started with this file
            return null;
        });
    }

    private static List<JsonObject> readPresets() throws IOException {
        if (!Files.exists(presetsFile())) {
            return List.of();
        }
        JsonObject root = JsonParser.parseString(Files.readString(presetsFile())).getAsJsonObject();
        List<JsonObject> presets = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("presets")) {
            presets.add(element.getAsJsonObject());
        }
        return presets;
    }

    private static List<String> packsOf(JsonObject preset) {
        List<String> packs = new ArrayList<>();
        preset.getAsJsonArray("packs").forEach(pack -> packs.add(pack.getAsString()));
        return packs;
    }

    private static List<String> names(List<JsonObject> presets) {
        return presets.stream().map(preset -> preset.get("name").getAsString()).toList();
    }

    /** Opens the presets from the open Resource Packs screen with its preset button */
    private static void openPresets(TestContext t) {
        t.require(press(t, KEY + "preset_button"), "No preset button");
        t.require(exists(t, PRESETS + "new"), "The presets screen did not open");
    }

    /** Applies a preset with the arrow on its icon; answers Apply if the custom packs question comes first */
    private static void applyPreset(TestContext t, int index) {
        clickPreset(t, index, true);
        if (asks(t, PRESETS + "replace_custom_title")) {
            press(t, PRESETS + "apply");
        }
    }

    /** Tells whether the open screen is a question with this title */
    private static boolean asks(TestContext t, String titleKey) {
        return t.mc().screen instanceof ConfirmScreen && titled(t, titleKey);
    }

    /** Returns the text of the preset button of the open screen, or "" if it has none */
    private static String presetLabel(TestContext t) {
        return t.onClientThread(() -> widget(t.mc().screen, KEY + "preset_button") instanceof AbstractWidget button
            ? button.getMessage().getString() : "");
    }

    /** Tells whether a button of the open screen is usable */
    private static boolean usable(TestContext t, String key) {
        return t.onClientThread(() -> widget(t.mc().screen, key) instanceof AbstractWidget button && button.active);
    }

    /** Returns the text of a text box of the open screen */
    private static String textOf(TestContext t, String key) {
        return t.onClientThread(() -> widget(t.mc().screen, key) instanceof EditBox box ? box.getValue() : "");
    }

    /** Clicks a preset row: on its icon (applies it) or on its name (selects it) */
    private static void clickPreset(TestContext t, int index, boolean onIcon) {
        AbstractSelectionList<?> list = firstList(t);
        click(t, onIcon ? iconX(list) : nameX(list), rowY(t, list, index));
    }

    /** Clicks the edit, copy or delete button of a preset row */
    private static void rowButton(TestContext t, int index, int button) {
        AbstractSelectionList<?> list = firstList(t);
        click(t, list.getRowLeft() + ROW_BUTTONS_X + button * ROW_BUTTON_STEP + 8.0, rowY(t, list, index));
    }

    /** Clicks twice on a preset row's name, as a double click */
    private static void doubleClickPreset(TestContext t, int index) {
        AbstractSelectionList<?> list = firstList(t);
        double x = nameX(list);
        double y = rowY(t, list, index);
        t.onClientThread(() -> {
            Screen screen = t.mc().screen;
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            screen.mouseClicked(x, y, 0);
            screen.mouseReleased(x, y, 0);
            return null;
        });
        t.ticks(2);
    }

    /** Returns the list widgets of the open screen, in their order */
    private static List<AbstractSelectionList<?>> lists(TestContext t) {
        List<GuiEventListener> found = t.onClientThread(() -> {
            List<GuiEventListener> lists = new ArrayList<>();
            for (GuiEventListener child : t.mc().screen.children()) {
                if (child instanceof AbstractSelectionList<?>) {
                    lists.add(child);
                }
            }
            return lists;
        });
        List<AbstractSelectionList<?>> lists = new ArrayList<>();
        for (GuiEventListener list : found) {
            lists.add((AbstractSelectionList<?>) list);
        }
        return lists;
    }

    // ---- tests: presets -------------------------------------------------------------------------------------------

    private void presetButton(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b");
            writePresets(t, preset("Pair", ids.get(1), ids.get(0)));
            PackSelectionScreen screen = openPackScreen(t);
            boolean placed = t.onClientThread(() -> {
                AbstractWidget preset = widget(t.mc().screen, KEY + "preset_button");
                AbstractWidget search = widget(t.mc().screen, KEY + "search");
                TransferableSelectionList selectedList = selectedList(screen);
                return preset != null && search != null && preset.getX() == selectedList.getX()
                    && search.getX() == availableList(screen).getX() && preset.getY() == search.getY()
                    && preset.getY() + preset.getHeight() < selectedList.getY();
            });
            t.check("the preset button is above Selected, the search box above Available", placed).isTrue();
            boolean vanillaFooter = t.onClientThread(() -> {
                AbstractWidget folder = widget(t.mc().screen, "pack.openFolder");
                AbstractWidget done = widget(t.mc().screen, "gui.done");
                return folder != null && done != null && folder.getWidth() == 150 && done.getWidth() == 150;
            });
            t.check("the bottom row is the game's own", vanillaFooter).isTrue();
            t.check("no preset matches: Custom", presetLabel(t)).isEqualTo("Preset: Custom");
            t.saveScreenshot("resource_packs_screen");

            openPresets(t);
            t.check("the button opens the presets", exists(t, PRESETS + "new")).isTrue();
            applyPreset(t, 0);
            t.check("after applying: the preset's name", presetLabel(t)).isEqualTo("Preset: Pair");
            PackManagerFeature.getConfig().askBeforeLeavingPreset = false;
            select(t, screen, "programmer_art");
            t.check("a change makes the packs custom", presetLabel(t)).isEqualTo("Preset: Custom");
            t.onClientThread(() -> {
                model(screen).getSelected().filter(entry -> entry.getId().equals("programmer_art")).findFirst()
                    .ifPresent(entry -> entry.unselect());
                return null;
            });
            t.check("changed back: the preset's name again", presetLabel(t)).isEqualTo("Preset: Pair");
            t.saveScreenshot("preset_in_use");

            PackManagerFeature.getConfig().packSearch = false;
            openPackScreen(t);
            t.check("search off: the preset button keeps its place", t.onClientThread(() ->
                widget(t.mc().screen, KEY + "preset_button") instanceof AbstractWidget button
                    && button.getX() == selectedList((PackSelectionScreen) t.mc().screen).getX())).isTrue();
            PackManagerFeature.getConfig().packSearch = true;
            PackManagerFeature.getConfig().presets = false;
            PackSelectionScreen off = openPackScreen(t);
            t.check("off: no preset button", exists(t, KEY + "preset_button")).isFalse();
            int withSearch = t.onClientThread(() -> selectedList(off).getY());
            PackManagerFeature.getConfig().packSearch = false;
            PackSelectionScreen bare = openPackScreen(t);
            t.check("both off: the lists are where the game puts them (24 higher)",
                t.onClientThread(() -> selectedList(bare).getY())).isEqualTo(withSearch - 24);
        } finally {
            restore(t, saved);
        }
    }

    private void applyOrder(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "c", "d");
            String a = ids.get(0);
            String c = ids.get(2);
            String d = ids.get(3);
            List<String> big = makePacks(t, "p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9");
            writePresets(t, preset("Order", c, "vanilla", a), preset("Ten", big.toArray(String[]::new)),
                preset("Ten reversed", big.reversed().toArray(String[]::new)));
            List<String> watched = new ArrayList<>(ids);
            watched.add("vanilla");

            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, d);
            openPresets(t);
            clickPreset(t, 0, true);
            t.check("custom packs: applying asks first", asks(t, PRESETS + "replace_custom_title")).isTrue();
            t.saveScreenshot("apply_over_custom_packs");
            press(t, "gui.cancel");
            t.check("Cancel: back on the presets", exists(t, PRESETS + "new")).isTrue();
            t.check("Cancel: nothing changed", among(selected(t, screen), watched)).isEqualTo(List.of(d, "vanilla"));
            clickPreset(t, 0, true);
            press(t, PRESETS + "apply");
            t.check("applying goes back to the Resource Packs screen", t.mc().screen == screen).isTrue();
            t.check("Selected holds the preset's packs in its order (d turned off)",
                among(selected(t, screen), watched)).isEqualTo(List.of(c, "vanilla", a));
            boolean requiredOn = t.onClientThread(() -> model(screen).getUnselected()
                .noneMatch(entry -> entry.isRequired()));
            t.check("every required pack is still on", requiredOn).isTrue();
            t.check("nothing is saved before Done (options.txt)", t.onClientThread(() ->
                List.copyOf(t.mc().options.resourcePacks))).isEqualTo(saved.options());
            t.check("nothing is loaded before Done", loaded(t).contains(c)).isFalse();
            t.saveScreenshot("applied_before_done");

            openPresets(t);
            t.check("the applied preset is marked Active",
                t.onClientThread(() -> PackManagerInternals.isActive(screen, 0))).isTrue();
            t.check("the other presets are not Active",
                t.onClientThread(() -> PackManagerInternals.isActive(screen, 1))).isFalse();
            t.saveScreenshot("presets_active");
            press(t, "gui.back");
            press(t, "gui.done");
            waitForReload(t);
            List<String> lowestFirst = List.of(a, "vanilla", c);
            t.check("Done: options.txt holds the packs, lowest first", among(t.onClientThread(() ->
                List.copyOf(t.mc().options.resourcePacks)), watched)).isEqualTo(lowestFirst);
            t.check("Done: the game loaded the packs in that order", among(loaded(t), watched))
                .isEqualTo(lowestFirst);

            // ten packs, then the same ten upside down: every step goes through the screen's move up / move down
            PackSelectionScreen again = openPackScreen(t);
            openPresets(t);
            clickPreset(t, 1, true);
            t.check("a preset in use: applying another does not ask", t.mc().screen == again).isTrue();
            t.check("ten packs in order", among(selected(t, again), big)).isEqualTo(big);
            openPresets(t);
            AbstractSelectionList<?> list = firstList(t);
            double x = iconX(list);
            double y = rowY(t, list, 2);
            long nanos = t.onClientThread(() -> {
                long begin = System.nanoTime();
                t.mc().screen.mouseClicked(x, y, 0);
                return System.nanoTime() - begin;
            });
            t.ticks(2);
            t.check("ten packs upside down", among(selected(t, again), big)).isEqualTo(big.reversed());
            t.note("time to turn ten packs upside down (ms)", nanos / 1_000_000.0);
        } finally {
            restore(t, saved);
        }
    }

    private void applyMissing(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "c");
            String a = ids.get(0);
            String b = ids.get(1);
            String c = ids.get(2);
            String gone = id("gone");
            writePresets(t, preset("With missing", b, gone, c));
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, a);
            openPresets(t);
            int[] counts = t.onClientThread(() -> PackManagerInternals.problemCounts(screen, 0));
            t.check("the row counts 1 missing pack", counts[0]).isEqualTo(1);
            t.check("the row counts no pack for another version", counts[1]).isEqualTo(0);
            t.saveScreenshot("presets_missing");
            applyPreset(t, 0);
            t.check("a pack not in the preset is turned off; the missing one is skipped",
                among(selected(t, screen), List.of(a, b, c, gone))).isEqualTo(List.of(b, c));
            boolean toast = t.onClientThread(() -> t.mc().getToasts().getToast(SystemToast.class,
                SystemToast.SystemToastId.PACK_LOAD_FAILURE) != null);
            t.check("a message names the missing packs", toast).isTrue();
            t.saveScreenshot("missing_message");
            openPresets(t);
            t.check("Active although a pack is missing (the others match)",
                t.onClientThread(() -> PackManagerInternals.isActive(screen, 0))).isTrue();
        } finally {
            restore(t, saved);
        }
    }

    private void applyIncompatible(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            String a = makePacks(t, "a").getFirst();
            String old = makeOldPack(t, "old");
            writePresets(t, preset("Old", old, a), preset("Every note", old, a, id("gone1"), id("gone2"), id("gone3"),
                id("gone4"), id("gone5"), id("gone6"), id("gone7"), id("gone8"), id("gone9"), id("gone10")));
            PackSelectionScreen screen = openPackScreen(t);
            openPresets(t);
            int[] counts = t.onClientThread(() -> PackManagerInternals.problemCounts(screen, 0));
            t.check("the row counts 1 pack for another version", counts[1]).isEqualTo(1);
            t.saveScreenshot("presets_every_note");
            applyPreset(t, 0);
            t.check("applying asks first", asks(t, "pack.incompatible.confirm.title")).isTrue();
            t.saveScreenshot("apply_confirm_other_version");
            press(t, "gui.no");
            t.check("No: back on the presets", exists(t, PRESETS + "new")).isTrue();
            t.check("No: nothing changed", among(selected(t, screen), List.of(a, old))).isEqualTo(List.of());
            applyPreset(t, 0);
            press(t, "gui.yes");
            t.check("Yes: back on the Resource Packs screen", t.mc().screen == screen).isTrue();
            t.check("Yes: applied in order", among(selected(t, screen), List.of(a, old))).isEqualTo(List.of(old, a));
            openPresets(t);
            clickPreset(t, 0, true);
            t.check("already on: applied without asking", t.mc().screen == screen).isTrue();
        } finally {
            restore(t, saved);
        }
    }

    private void presetForm(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b");
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, ids.get(0), ids.get(1));
            openPresets(t);
            t.check("no presets: empty list", rowCount(t, firstList(t))).isEqualTo(0);
            t.saveScreenshot("presets_empty");
            t.check("the bottom row: New from Current, Open Folder, Back only", exists(t, PRESETS + "new")
                && exists(t, PRESETS + "folder") && exists(t, "gui.back") && !exists(t, PRESETS + "apply")
                && !exists(t, PRESETS + "edit") && !exists(t, PRESETS + "copy") && !exists(t, PRESETS + "delete"))
                .isTrue();

            press(t, PRESETS + "new");
            t.check("New from Current opens the form", exists(t, KEY + "edit.create")).isTrue();
            t.check("the form starts with a name", textOf(t, KEY + "edit.preset_name")).isEqualTo("New Preset");
            t.check("nothing is saved yet", readPresets().size()).isEqualTo(0);
            press(t, "gui.cancel");
            t.check("Cancel without changes: back, no question", exists(t, PRESETS + "new")).isTrue();
            t.check("Cancel: nothing saved", readPresets().size()).isEqualTo(0);

            press(t, PRESETS + "new");
            type(t, KEY + "edit.preset_name", "Building");
            press(t, "gui.cancel");
            t.check("Cancel after a change asks", asks(t, KEY + "edit.discard_title")).isTrue();
            t.saveScreenshot("form_discard_question");
            press(t, KEY + "edit.keep_editing");
            t.check("Keep Editing: the form keeps the text", textOf(t, KEY + "edit.preset_name"))
                .isEqualTo("Building");
            type(t, KEY + "edit.preset_name", "");
            t.check("no name: Create is off", usable(t, KEY + "edit.create")).isFalse();
            type(t, KEY + "edit.preset_name", "Building");
            type(t, KEY + "edit.description", "Bright and clean");
            boolean cycled = t.onClientThread(() -> {
                // the button shows its value: Auto at first
                if (widget(t.mc().screen, KEY + "edit.icon.auto") instanceof CycleButton<?> icon) {
                    icon.onPress();
                    return true;
                }
                return false;
            });
            t.require(cycled, "No icon button");
            t.saveScreenshot("form_new");
            t.check("still nothing saved before Create", readPresets().size()).isEqualTo(0);
            List<String> current = t.onClientThread(() -> model(screen).getSelected()
                .filter(entry -> !entry.isFixedPosition()).map(entry -> entry.getId()).toList());
            press(t, KEY + "edit.create");
            t.check("Create goes back to the presets", exists(t, PRESETS + "new")).isTrue();
            List<JsonObject> presets = readPresets();
            t.check("Create saves one preset", presets.size()).isEqualTo(1);
            JsonObject created = presets.getFirst();
            t.check("name", created.get("name").getAsString()).isEqualTo("Building");
            t.check("description", created.get("description").getAsString()).isEqualTo("Bright and clean");
            t.check("icon: the first pack", created.get("icon").getAsString()).isEqualTo(current.getFirst());
            t.check("packs: the Selected list, top first", packsOf(created)).isEqualTo(current);
            AbstractSelectionList<?> presetList = firstList(t);
            t.check("the new preset is selected", t.onClientThread(() ->
                PackManagerInternals.selectedRow(presetList))).isEqualTo(0);
            t.saveScreenshot("presets_row_buttons");

            rowButton(t, 0, COPY);
            t.check("Copy opens the form for a new preset", exists(t, KEY + "edit.create")).isTrue();
            t.check("Copy: named as a copy", textOf(t, KEY + "edit.preset_name")).isEqualTo("Building (Copy)");
            t.check("Copy: nothing saved before Create", readPresets().size()).isEqualTo(1);
            press(t, KEY + "edit.create");
            presets = readPresets();
            t.check("Copy: names", names(presets)).isEqualTo(List.of("Building", "Building (Copy)"));
            t.check("Copy: same packs", packsOf(presets.get(1))).isEqualTo(packsOf(presets.get(0)));
            t.check("Copy: same description and icon", presets.get(1).get("description").equals(
                presets.get(0).get("description")) && presets.get(1).get("icon").equals(presets.get(0).get("icon")))
                .isTrue();
            t.check("Copy: its own id", presets.get(1).get("id").equals(presets.get(0).get("id"))).isFalse();

            rowButton(t, 0, EDIT);
            t.check("the edit button opens the form", exists(t, KEY + "edit.save")).isTrue();
            t.check("no change: Save is off", usable(t, KEY + "edit.save")).isFalse();
            type(t, KEY + "edit.preset_name", "Renamed");
            t.check("a change: Save is on", usable(t, KEY + "edit.save")).isTrue();
            t.check("not saved before Save", names(readPresets()).getFirst()).isEqualTo("Building");
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape after a change asks", asks(t, KEY + "edit.discard_title")).isTrue();
            press(t, KEY + "edit.discard");
            t.check("Discard: back on the presets, the name unchanged", exists(t, PRESETS + "new")
                && names(readPresets()).getFirst().equals("Building")).isTrue();
            doubleClickPreset(t, 0);
            t.check("a double click opens the form", exists(t, KEY + "edit.save")).isTrue();
            type(t, KEY + "edit.preset_name", "Renamed");
            press(t, KEY + "edit.save");
            t.check("Save keeps the change", names(readPresets())).isEqualTo(List.of("Renamed", "Building (Copy)"));

            rowButton(t, 1, DELETE);
            t.check("the delete button asks first", t.mc().screen instanceof ConfirmScreen).isTrue();
            press(t, "gui.no");
            t.check("No: both stay", readPresets().size()).isEqualTo(2);
            clickPreset(t, 1, false);
            t.pressKey(GLFW.GLFW_KEY_DELETE);
            t.check("the Delete key asks too", t.mc().screen instanceof ConfirmScreen).isTrue();
            press(t, "gui.yes");
            t.check("Yes: the copy is gone", names(readPresets())).isEqualTo(List.of("Renamed"));
            t.check("back on the presets", exists(t, PRESETS + "new")).isTrue();
        } finally {
            restore(t, saved);
        }
    }

    private void orderPresets(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            writePresets(t, preset("A"), preset("B"), preset("C"));
            openPackScreen(t);
            openPresets(t);
            AbstractSelectionList<?> list = firstList(t);
            double x = nameX(list);
            drag(t, x, rowY(t, list, 2), x, slotY(t, list, 0));
            t.check("C dragged to the top", names(readPresets())).isEqualTo(List.of("C", "A", "B"));
            drag(t, x, rowY(t, list, 0), x, slotY(t, list, 3));
            t.check("C dragged below the last", names(readPresets())).isEqualTo(List.of("A", "B", "C"));
            click(t, x, rowY(t, list, 1));
            t.check("a click does not move", names(readPresets())).isEqualTo(List.of("A", "B", "C"));
            pressAndMove(t, x, rowY(t, list, 0), x, slotY(t, list, 3));
            t.ticks(2);
            t.saveScreenshot("presets_dragging");
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape keeps the presets screen open", exists(t, PRESETS + "new")).isTrue();
            letGo(t, x, slotY(t, list, 3));
            t.check("Escape cancels the drag", names(readPresets())).isEqualTo(List.of("A", "B", "C"));
        } finally {
            restore(t, saved);
        }
    }

    private void presetsFile(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            Files.createDirectories(PackManagerInternals.presetsFolder());
            Files.writeString(presetsFile(), "{ broken");
            openPackScreen(t);
            openPresets(t);
            t.check("broken file: no presets", rowCount(t, firstList(t))).isEqualTo(0);
            t.check("broken file: kept as presets.json.broken", Files.exists(brokenFile())
                && Files.readString(brokenFile()).equals("{ broken")).isTrue();
            press(t, PRESETS + "new");
            press(t, KEY + "edit.create");
            t.check("a new preset makes a new file", readPresets().size()).isEqualTo(1);
            t.check("the broken file is never written over", Files.readString(brokenFile())).isEqualTo("{ broken");

            Files.writeString(presetsFile(), "{\"presets\":[{\"name\":\"\",\"packs\":[\"x\",\"x\",null]},"
                + "{\"id\":\"same\",\"name\":\"A\"},{\"id\":\"same\",\"name\":\"B\"}]}");
            press(t, "gui.back");
            openPresets(t);
            t.check("hand edits show up when the presets open", rowCount(t, firstList(t))).isEqualTo(3);
            rowButton(t, 2, COPY);
            press(t, KEY + "edit.create");
            List<JsonObject> presets = readPresets();
            t.check("empty name repaired", presets.getFirst().get("name").getAsString()).isEqualTo("Preset");
            t.check("repeated and empty packs dropped", packsOf(presets.getFirst())).isEqualTo(List.of("x"));
            t.check("missing pack list repaired", packsOf(presets.get(1))).isEqualTo(List.of());
            t.check("every id is set and different", presets.stream().map(preset -> preset.get("id").getAsString())
                .filter(id -> !id.isBlank()).distinct().count()).isEqualTo(4L);

            String before = Files.readString(presetsFile());
            t.require(t.openSettings(id()), "The settings screen did not open");
            press(t, "firstone-framework.config.reset");
            press(t, "gui.yes");
            t.check("Reset keeps the presets file", Files.readString(presetsFile())).isEqualTo(before);
        } finally {
            restore(t, saved);
        }
    }

    private void editPacks(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "c", "d");
            String a = ids.get(0);
            String b = ids.get(1);
            String c = ids.get(2);
            String d = ids.get(3);
            String gone = id("gone");
            writePresets(t, preset("Edit me", a, gone, b));
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, c);
            press(t, "gui.done");
            waitForReload(t);
            List<String> gameBefore = t.onClientThread(() ->
                List.copyOf(t.mc().getResourcePackRepository().getSelectedIds()));
            List<String> optionsBefore = t.onClientThread(() -> List.copyOf(t.mc().options.resourcePacks));
            boolean contrastBefore = t.onClientThread(() -> t.mc().options.highContrast().get());
            List<String> fileBefore = packsOf(readPresets().getFirst());

            PackSelectionScreen packScreen = openPackScreen(t);
            openPresets(t);
            rowButton(t, 0, EDIT);
            List<AbstractSelectionList<?>> columns = lists(t);
            t.check("two columns: the options on the left, the packs on the right", columns.size() == 2
                && columns.get(0).getRowLeft() + columns.get(0).getRowWidth() < columns.get(1).getX()).isTrue();
            t.check("the right column lists every pack, missing ones too", t.onClientThread(() ->
                ((ContainerEventHandler) columns.get(1)).children().size())).isEqualTo(3);
            t.saveScreenshot("form_two_columns");
            t.require(press(t, KEY + "edit.edit_packs.button"), "No Edit Packs button");
            t.require(t.mc().screen instanceof PackSelectionScreen, "Edit Packs did not open the pack screen");
            PackSelectionScreen editor = (PackSelectionScreen) t.mc().screen;
            t.check("the pack screen shows the form's packs", among(selected(t, editor), ids))
                .isEqualTo(List.of(a, b));
            t.check("no preset button there", exists(t, KEY + "preset_button")).isFalse();
            t.check("a Cancel button there", exists(t, "gui.cancel")).isTrue();
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape without changes: back to the form, no question", exists(t, KEY + "edit.save"))
                .isTrue();

            press(t, KEY + "edit.edit_packs.button");
            PackSelectionScreen editor2 = (PackSelectionScreen) t.mc().screen;
            chooseInEditor(t, editor2, b, d);
            t.check("changes in this screen never ask about leaving a preset", t.mc().screen == editor2).isTrue();
            press(t, "gui.cancel");
            t.check("Cancel after changes asks", asks(t, KEY + "edit_packs.discard_title")).isTrue();
            press(t, KEY + "edit_packs.keep_editing");
            t.check("Keep Editing: still choosing, the changes kept", t.mc().screen == editor2
                && among(selected(t, editor2), ids).equals(List.of(d, b, a))).isTrue();
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape after changes asks", asks(t, KEY + "edit_packs.discard_title")).isTrue();
            press(t, KEY + "edit_packs.discard");
            t.check("Discard: back to the form", exists(t, KEY + "edit.save")).isTrue();
            t.check("Discard: the form's packs unchanged (Save off)", usable(t, KEY + "edit.save")).isFalse();

            press(t, KEY + "edit.edit_packs.button");
            chooseInEditor(t, (PackSelectionScreen) t.mc().screen, b, d);
            press(t, "gui.done");
            t.check("Done goes back to the form", exists(t, KEY + "edit.save")).isTrue();
            t.check("Done: not saved yet", packsOf(readPresets().getFirst())).isEqualTo(fileBefore);
            t.check("Done: Save is on", usable(t, KEY + "edit.save")).isTrue();
            t.saveScreenshot("form_edit_missing");
            press(t, KEY + "edit.save");
            List<String> packs = packsOf(readPresets().getFirst());
            t.check("Save: the chosen order; the missing pack stays below the pack above it",
                among(packs, List.of(a, b, c, d, gone))).isEqualTo(List.of(d, b, a, gone));
            t.check("the chosen High Contrast pack is in the preset", packs.contains("high_contrast")).isTrue();
            List<String> unknown = t.onClientThread(() -> {
                List<String> listed = Stream.concat(model(packScreen).getSelected(), model(packScreen).getUnselected())
                    .map(entry -> entry.getId()).toList();
                return packs.stream().filter(pack -> !listed.contains(pack) && !pack.equals(gone)).toList();
            });
            t.check("the preset holds only packs the screen lists (none Fabric hides)", unknown).isEqualTo(List.of());
            t.check("the game's packs are as before", t.onClientThread(() ->
                List.copyOf(t.mc().getResourcePackRepository().getSelectedIds()))).isEqualTo(gameBefore);
            t.check("options.txt is as before", t.onClientThread(() -> List.copyOf(t.mc().options.resourcePacks)))
                .isEqualTo(optionsBefore);
            t.check("High Contrast option is as before", t.onClientThread(() ->
                t.mc().options.highContrast().get())).isEqualTo(contrastBefore);
            t.check("no resources were loaded", t.mc().getOverlay() == null).isTrue();
            t.check("the Resource Packs screen still shows the game's packs", among(selected(t, packScreen),
                ids)).isEqualTo(List.of(c));

            rowButton(t, 0, EDIT);
            press(t, KEY + "edit.remove_missing.button");
            t.check("Remove Missing asks first", asks(t, KEY + "edit.remove_missing.title")).isTrue();
            press(t, "gui.cancel");
            t.check("Cancel: the form unchanged (Save off)", usable(t, KEY + "edit.save")).isFalse();
            press(t, KEY + "edit.remove_missing.button");
            press(t, KEY + "edit.remove_missing.confirm");
            press(t, KEY + "edit.save");
            t.check("Remove Missing, then Save", packsOf(readPresets().getFirst()).contains(gone)).isFalse();

            rowButton(t, 0, EDIT);
            press(t, KEY + "edit.use_current.button");
            t.check("Use Current asks first", asks(t, KEY + "edit.use_current.title")).isTrue();
            t.saveScreenshot("use_current_question");
            press(t, "gui.cancel");
            t.check("Cancel: the form unchanged (Save off)", usable(t, KEY + "edit.save")).isFalse();
            press(t, KEY + "edit.use_current.button");
            press(t, KEY + "edit.use_current.confirm");
            t.ticks(2);
            t.check("the packs are the current ones: Use Current is off", usable(t, KEY + "edit.use_current.button"))
                .isFalse();
            press(t, KEY + "edit.save");
            t.check("Use Current, then Save", among(packsOf(readPresets().getFirst()), ids)).isEqualTo(List.of(c));
        } finally {
            restore(t, saved);
        }
    }

    /** In the pack screen of the preset form: turns d and High Contrast on and moves b one up */
    private static void chooseInEditor(TestContext t, PackSelectionScreen editor, String b, String d) {
        select(t, editor, d, "high_contrast");
        t.onClientThread(() -> {
            model(editor).getSelected().filter(entry -> entry.getId().equals(b)).findFirst()
                .ifPresent(entry -> entry.moveUp());
            return null;
        });
        t.ticks(2);
    }

    private void leavePreset(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "c");
            String a = ids.get(0);
            String b = ids.get(1);
            String c = ids.get(2);
            writePresets(t, preset("Pair", b, a));
            PackSelectionScreen screen = openPackScreen(t);
            openPresets(t);
            applyPreset(t, 0);
            t.require(presetLabel(t).equals("Preset: Pair"), "The preset is not in use");
            TransferableSelectionList available = availableList(screen);
            TransferableSelectionList selectedList = selectedList(screen);

            click(t, iconX(available), rowY(t, available, rowOf(t, available, c)));
            t.check("turning a pack on asks first", asks(t, KEY + "leave_preset.title")).isTrue();
            t.saveScreenshot("leave_preset_question");
            press(t, "gui.cancel");
            t.check("Cancel: back, nothing changed", t.mc().screen == screen
                && among(selected(t, screen), ids).equals(List.of(b, a))).isTrue();
            t.check("Cancel: still the preset", presetLabel(t)).isEqualTo("Preset: Pair");

            click(t, iconX(available), rowY(t, available, rowOf(t, available, c)));
            press(t, KEY + "leave_preset.change");
            t.check("Change Packs: the pack is on", among(selected(t, screen), ids)).isEqualTo(List.of(c, b, a));
            t.check("Change Packs: custom now", presetLabel(t)).isEqualTo("Preset: Custom");
            t.onClientThread(() -> {
                model(screen).getSelected().filter(entry -> entry.getId().equals(c)).findFirst()
                    .ifPresent(entry -> entry.unselect());
                return null;
            });
            t.ticks(2);
            t.check("custom packs: a change does not ask", t.mc().screen == screen).isTrue();
            t.check("back to the preset's packs: the preset again", presetLabel(t)).isEqualTo("Preset: Pair");

            t.onClientThread(() -> {
                model(screen).getSelected().filter(entry -> entry.getId().equals(a)).findFirst()
                    .ifPresent(entry -> entry.moveUp());
                return null;
            });
            t.ticks(2);
            t.check("moving a pack one step (arrow, Shift+arrow) asks first", asks(t, KEY + "leave_preset.title"))
                .isTrue();
            press(t, "gui.cancel");
            t.check("Cancel: not moved", among(selected(t, screen), ids)).isEqualTo(List.of(b, a));

            dragToSlot(t, screen, selectedList, a, 0);
            t.check("dropping a dragged pack asks first", asks(t, KEY + "leave_preset.title")).isTrue();
            press(t, "gui.cancel");
            t.check("Cancel: not moved", among(selected(t, screen), ids)).isEqualTo(List.of(b, a));
            dragToSlot(t, screen, selectedList, a, 0);
            press(t, KEY + "leave_preset.change");
            t.check("Change Packs: the drop is made once", among(selected(t, screen), ids))
                .isEqualTo(List.of(a, b));
            t.check("Change Packs: lands on top", selected(t, screen).getFirst()).isEqualTo(a);

            openPresets(t);
            applyPreset(t, 0);
            PackManagerFeature.getConfig().askBeforeLeavingPreset = false;
            click(t, iconX(available), rowY(t, available, rowOf(t, available, c)));
            t.check("off: no question, the pack is on", t.mc().screen == screen
                && selected(t, screen).contains(c)).isTrue();
        } finally {
            restore(t, saved);
        }
    }

    /**
     * Writes a zip pack for 1.21.1 into the resource pack folder; the description changes its size and content, the
     * same description gives the same bytes (the entry's time is fixed)
     */
    private static Path writeZipPack(TestContext t, String name, String description) throws IOException {
        Path file = t.mc().getResourcePackDirectory().resolve(PREFIX + name + ".zip");
        writeZipFile(file, description);
        return file;
    }

    /** Returns how many of the preset's packs are changed and not accepted, as its row counts them */
    private static int changedCount(TestContext t, PackSelectionScreen screen, int index) {
        return t.onClientThread(() -> PackManagerInternals.problemCounts(screen, index))[2];
    }

    /** Clicks the row of a pack in the Review Changed screen (ticks it or clears its tick) */
    private static void tickChanged(TestContext t, int row) {
        AbstractSelectionList<?> list = firstList(t);
        click(t, nameX(list), t.onClientThread(() -> (double) PackManagerInternals.rowTop(list, row) + 10));
    }

    private void changedFiles(TestContext t) throws Exception {
        Saved saved = start(t);
        Path zip1 = writeZipPack(t, "zipped1", "first");
        Path zip2 = writeZipPack(t, "zipped2", "second");
        String one = "file/" + zip1.getFileName();
        String two = "file/" + zip2.getFileName();
        List<String> zips = List.of(one, two);
        try {
            String folder = makePacks(t, "folder").getFirst();
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, one, two, folder);
            openPresets(t);
            press(t, PRESETS + "new");
            press(t, KEY + "edit.create");
            JsonObject preset = readPresets().getFirst();
            JsonObject files = preset.getAsJsonObject("files");
            List<String> order = packsOf(preset);
            t.check("Create keeps each zip pack's size and SHA-1", zips.stream().allMatch(id -> files.has(id)
                && files.get(id).getAsString().matches("\\d+:[0-9a-f]{40}"))).isTrue();
            t.check("folder packs are not checked", files.has(folder)).isFalse();
            t.check("the same files: not changed", changedCount(t, screen, 0)).isEqualTo(0);

            // another file under the same name, then the old one put back: not changed any more
            writeZipPack(t, "zipped1", "another file with the same name");
            press(t, "gui.back");
            openPresets(t);
            t.check("another file with the same name: changed", changedCount(t, screen, 0)).isEqualTo(1);
            writeZipPack(t, "zipped1", "first");
            t.check("the old file put back: not changed", changedCount(t, screen, 0)).isEqualTo(0);

            writeZipPack(t, "zipped1", "another file with the same name");
            writeZipPack(t, "zipped2", "another second file");
            press(t, "gui.back");
            openPresets(t);
            t.check("both replaced: 2 changed", changedCount(t, screen, 0)).isEqualTo(2);
            t.saveScreenshot("presets_changed_files");
            rowButton(t, 0, COPY);
            press(t, KEY + "edit.create");
            t.check("a copy keeps the changed state (Create accepts nothing)", changedCount(t, screen, 1))
                .isEqualTo(2);
            rowButton(t, 1, DELETE);
            press(t, "gui.yes");

            applyPreset(t, 0);
            t.check("Apply skips the changed packs", among(selected(t, screen), zips)).isEqualTo(List.of());
            t.check("Apply turns the other packs on", selected(t, screen).contains(folder)).isTrue();
            t.check("a message names the skipped packs", t.onClientThread(() -> t.mc().getToasts()
                .getToast(SystemToast.class, SystemToast.SystemToastId.PACK_LOAD_FAILURE) != null)).isTrue();
            t.check("the preset counts as in use (skipped packs left out)", presetLabel(t))
                .isEqualTo("Preset: New Preset");

            openPresets(t);
            rowButton(t, 0, EDIT);
            t.check("changed files alone: Save is off", usable(t, KEY + "edit.save")).isFalse();
            t.require(press(t, KEY + "edit.review_changed.button"), "No Review Changed button");
            AbstractSelectionList<?> review = firstList(t);
            t.check("Review lists only the changed packs", t.onClientThread(() ->
                ((ContainerEventHandler) review).children().size())).isEqualTo(2);
            t.check("nothing ticked: Accept is off", usable(t, KEY + "changed.accept")).isFalse();
            t.saveScreenshot("review_changed");
            tickChanged(t, 1);
            t.check("a tick: Accept is on", usable(t, KEY + "changed.accept")).isTrue();
            press(t, "gui.cancel");
            t.check("Cancel: back to the form, Save still off", exists(t, KEY + "edit.save")
                && !usable(t, KEY + "edit.save")).isTrue();

            press(t, KEY + "edit.review_changed.button");
            tickChanged(t, 1);
            tickChanged(t, 0);
            tickChanged(t, 0);
            press(t, KEY + "changed.accept");
            t.check("Accept: Save is on", usable(t, KEY + "edit.save")).isTrue();
            t.check("Accept: nothing saved before Save", changedCount(t, screen, 0)).isEqualTo(2);
            press(t, "gui.cancel");
            t.check("Cancel after accepting asks", asks(t, KEY + "edit.discard_title")).isTrue();
            press(t, KEY + "edit.discard");
            t.check("Discard: still 2 changed", changedCount(t, screen, 0)).isEqualTo(2);

            rowButton(t, 0, EDIT);
            press(t, KEY + "edit.review_changed.button");
            tickChanged(t, 1);
            press(t, KEY + "changed.accept");
            press(t, KEY + "edit.save");
            t.check("only the ticked file is accepted", changedCount(t, screen, 0)).isEqualTo(1);
            t.check("the order of the packs is kept", packsOf(readPresets().getFirst())).isEqualTo(order);

            rowButton(t, 0, EDIT);
            type(t, KEY + "edit.description", "Edited");
            press(t, KEY + "edit.save");
            t.check("another change saved: the file not accepted stays changed", changedCount(t, screen, 0))
                .isEqualTo(1);

            rowButton(t, 0, EDIT);
            press(t, KEY + "edit.review_changed.button");
            press(t, KEY + "changed.select_all");
            t.check("Select All ticks everything (Select None shows)", exists(t, KEY + "changed.select_none"))
                .isTrue();
            press(t, KEY + "changed.accept");
            press(t, KEY + "edit.save");
            t.check("all accepted: nothing changed", changedCount(t, screen, 0)).isEqualTo(0);
            applyPreset(t, 0);
            t.check("accepted files are applied", among(selected(t, screen), zips)).isEqualTo(
                among(order, zips));

            Files.delete(zip2);
            // the Resource Packs screen notices the deleted file when it watches its folder (20 ticks after a change)
            t.waitUntil("the screen drops the deleted pack", () -> !t.onClientThread(() -> Stream.concat(
                model(screen).getSelected(), model(screen).getUnselected()).anyMatch(entry -> entry.getId()
                .equals(two))), 200);
            press(t, KEY + "preset_button");
            int[] counts = t.onClientThread(() -> PackManagerInternals.problemCounts(screen, 0));
            t.check("a deleted file is missing, not changed", counts[0] == 1 && counts[2] == 0).isTrue();
        } finally {
            restore(t, saved);
            Files.deleteIfExists(zip1);
            Files.deleteIfExists(zip2);
        }
    }

    // ---- tests: drag ----------------------------------------------------------------------------------------------

    private void dragWithinSelected(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "c");
            String a = ids.get(0);
            String b = ids.get(1);
            String c = ids.get(2);
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, a, b, c);
            TransferableSelectionList list = selectedList(screen);
            t.check("start: newest on top", among(selected(t, screen), ids)).isEqualTo(List.of(c, b, a));

            dragToSlot(t, screen, list, c, rowOf(t, list, a) + 1);
            t.check("c dragged below a", among(selected(t, screen), ids)).isEqualTo(List.of(b, a, c));
            t.check("c lands right below a", gap(t, screen, a, c)).isEqualTo(1);
            dragToSlot(t, screen, list, c, rowOf(t, list, b));
            t.check("c dragged above b", among(selected(t, screen), ids)).isEqualTo(List.of(c, b, a));
            t.check("c lands right above b", gap(t, screen, c, b)).isEqualTo(1);
            dragToSlot(t, screen, list, a, rowOf(t, list, b));
            t.check("a dragged one up", among(selected(t, screen), ids)).isEqualTo(List.of(c, a, b));
            t.check("a lands right above b", gap(t, screen, a, b)).isEqualTo(1);
            // dropped on the upper half of a row: the place above that row
            drag(t, nameX(list), rowY(t, list, rowOf(t, list, b)), nameX(list),
                rowY(t, list, rowOf(t, list, a)) - 10);
            t.check("dropped on the upper half of a: right above a", gap(t, screen, b, a)).isEqualTo(1);
            dragToSlot(t, screen, list, b, rowOf(t, list, a) + 1);

            click(t, nameX(list), rowY(t, list, rowOf(t, list, b)));
            t.check("a click does not move", among(selected(t, screen), ids)).isEqualTo(List.of(c, a, b));
            String clicked = t.onClientThread(() -> list.getSelected() == null ? "" : list.getSelected().getPackId());
            t.check("a click selects the row, as in the game", clicked).isEqualTo(b);

            double x = nameX(list);
            pressAndMove(t, x, rowY(t, list, rowOf(t, list, c)), x, slotY(t, list, rowOf(t, list, b) + 1));
            t.ticks(2);
            t.saveScreenshot("dragging_a_pack");
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape keeps the screen open", t.mc().screen == screen).isTrue();
            letGo(t, x, slotY(t, list, rowOf(t, list, b) + 1));
            t.check("Escape cancels the drag", among(selected(t, screen), ids)).isEqualTo(List.of(c, a, b));

            dragToSlot(t, screen, list, "vanilla", 0);
            t.check("a required pack moves like with the arrows", selected(t, screen).getFirst())
                .isEqualTo("vanilla");
        } finally {
            restore(t, saved);
        }
    }

    private void dragAcrossLists(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "d");
            String a = ids.get(0);
            String b = ids.get(1);
            String d = ids.get(2);
            String old = makeOldPack(t, "old");
            List<String> watched = List.of(a, b, d, old);
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, a, b);
            TransferableSelectionList selectedList = selectedList(screen);
            TransferableSelectionList availableList = availableList(screen);

            dragToSlot(t, screen, availableList, d, rowOf(t, selectedList, a));
            t.check("d dragged from Available between b and a", among(selected(t, screen), watched))
                .isEqualTo(List.of(b, d, a));
            t.check("d lands right above a", gap(t, screen, d, a)).isEqualTo(1);

            double centerX = availableList.getX() + availableList.getWidth() / 2.0;
            double centerY = availableList.getY() + availableList.getHeight() / 2.0;
            drag(t, nameX(selectedList), rowY(t, selectedList, rowOf(t, selectedList, b)), centerX, centerY);
            t.check("b dragged to Available is off", among(selected(t, screen), watched)).isEqualTo(List.of(d, a));
            drag(t, nameX(selectedList), rowY(t, selectedList, rowOf(t, selectedList, "vanilla")), centerX,
                centerY);
            t.check("a required pack cannot be turned off", selected(t, screen).contains("vanilla")).isTrue();

            dragToSlot(t, screen, availableList, old, 0);
            t.check("a pack for another version asks first", t.mc().screen instanceof ConfirmScreen).isTrue();
            press(t, "gui.no");
            t.check("No: back, nothing changed", t.mc().screen == screen
                && !selected(t, screen).contains(old)).isTrue();
            dragToSlot(t, screen, availableList, old, 0);
            press(t, "gui.yes");
            t.check("Yes: on, at the place it was dropped", selected(t, screen).getFirst()).isEqualTo(old);
        } finally {
            restore(t, saved);
        }
    }

    private void dragOffAndScrolling(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b");
            String a = ids.get(0);
            String b = ids.get(1);
            PackManagerFeature.getConfig().dragToReorder = false;
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, a, b);
            TransferableSelectionList list = selectedList(screen);
            dragToSlot(t, screen, list, b, rowOf(t, list, a) + 1);
            t.check("off: a drag moves nothing", among(selected(t, screen), ids)).isEqualTo(List.of(b, a));
            dragToSlot(t, screen, availableList(screen), "programmer_art", 0);
            t.check("off: a drag from Available turns nothing on", selected(t, screen).contains("programmer_art"))
                .isFalse();
            click(t, nameX(list), rowY(t, list, rowOf(t, list, a)));
            t.check("off: a click selects the row", t.onClientThread(() -> list.getSelected() != null
                && list.getSelected().getPackId().equals(a))).isTrue();

            PackManagerFeature.getConfig().dragToReorder = true;
            List<String> many = makePacks(t, "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10",
                "s11");
            PackSelectionScreen tall = openPackScreen(t);
            select(t, tall, many.toArray(String[]::new));
            TransferableSelectionList tallList = selectedList(tall);
            List<String> order = selected(t, tall);
            t.require(t.onClientThread(() -> tallList.getMaxScroll() > 0), "The Selected list does not scroll");
            double barX = tallList.getRight() - 3;
            drag(t, barX, tallList.getY() + 10, barX, tallList.getY() + 70);
            t.check("the scrollbar scrolls", t.onClientThread(tallList::getScrollAmount)).atLeast(1);
            t.check("the scrollbar moves no pack", selected(t, tall)).isEqualTo(order);

            t.onClientThread(() -> {
                tallList.setScrollAmount(0);
                return null;
            });
            double x = nameX(tallList);
            pressAndMove(t, x, rowY(t, tallList, 0), x, tallList.getBottom() - 4);
            t.ticks(10);
            t.check("held near the bottom edge, the list scrolls", t.onClientThread(tallList::getScrollAmount))
                .atLeast(40);
            letGo(t, x, tallList.getBottom() - 4);
        } finally {
            restore(t, saved);
        }
    }

    // ---- tests: search, data packs, settings, real packs ----------------------------------------------------------

    private void search(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "alpha_one", "alpha_two", "beta");
            String one = ids.get(0);
            String two = ids.get(1);
            String beta = ids.get(2);
            writePresets(t, preset("One", one));
            // turning beta on after one would leave the preset "One"; that question is tested on its own
            PackManagerFeature.getConfig().askBeforeLeavingPreset = false;
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, one, beta);
            TransferableSelectionList selectedList = selectedList(screen);
            TransferableSelectionList availableList = availableList(screen);
            t.check("on: a search box", exists(t, KEY + "search")).isTrue();
            int listTop = t.onClientThread(selectedList::getY);
            boolean below = t.onClientThread(() -> widget(t.mc().screen, KEY + "search") instanceof EditBox box
                && box.getY() + box.getHeight() < selectedList.getY());
            t.check("the lists start below the box", below).isTrue();

            type(t, KEY + "search", "ALPHA");
            t.saveScreenshot("search_alpha");
            t.check("Selected shows only matching packs (any case)", shown(t, selectedList))
                .isEqualTo(List.of(one));
            t.check("Available shows only matching packs", shown(t, availableList)).isEqualTo(List.of(two));
            t.check("searching", PackManagerInternals.isSearching(t.mc().screen)).isTrue();
            boolean canMove = t.onClientThread(() -> model(screen).getSelected().filter(entry ->
                entry.getId().equals(one)).findFirst().map(entry -> entry.canMoveUp()).orElse(true));
            t.check("no moving up or down while searching", canMove).isFalse();
            dragToSlot(t, screen, availableList, two, 0);
            t.check("no dragging while searching", selected(t, screen).contains(two)).isFalse();
            click(t, iconX(availableList), rowY(t, availableList, 0));
            t.check("turning on still works", selected(t, screen).contains(two)).isTrue();

            type(t, KEY + "search", "programmer_art");
            t.check("found by id", shown(t, availableList)).isEqualTo(List.of("programmer_art"));
            type(t, KEY + "search", "Programmer Art");
            t.check("found by name", shown(t, availableList)).isEqualTo(List.of("programmer_art"));

            type(t, KEY + "search", "zzzz");
            t.check("nothing matches: Selected shows nothing", shown(t, selectedList).size()).isEqualTo(0);
            t.check("Done stays usable", t.onClientThread(() -> widget(t.mc().screen, "gui.done") != null
                && widget(t.mc().screen, "gui.done").active)).isTrue();

            type(t, KEY + "search", "beta");
            t.onClientThread(() -> {
                t.mc().screen.resize(t.mc(), t.mc().getWindow().getGuiScaledWidth(),
                    t.mc().getWindow().getGuiScaledHeight());
                return null;
            });
            t.ticks(2);
            t.check("resized: the text and the filter stay", shown(t, selectedList)).isEqualTo(List.of(beta));
            t.check("resized: the lists stay below the box", t.onClientThread(selectedList::getY)).isEqualTo(listTop);

            type(t, KEY + "search", "");
            t.check("empty: every pack is shown again", shown(t, selectedList)).isEqualTo(selected(t, screen));
            boolean movable = t.onClientThread(() -> model(screen).getSelected().filter(entry ->
                entry.getId().equals(one)).findFirst().map(entry -> entry.canMoveUp()).orElse(false));
            t.check("empty: moving works again", movable).isTrue();

            type(t, KEY + "search", "alpha");
            openPresets(t);
            applyPreset(t, 0);
            t.check("applying a preset empties the search", PackManagerInternals.isSearching(t.mc().screen))
                .isFalse();
            t.check("applying shows every pack", shown(t, selectedList)).isEqualTo(selected(t, screen));

            PackManagerFeature.getConfig().packSearch = false;
            openPackScreen(t);
            t.check("off: no search box", exists(t, KEY + "search")).isFalse();
        } finally {
            restore(t, saved);
        }
    }

    private void dataPackScreen(TestContext t) throws Exception {
        Saved saved = start(t);
        Path folder = t.mc().gameDirectory.toPath().resolve(PREFIX + "datapacks");
        try {
            for (String name : List.of("first", "second")) {
                Path pack = folder.resolve(name);
                Files.createDirectories(pack);
                Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + DATA_PACK_FORMAT
                    + ",\"description\":\"data\"}}");
            }
            PackRepository repository = new PackRepository(new FolderRepositorySource(folder, PackType.SERVER_DATA,
                PackSource.DEFAULT, t.mc().directoryValidator()));
            t.openScreen(() -> new PackSelectionScreen(repository, chosen -> t.mc().setScreen(null), folder,
                Component.literal("Data Packs")));
            t.require(t.mc().screen instanceof PackSelectionScreen, "The data pack screen did not open");
            PackSelectionScreen screen = (PackSelectionScreen) t.mc().screen;
            OPENED.add(screen);
            t.check("no Presets button", exists(t, KEY + "presets_button")).isFalse();
            t.check("a search box", exists(t, KEY + "search")).isTrue();
            dragToSlot(t, screen, availableList(screen), "file/first", 0);
            t.check("dragging turns a data pack on", selected(t, screen).contains("file/first")).isTrue();
            type(t, KEY + "search", "second");
            t.check("search filters", shown(t, availableList(screen))).isEqualTo(List.of("file/second"));
            t.saveScreenshot("data_pack_screen");
        } finally {
            restore(t, saved);
            if (Files.exists(folder)) {
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
        }
    }

    private void settings(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            writePresets(t, preset("Kept"));
            String presetsBefore = Files.readString(presetsFile());
            Path configFile = FabricLoader.getInstance().getConfigDir()
                .resolve("firstone-framework/pack_manager.json");
            t.require(t.openSettings(id()), "The settings screen did not open");
            t.saveScreenshot("settings");
            List<CycleButton<?>> toggles = t.onClientThread(() -> {
                List<CycleButton<?>> found = new ArrayList<>();
                collectToggles(t.mc().screen, found);
                return found;
            });
            t.require(toggles.size() == 4, "Expected 4 toggles, found " + toggles.size());
            // in the order of the screen's rows
            String[] fields = {"presets", "dragToReorder", "packSearch", "askBeforeLeavingPreset"};
            int right = 0;
            for (int index = 0; index < fields.length; index++) {
                CycleButton<?> toggle = toggles.get(index);
                t.onClientThread(() -> {
                    toggle.onPress();
                    return null;
                });
                PackManagerConfig config = PackManagerFeature.getConfig();
                boolean[] values = {config.presets, config.dragToReorder, config.packSearch,
                    config.askBeforeLeavingPreset};
                JsonObject file = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
                boolean ok = true;
                for (int other = 0; other < fields.length; other++) {
                    boolean expected = other > index;
                    ok &= values[other] == expected && file.get(fields[other]).getAsBoolean() == expected;
                }
                right += ok ? 1 : 0;
            }
            t.check("each toggle turns off its own option and saves it", right).isEqualTo(4);
            t.ticks(2);
            t.check("Presets off: Open Presets is not usable", press(t, KEY + "open_presets.button")).isFalse();

            press(t, "firstone-framework.config.reset");
            press(t, "gui.yes");
            PackManagerConfig defaults = new PackManagerConfig();
            PackManagerConfig config = PackManagerFeature.getConfig();
            t.check("Reset restores the options", config.presets == defaults.presets
                && config.dragToReorder == defaults.dragToReorder && config.packSearch == defaults.packSearch
                && config.askBeforeLeavingPreset == defaults.askBeforeLeavingPreset).isTrue();
            t.check("Reset keeps the presets", Files.readString(presetsFile())).isEqualTo(presetsBefore);

            t.ticks(2);
            t.require(press(t, KEY + "open_presets.button"), "Open Presets is not usable");
            t.check("Open Presets shows the presets", exists(t, PRESETS + "new")).isTrue();
            press(t, "gui.back");
            t.check("under them the Resource Packs screen", t.mc().screen instanceof PackSelectionScreen).isTrue();
            press(t, "gui.done");
            waitForReload(t);
            t.check("Done returns to the settings", exists(t, KEY + "open_presets.button")).isTrue();
        } finally {
            restore(t, saved);
        }
    }

    private static void collectToggles(ContainerEventHandler parent, List<CycleButton<?>> found) {
        for (GuiEventListener child : parent.children()) {
            if (child instanceof CycleButton<?> toggle) {
                found.add(toggle);
            } else if (child instanceof ContainerEventHandler container) {
                collectToggles(container, found);
            }
        }
    }

    private void realPacks(TestContext t) throws Exception {
        Path source = t.mc().gameDirectory.toPath().resolve(USER_PACKS).normalize();
        if (!Files.isDirectory(source)) {
            t.note("skipped: no user packs at", source);
            return;
        }
        Saved saved = start(t);
        List<Path> copies = new ArrayList<>();
        try {
            List<Path> zips;
            try (Stream<Path> files = Files.list(source)) {
                zips = files.filter(file -> file.getFileName().toString().endsWith(".zip")).sorted().toList();
            }
            List<String> ids = new ArrayList<>();
            for (Path zip : zips) {
                Path copy = t.mc().getResourcePackDirectory().resolve(PREFIX + "real_" + zip.getFileName());
                Files.copy(zip, copy);
                copies.add(copy);
                ids.add("file/" + copy.getFileName());
            }
            t.note("user packs", zips.size());
            List<String> topFirst = ids.reversed();
            writePresets(t, preset("My packs", topFirst.toArray(String[]::new)));
            PackSelectionScreen screen = openPackScreen(t);
            openPresets(t);
            int[] counts = t.onClientThread(() -> PackManagerInternals.problemCounts(screen, 0));
            t.note("packs for another version", counts[1]);
            t.saveScreenshot("real_presets");
            applyPreset(t, 0);
            if (t.mc().screen instanceof ConfirmScreen) {
                press(t, "gui.yes");
            }
            t.check("the Selected list holds the user's packs in order", among(selected(t, screen), ids))
                .isEqualTo(topFirst);
            t.saveScreenshot("real_packs_selected");
            press(t, "gui.done");
            waitForReload(t);
            t.check("options.txt holds them lowest first", among(t.onClientThread(() ->
                List.copyOf(t.mc().options.resourcePacks)), ids)).isEqualTo(ids);
            t.check("the game loaded them in that order", among(loaded(t), ids)).isEqualTo(ids);
            t.saveScreenshot("real_packs_loaded");

            // the cost of the file check: SHA-1 of every zip pack when a preset is saved, then only from memory
            openPackScreen(t);
            openPresets(t);
            press(t, PRESETS + "new");
            long create = t.onClientThread(() -> {
                long begin = System.nanoTime();
                if (widget(t.mc().screen, KEY + "edit.create") instanceof AbstractButton button) {
                    button.onPress();
                }
                return System.nanoTime() - begin;
            });
            t.ticks(2);
            t.note("Create with " + zips.size() + " zip packs, files read for the first time (ms)",
                create / 1_000_000.0);
            press(t, "gui.back");
            long open = t.onClientThread(() -> {
                long begin = System.nanoTime();
                if (widget(t.mc().screen, KEY + "preset_button") instanceof AbstractButton button) {
                    button.onPress();
                }
                return System.nanoTime() - begin;
            });
            t.ticks(2);
            t.note("opening the presets again, files known (ms)", open / 1_000_000.0);
            t.check("the user's files are not changed", t.onClientThread(() ->
                PackManagerInternals.problemCounts(screen, 1))[2]).isEqualTo(0);
        } finally {
            restore(t, saved);
            for (Path copy : copies) {
                Files.deleteIfExists(copy);
            }
        }
    }

    // ---- tests: odd cases -----------------------------------------------------------------------------------------

    /** Writes the presets file as it is, without reading it (the presets screen reads it when it opens) */
    private static void writeRawPresets(String content) throws IOException {
        Files.createDirectories(PackManagerInternals.presetsFolder());
        Files.writeString(presetsFile(), content);
    }

    /** Opens the presets again from the Resource Packs screen under them */
    private static void reopenPresets(TestContext t) {
        press(t, "gui.back");
        openPresets(t);
    }

    private void oddFiles(TestContext t) throws Exception {
        Saved saved = start(t);
        Path outside = t.mc().gameDirectory.toPath().resolve(PREFIX + "outside.zip");
        try {
            String a = makePacks(t, "a").getFirst();
            PackSelectionScreen screen = openPackScreen(t);
            openPresets(t);
            // {what, file, rows shown, kept aside as .broken}
            Object[][] cases = {
                {"an empty file", "", 0, false},
                {"only spaces", "  \n ", 0, false},
                {"an empty object", "{}", 0, false},
                {"no list", "{\"presets\":null}", 0, false},
                {"an empty list", "{\"presets\":[]}", 0, false},
                {"null entries", "{\"presets\":[null,{\"name\":\"Ok\"},null]}", 1, false},
                {"a number as name", "{\"presets\":[{\"name\":5,\"packs\":[\"x\"]}]}", 1, false},
                {"unknown fields", "{\"presets\":[{\"name\":\"A\",\"extra\":[1,2]}],\"version\":9}", 1, false},
                {"a null fingerprint", "{\"presets\":[{\"name\":\"A\",\"files\":{\"x\":null}}]}", 1, false},
                {"a list as the file", "[]", 0, true},
                {"a number as the list", "{\"presets\":5}", 0, true},
                {"a text as the pack list", "{\"presets\":[{\"name\":\"A\",\"packs\":\"x\"}]}", 0, true},
                {"text after the end", "{\"presets\":[]} {}", 0, true},
                {"cut off", "{\"presets\":[{\"name\":\"A\"", 0, true},
            };
            int right = 0;
            List<String> wrong = new ArrayList<>();
            for (Object[] testCase : cases) {
                deletePresetFiles();
                writeRawPresets((String) testCase[1]);
                reopenPresets(t);
                int rows = rowCount(t, firstList(t));
                boolean broken = Files.exists(brokenFile());
                if (rows == (int) testCase[2] && broken == (boolean) testCase[3]) {
                    right++;
                } else {
                    wrong.add(testCase[0] + ": rows " + rows + ", broken " + broken);
                }
            }
            t.check("every odd file is read or kept aside as expected " + wrong, right).isEqualTo(cases.length);

            deletePresetFiles();
            writeRawPresets("{\"presets\":[{\"name\":5}]}");
            reopenPresets(t);
            rowButton(t, 0, EDIT);
            t.check("a number as name shows as text", textOf(t, KEY + "edit.preset_name")).isEqualTo("5");
            press(t, "gui.cancel");

            String name = "ทดสอบ ✓ \"q\" <b>&amp; 'x' \\ é 漢字";
            press(t, PRESETS + "new");
            type(t, KEY + "edit.preset_name", name);
            type(t, KEY + "edit.description", "line <i>&</i> \\n");
            press(t, KEY + "edit.create");
            JsonObject created = readPresets().getLast();
            t.check("every character of the name is saved", created.get("name").getAsString()).isEqualTo(name);
            t.check("the file keeps < > & as they are (readable by hand)",
                Files.readString(presetsFile()).contains("<b>&amp;")).isTrue();
            reopenPresets(t);
            rowButton(t, rowCount(t, firstList(t)) - 1, EDIT);
            t.check("read again: the same name", textOf(t, KEY + "edit.preset_name")).isEqualTo(name);
            t.check("read again: the same description", textOf(t, KEY + "edit.description"))
                .isEqualTo("line <i>&</i> \\n");
            press(t, "gui.cancel");

            // pack ids that point outside the resource pack folder: never read, shown as missing
            writeZipFile(outside, "outside");
            String up = "file/../" + outside.getFileName();
            String absolute = "file/" + outside.toAbsolutePath();
            JsonObject sneaky = preset("Outside", a, up, absolute);
            JsonObject files = new JsonObject();
            files.addProperty(up, "1:" + "0".repeat(40));
            files.addProperty(absolute, "1:" + "0".repeat(40));
            sneaky.add("files", files);
            writePresets(t, sneaky);
            reopenPresets(t);
            int[] counts = t.onClientThread(() -> PackManagerInternals.problemCounts(screen, 0));
            t.check("ids outside the folder: missing", counts[0]).isEqualTo(2);
            t.check("ids outside the folder: the file is never read (not changed)", counts[2]).isEqualTo(0);
            applyPreset(t, 0);
            t.check("applying turns on only the real pack", among(selected(t, screen), List.of(a, up, absolute)))
                .isEqualTo(List.of(a));

            // a name no file can have here (NUL; on Windows also ? : * < > |), e.g. a presets file from another system
            String invalid = "file/bad\u0000name.zip";
            JsonObject odd = preset("Invalid name", a, invalid);
            JsonObject oddFiles = new JsonObject();
            oddFiles.addProperty(invalid, "1:" + "0".repeat(40));
            odd.add("files", oddFiles);
            writePresets(t, odd);
            reopenPresets(t);
            int[] invalidCounts = t.onClientThread(() -> PackManagerInternals.problemCounts(screen, 0));
            t.check("a name no file can have: missing, not read", invalidCounts[0] == 1 && invalidCounts[2] == 0)
                .isTrue();
            applyPreset(t, 0);
            t.check("a name no file can have: the rest applies", among(selected(t, screen), List.of(a)))
                .isEqualTo(List.of(a));
            t.check("a name no file can have: the preset button works", presetLabel(t))
                .isEqualTo("Preset: Invalid name");
        } finally {
            restore(t, saved);
            Files.deleteIfExists(outside);
        }
    }

    private void oddPresets(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b");
            String a = ids.get(0);
            String b = ids.get(1);
            JsonObject iconGone = preset("Icon gone", a);
            iconGone.addProperty("icon", id("gone"));
            writePresets(t, preset("First", b, a), preset("Second", b, a), preset("All missing", id("gone1"),
                id("gone2")), preset("Nothing"), iconGone);
            PackSelectionScreen screen = openPackScreen(t);
            List<String> required = t.onClientThread(() -> model(screen).getSelected()
                .filter(entry -> entry.isRequired()).map(entry -> entry.getId()).toList());
            openPresets(t);
            applyPreset(t, 0);
            t.check("two presets with the same packs: the first is in use", presetLabel(t))
                .isEqualTo("Preset: First");
            openPresets(t);
            t.check("the second matches too", t.onClientThread(() -> PackManagerInternals.isActive(screen, 1)))
                .isTrue();
            rowButton(t, 0, DELETE);
            press(t, "gui.yes");
            press(t, "gui.back");
            t.check("the preset in use deleted: the next match is in use", presetLabel(t))
                .isEqualTo("Preset: Second");
            openPresets(t);
            rowButton(t, 0, DELETE);
            press(t, "gui.yes");
            press(t, "gui.back");
            t.check("no match left: Custom", presetLabel(t)).isEqualTo("Preset: Custom");

            openPresets(t);
            applyPreset(t, 0);
            t.check("every pack missing: only the packs that are always on stay", among(selected(t, screen), ids))
                .isEqualTo(List.of());
            t.check("every pack missing: the always-on packs keep their order", t.onClientThread(() ->
                model(screen).getSelected().filter(entry -> entry.isRequired()).map(entry -> entry.getId())
                    .toList())).isEqualTo(required);
            t.check("every pack missing: a message names them", t.onClientThread(() -> t.mc().getToasts()
                .getToast(SystemToast.class, SystemToast.SystemToastId.PACK_LOAD_FAILURE) != null)).isTrue();
            t.check("every pack missing: not in use (nothing of it is on)", presetLabel(t))
                .isEqualTo("Preset: Custom");
            select(t, screen, a);
            openPresets(t);
            applyPreset(t, 1);
            t.check("no packs: the packs turn off", among(selected(t, screen), ids)).isEqualTo(List.of());

            openPresets(t);
            t.ticks(5);
            t.saveScreenshot("odd_presets");
            t.check("a missing icon pack: the screen draws (icons of the packs instead)",
                exists(t, PRESETS + "new")).isTrue();

            press(t, PRESETS + "new");
            type(t, KEY + "edit.preset_name", "N".repeat(100));
            type(t, KEY + "edit.description", "D".repeat(400));
            t.check("the name is cut at 64 characters", textOf(t, KEY + "edit.preset_name").length()).isEqualTo(64);
            t.check("the description is cut at 256 characters", textOf(t, KEY + "edit.description").length())
                .isEqualTo(256);
            type(t, KEY + "edit.preset_name", "W".repeat(64));
            press(t, KEY + "edit.create");
            t.check("a long name is saved whole", names(readPresets()).getLast()).isEqualTo("W".repeat(64));
            applyPreset(t, rowCount(t, firstList(t)) - 1);
            t.ticks(5);
            t.saveScreenshot("long_name_button");
            t.check("a long name on the preset button", presetLabel(t)).isEqualTo("Preset: " + "W".repeat(64));

            JsonObject[] many = new JsonObject[60];
            for (int index = 0; index < many.length - 1; index++) {
                many[index] = preset("P" + index, a);
            }
            many[many.length - 1] = preset("Last", b);
            writePresets(t, many);
            long begin = System.nanoTime();
            openPresets(t);
            t.note("opening 60 presets, with the ticks of the test (ms)", (System.nanoTime() - begin) / 1_000_000.0);
            AbstractSelectionList<?> list = firstList(t);
            t.check("60 presets: the list scrolls", t.onClientThread(() -> list.getMaxScroll() > 0)).isTrue();
            t.onClientThread(() -> {
                list.setScrollAmount(list.getMaxScroll());
                return null;
            });
            t.ticks(2);
            applyPreset(t, many.length - 1);
            t.check("the last of 60, scrolled to: applied", presetLabel(t)).isEqualTo("Preset: Last");
        } finally {
            restore(t, saved);
        }
    }

    private void keyboard(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b");
            String a = ids.get(0);
            String b = ids.get(1);
            writePresets(t, preset("A", a), preset("B", b, a));
            PackSelectionScreen screen = openPackScreen(t);
            openPresets(t);
            AbstractSelectionList<?> list = firstList(t);
            clickPreset(t, 0, false);
            t.pressKey(GLFW.GLFW_KEY_DOWN);
            t.check("Down selects the next preset", t.onClientThread(() -> PackManagerInternals.selectedRow(list)))
                .isEqualTo(1);
            t.pressKey(GLFW.GLFW_KEY_ENTER);
            t.check("Enter applies; over custom packs it asks first", asks(t, PRESETS + "replace_custom_title"))
                .isTrue();
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape on the question: back on the presets, nothing changed", exists(t, PRESETS + "new")
                && among(selected(t, screen), ids).isEmpty()).isTrue();
            t.pressKey(GLFW.GLFW_KEY_ENTER);
            press(t, PRESETS + "apply");
            t.check("Enter, then Apply: the selected preset", among(selected(t, screen), ids)).isEqualTo(List.of(b, a));
            openPresets(t);
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape on the presets: back on the Resource Packs screen", t.mc().screen == screen).isTrue();

            TransferableSelectionList selectedList = selectedList(screen);
            click(t, nameX(selectedList), rowY(t, selectedList, rowOf(t, selectedList, b)));
            t.pressKey(GLFW.GLFW_KEY_ENTER);
            t.check("Enter on a pack of the preset in use asks first", asks(t, KEY + "leave_preset.title")).isTrue();
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            t.check("Escape on the question: nothing changed", t.mc().screen == screen
                && among(selected(t, screen), ids).equals(List.of(b, a))).isTrue();
            t.check("still the preset", presetLabel(t)).isEqualTo("Preset: B");
            click(t, nameX(selectedList), rowY(t, selectedList, rowOf(t, selectedList, b)));
            t.pressKey(GLFW.GLFW_KEY_SPACE);
            t.check("Space asks too", asks(t, KEY + "leave_preset.title")).isTrue();
            press(t, KEY + "leave_preset.change");
            t.check("Change Packs: the pack is off", among(selected(t, screen), ids)).isEqualTo(List.of(a));
            t.check("Change Packs: preset A matches now", presetLabel(t)).isEqualTo("Preset: A");
        } finally {
            restore(t, saved);
        }
    }

    /** Where a widget or list of a screen is, for the layout checks */
    private record Box(String name, int x, int y, int width, int height, boolean list) {

        boolean overlaps(Box other) {
            return x < other.x + other.width && other.x < x + width && y < other.y + other.height
                && other.y < y + height;
        }

        boolean inside(int screenWidth, int screenHeight) {
            return x >= 0 && y >= 0 && x + width <= screenWidth && y + height <= screenHeight;
        }
    }

    /** Returns what is wrong with the layout of the open screen: parts off screen, overlapping, or lists too low */
    private static List<String> layoutProblems(TestContext t, int minListHeight) {
        return t.onClientThread(() -> {
            Screen screen = t.mc().screen;
            List<Box> boxes = new ArrayList<>();
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractSelectionList<?> list) {
                    boxes.add(new Box("list " + boxes.size(), list.getX(), list.getY(), list.getWidth(),
                        list.getHeight(), true));
                } else if (child instanceof AbstractWidget widget && widget.visible) {
                    boxes.add(new Box("'" + widget.getMessage().getString() + "'", widget.getX(), widget.getY(),
                        widget.getWidth(), widget.getHeight(), false));
                }
            }
            List<String> problems = new ArrayList<>();
            for (int index = 0; index < boxes.size(); index++) {
                Box box = boxes.get(index);
                if (!box.inside(screen.width, screen.height)) {
                    problems.add(box.name() + " off screen (x " + box.x() + ", width " + box.width() + ")");
                }
                if (box.list() && box.height() < minListHeight) {
                    problems.add(box.name() + " only " + box.height() + " high");
                }
                for (int other = index + 1; other < boxes.size(); other++) {
                    if (box.overlaps(boxes.get(other))) {
                        problems.add(box.name() + " overlaps " + boxes.get(other).name());
                    }
                }
            }
            return problems;
        });
    }

    /** Gives the open screen another size, as a changed window or GUI scale does */
    private static void resizeScreen(TestContext t, int width, int height) {
        t.onClientThread(() -> {
            t.mc().screen.resize(t.mc(), width, height);
            return null;
        });
        t.ticks(2);
    }

    /** Gives the open screen the window's size again */
    private static void resizeToWindow(TestContext t) {
        resizeScreen(t, t.mc().getWindow().getGuiScaledWidth(), t.mc().getWindow().getGuiScaledHeight());
    }

    /**
     * Checks the open screen at the sizes of real windows and GUI scales, and notes how it does below 430 wide, where
     * the game's own Resource Packs screen (two lists of 200 with 30 between) does not fit either
     */
    private static void checkSizes(TestContext t, String screenName, int minListHeight) {
        // 1366x768 at 3, 1920x1080 at 4, 3 and 2
        int[][] sizes = {{455, 256}, {480, 270}, {640, 360}, {960, 540}};
        List<String> problems = new ArrayList<>();
        for (int[] size : sizes) {
            resizeScreen(t, size[0], size[1]);
            for (String problem : layoutProblems(t, minListHeight)) {
                problems.add(size[0] + "x" + size[1] + ": " + problem);
            }
        }
        t.check(screenName + ": every part on screen and apart " + problems, problems.size()).isEqualTo(0);
        // the smallest scaled window; 854x480 and 1280x720 at Auto
        for (int[] size : new int[][] {{320, 240}, {426, 240}, {427, 240}}) {
            resizeScreen(t, size[0], size[1]);
            t.note(screenName + " at " + size[0] + "x" + size[1], layoutProblems(t, minListHeight));
        }
        resizeToWindow(t);
    }

    private void screenSizes(TestContext t) throws Exception {
        Saved saved = start(t);
        Path zip = writeZipPack(t, "sized", "sized");
        String zipId = "file/" + zip.getFileName();
        try {
            t.onClientThread(() -> {
                t.mc().options.guiScale().set(2);
                t.mc().resizeDisplay();
                return null;
            });
            t.ticks(2);
            List<String> ids = makePacks(t, "a", "b");
            JsonObject main = preset("Sized with a rather long name to see how it fits", zipId, ids.get(0),
                id("gone"));
            JsonObject files = new JsonObject();
            files.addProperty(zipId, "1:" + "0".repeat(40));
            main.add("files", files);
            writePresets(t, main, preset("Other", ids.get(1)));
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, ids.get(1));
            t.saveScreenshot("small_resource_packs");
            checkSizes(t, "Resource Packs", 36);

            openPresets(t);
            t.saveScreenshot("small_presets");
            checkSizes(t, "Presets", 36);
            rowButton(t, 0, EDIT);
            t.require(exists(t, KEY + "edit.review_changed.button"), "No Review Changed button");
            t.saveScreenshot("small_form");
            checkSizes(t, "Preset form", 36);
            press(t, KEY + "edit.review_changed.button");
            t.saveScreenshot("small_review_changed");
            checkSizes(t, "Review Changed", 24);
            press(t, "gui.cancel");
            press(t, KEY + "edit.edit_packs.button");
            t.require(t.mc().screen instanceof PackSelectionScreen, "Edit Packs did not open");
            OPENED.add((PackSelectionScreen) t.mc().screen);
            t.saveScreenshot("small_edit_packs");
            checkSizes(t, "Edit Packs", 36);
            press(t, "gui.cancel");
            press(t, "gui.cancel");
            press(t, "gui.back");

            t.require(t.openSettings(id()), "The settings screen did not open");
            t.saveScreenshot("small_settings");
            checkSizes(t, "Settings", 36);
        } finally {
            restore(t, saved);
            Files.deleteIfExists(zip);
        }
    }

    private void resizeKeeps(TestContext t) throws Exception {
        Saved saved = start(t);
        Path zip = writeZipPack(t, "resized", "resized");
        String zipId = "file/" + zip.getFileName();
        try {
            List<String> ids = makePacks(t, "a", "b", "c");
            JsonObject main = preset("Main", zipId, ids.get(0));
            JsonObject files = new JsonObject();
            files.addProperty(zipId, "1:" + "0".repeat(40));
            main.add("files", files);
            writePresets(t, main, preset("Other", ids.get(1)));
            PackSelectionScreen screen = openPackScreen(t);
            openPresets(t);
            AbstractSelectionList<?> list = firstList(t);
            clickPreset(t, 1, false);
            resizeScreen(t, 600, 400);
            t.check("presets: the selected preset stays selected", t.onClientThread(() ->
                PackManagerInternals.selectedRow(list))).isEqualTo(1);
            resizeToWindow(t);

            rowButton(t, 0, EDIT);
            type(t, KEY + "edit.preset_name", "Changed name");
            resizeScreen(t, 600, 400);
            t.check("form: the typed name stays", textOf(t, KEY + "edit.preset_name")).isEqualTo("Changed name");
            t.check("form: Save stays on", usable(t, KEY + "edit.save")).isTrue();
            resizeToWindow(t);
            press(t, KEY + "edit.edit_packs.button");
            PackSelectionScreen editor = (PackSelectionScreen) t.mc().screen;
            OPENED.add(editor);
            select(t, editor, ids.get(2));
            resizeScreen(t, 600, 400);
            t.check("Edit Packs: the choice stays", selected(t, editor).contains(ids.get(2))).isTrue();
            resizeToWindow(t);
            press(t, "gui.done");
            t.check("back in the form: the name and the packs", textOf(t, KEY + "edit.preset_name")
                .equals("Changed name") && usable(t, KEY + "edit.save")).isTrue();
            press(t, KEY + "edit.review_changed.button");
            tickChanged(t, 0);
            resizeScreen(t, 600, 400);
            t.check("Review Changed: the tick stays (Accept on)", usable(t, KEY + "changed.accept")).isTrue();
            resizeToWindow(t);
            press(t, "gui.cancel");
            press(t, "gui.cancel");
            press(t, KEY + "edit.discard");
            t.check("discarded: nothing saved", names(readPresets())).isEqualTo(List.of("Main", "Other"));

            AbstractSelectionList<?> presets = firstList(t);
            double x = nameX(presets);
            pressAndMove(t, x, rowY(t, presets, 0), x, slotY(t, presets, 2));
            resizeScreen(t, 600, 400);
            letGo(t, x, slotY(t, presets, 2));
            resizeToWindow(t);
            t.check("presets resized while dragging: none lost or doubled", names(readPresets()).stream().sorted()
                .toList()).isEqualTo(List.of("Main", "Other"));

            press(t, "gui.back");
            PackManagerFeature.getConfig().askBeforeLeavingPreset = false;
            select(t, screen, ids.get(1), ids.get(2));
            List<String> before = selected(t, screen);
            TransferableSelectionList selectedList = selectedList(screen);
            double packX = nameX(selectedList);
            pressAndMove(t, packX, rowY(t, selectedList, 0), packX, slotY(t, selectedList, 3));
            resizeScreen(t, 600, 400);
            letGo(t, packX, slotY(t, selectedList, 3));
            resizeToWindow(t);
            List<String> after = selected(t, screen);
            t.check("packs resized while dragging: none lost or doubled", after.stream().sorted().toList())
                .isEqualTo(before.stream().sorted().toList());
            t.check("packs resized while dragging: no pack twice", new HashSet<>(after).size()).isEqualTo(after.size());
        } finally {
            restore(t, saved);
            Files.deleteIfExists(zip);
        }
    }

    private void serverPack(TestContext t) throws Exception {
        Saved saved = start(t);
        Path serverFolder = t.mc().gameDirectory.toPath().resolve(PREFIX + "server_pack");
        String server = "server/devtest";
        try {
            Files.createDirectories(serverFolder);
            Files.writeString(serverFolder.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":34,\"description\":\"server\"}}");
            List<String> ids = makePacks(t, "a", "b");
            String a = ids.get(0);
            String b = ids.get(1);
            // the server's pack as the game adds it (DownloadedPackSource): always on, on top, fixed
            PackRepository repository = new PackRepository(new FolderRepositorySource(
                t.mc().getResourcePackDirectory(), PackType.CLIENT_RESOURCES, PackSource.DEFAULT,
                t.mc().directoryValidator()), packs -> {
                    Pack pack = Pack.readMetaAndCreate(new PackLocationInfo(server, Component.literal("Server pack"),
                        PackSource.SERVER, Optional.empty()), new PathPackResources.PathResourcesSupplier(serverFolder),
                        PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true));
                    if (pack != null) {
                        packs.accept(pack);
                    }
                });
            t.onClientThread(() -> {
                repository.reload();
                repository.setSelected(List.of());
                return null;
            });
            t.openScreen(() -> new PackSelectionScreen(repository, chosen -> t.mc().setScreen(null),
                t.mc().getResourcePackDirectory(), Component.translatable("resourcePack.title")));
            t.require(t.mc().screen instanceof PackSelectionScreen, "The screen did not open");
            PackSelectionScreen screen = (PackSelectionScreen) t.mc().screen;
            OPENED.add(screen);
            t.require(selected(t, screen).contains(server), "The server pack is not on");
            select(t, screen, a, b);
            t.check("the server pack is on top", selected(t, screen).getFirst()).isEqualTo(server);
            t.check("a preset button", exists(t, KEY + "preset_button")).isTrue();
            TransferableSelectionList list = selectedList(screen);

            dragToSlot(t, screen, list, a, 0);
            t.check("a pack dropped above the server pack lands right below it", selected(t, screen).subList(0, 2))
                .isEqualTo(List.of(server, a));
            List<String> order = selected(t, screen);
            dragToSlot(t, screen, list, server, rowOf(t, list, b) + 1);
            t.check("the server pack cannot be dragged", selected(t, screen)).isEqualTo(order);
            drag(t, nameX(list), rowY(t, list, 0), availableList(screen).getX() + 50.0,
                availableList(screen).getY() + 50.0);
            t.check("the server pack cannot be dragged off", selected(t, screen)).isEqualTo(order);
            t.saveScreenshot("server_pack");

            openPresets(t);
            press(t, PRESETS + "new");
            press(t, KEY + "edit.create");
            List<String> stored = packsOf(readPresets().getFirst());
            t.check("a new preset does not store the server pack", stored.contains(server)).isFalse();
            t.check("it stores the other packs in order", among(stored, ids)).isEqualTo(List.of(a, b));
            press(t, "gui.back");
            t.check("in use while the server pack is on", presetLabel(t)).isEqualTo("Preset: New Preset");

            writePresets(t, preset("Reversed", b, a), preset("Names it", a, server));
            openPresets(t);
            applyPreset(t, 0);
            t.check("applying keeps the server pack on top", selected(t, screen).subList(0, 3))
                .isEqualTo(List.of(server, b, a));
            openPresets(t);
            applyPreset(t, 1);
            t.check("a preset that names the server pack: it stays on top, the rest applied",
                selected(t, screen).subList(0, 2)).isEqualTo(List.of(server, a));
        } finally {
            restore(t, saved);
            try (Stream<Path> found = Files.walk(serverFolder)) {
                for (Path file : found.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private void fromOptions(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            t.require(t.mc().level != null, "Not in a world");
            List<String> ids = makePacks(t, "a", "b");
            String a = ids.get(0);
            String b = ids.get(1);
            writePresets(t, preset("Ab", a, b), preset("Ba", b, a));
            t.openScreen(() -> new OptionsScreen(new PauseScreen(true), t.mc().options));
            t.require(press(t, "options.resourcepack"), "No Resource Packs button");
            t.require(t.mc().screen instanceof PackSelectionScreen, "Resource Packs did not open");
            PackSelectionScreen screen = (PackSelectionScreen) t.mc().screen;
            OPENED.add(screen);
            t.check("the preset button is there", exists(t, KEY + "preset_button")).isTrue();
            openPresets(t);
            applyPreset(t, 0);
            press(t, "gui.done");
            waitForReload(t);
            t.check("Done: back on Options", t.mc().screen instanceof OptionsScreen).isTrue();
            t.check("Done: saved lowest first", among(t.onClientThread(() ->
                List.copyOf(t.mc().options.resourcePacks)), ids)).isEqualTo(List.of(b, a));
            t.check("Done: loaded in that order", among(loaded(t), ids)).isEqualTo(List.of(b, a));

            t.require(press(t, "options.resourcepack"), "No Resource Packs button");
            PackSelectionScreen again = (PackSelectionScreen) t.mc().screen;
            OPENED.add(again);
            t.check("opened again: the preset in use", presetLabel(t)).isEqualTo("Preset: Ab");
            openPresets(t);
            applyPreset(t, 1);
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            waitForReload(t);
            t.check("Escape saves like the game does", among(t.onClientThread(() ->
                List.copyOf(t.mc().options.resourcePacks)), ids)).isEqualTo(List.of(a, b));
            t.check("Escape: back on Options", t.mc().screen instanceof OptionsScreen).isTrue();
        } finally {
            restore(t, saved);
        }
    }

    private void restart(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b");
            writePresets(t, preset("Kept", ids.get(1), ids.get(0)), preset("Other", ids.get(0)));
            String written = Files.readString(presetsFile());
            FileTime time = Files.getLastModifiedTime(presetsFile());
            openPackScreen(t);
            openPresets(t);
            applyPreset(t, 0);
            press(t, "gui.done");
            waitForReload(t);
            t.onClientThread(() -> {
                PackManagerInternals.loadPresets(); // as the game does when it starts
                return null;
            });
            PackSelectionScreen screen = openPackScreen(t);
            t.check("after a restart: the preset in use", presetLabel(t)).isEqualTo("Preset: Kept");
            t.check("after a restart: the packs in order", among(selected(t, screen), ids))
                .isEqualTo(List.of(ids.get(1), ids.get(0)));
            openPresets(t);
            t.check("after a restart: marked Active", t.onClientThread(() ->
                PackManagerInternals.isActive(screen, 0))).isTrue();
            press(t, "gui.back");
            press(t, "gui.done");
            waitForReload(t);
            t.check("reading, applying and Done never write the presets file", Files.readString(presetsFile()))
                .isEqualTo(written);
            t.check("the presets file's time is unchanged", Files.getLastModifiedTime(presetsFile()))
                .isEqualTo(time);
        } finally {
            restore(t, saved);
        }
    }

    private void oddPackFiles(TestContext t) throws Exception {
        Saved saved = start(t);
        Path zip = writeZipPack(t, "same", "aaaa");
        String zipId = "file/" + zip.getFileName();
        Path folderZip = t.mc().getResourcePackDirectory().resolve(PREFIX + "folder.zip");
        String folderZipId = "file/" + folderZip.getFileName();
        try {
            Files.createDirectories(folderZip);
            Files.writeString(folderZip.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":34,\"description\":\"folder\"}}");
            PackSelectionScreen screen = openPackScreen(t);
            t.waitUntil("the screen lists the folder named .zip", () -> t.onClientThread(() ->
                PackManagerInternals.isListed(screen, folderZipId)), 100);
            select(t, screen, zipId, folderZipId);
            openPresets(t);
            press(t, PRESETS + "new");
            press(t, KEY + "edit.create");
            JsonObject files = readPresets().getFirst().getAsJsonObject("files");
            t.check("a folder named .zip is not checked", files.has(folderZipId)).isFalse();
            t.check("the zip is checked", files.has(zipId)).isTrue();

            FileTime first = Files.getLastModifiedTime(zip);
            writeZipPack(t, "same", "bbbb");
            Files.setLastModifiedTime(zip, FileTime.fromMillis(first.toMillis() + 10_000));
            t.check("the same size, other content: changed", changedCount(t, screen, 0)).isEqualTo(1);
            writeZipPack(t, "same", "aaaa");
            Files.setLastModifiedTime(zip, FileTime.fromMillis(first.toMillis() + 20_000));
            t.check("the same content written again: not changed", changedCount(t, screen, 0)).isEqualTo(0);

            press(t, "gui.back");
            t.check("in use", presetLabel(t)).isEqualTo("Preset: New Preset");
            writeZipPack(t, "same", "another file");
            t.waitUntil("the preset button shows the file is not the preset's any more", () ->
                presetLabel(t).equals("Preset: Custom"), 200);
            t.check("the preset button updated by itself", presetLabel(t)).isEqualTo("Preset: Custom");
        } finally {
            restore(t, saved);
            Files.deleteIfExists(zip);
            try (Stream<Path> found = Files.walk(folderZip)) {
                for (Path file : found.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private void dataPackNeverAsks(TestContext t) throws Exception {
        Saved saved = start(t);
        Path folder = t.mc().gameDirectory.toPath().resolve(PREFIX + "datapacks_ask");
        try {
            String a = makePacks(t, "a").getFirst();
            writePresets(t, preset("A", a));
            openPackScreen(t);
            openPresets(t);
            applyPreset(t, 0);
            press(t, "gui.done");
            waitForReload(t);
            List<String> game = t.onClientThread(() ->
                List.copyOf(t.mc().getResourcePackRepository().getSelectedIds()));

            for (String name : List.of("first", "second")) {
                Path pack = folder.resolve(name);
                Files.createDirectories(pack);
                Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + DATA_PACK_FORMAT
                    + ",\"description\":\"data\"}}");
            }
            PackRepository repository = new PackRepository(new FolderRepositorySource(folder, PackType.SERVER_DATA,
                PackSource.DEFAULT, t.mc().directoryValidator()));
            t.openScreen(() -> new PackSelectionScreen(repository, chosen -> t.mc().setScreen(null), folder,
                Component.literal("Data Packs")));
            PackSelectionScreen screen = (PackSelectionScreen) t.mc().screen;
            OPENED.add(screen);
            TransferableSelectionList available = availableList(screen);
            click(t, iconX(available), rowY(t, available, rowOf(t, available, "file/first")));
            t.check("turning a data pack on does not ask", t.mc().screen == screen).isTrue();
            dragToSlot(t, screen, availableList(screen), "file/second", 0);
            t.check("dropping a data pack does not ask", t.mc().screen == screen
                && selected(t, screen).subList(0, 2).equals(List.of("file/second", "file/first"))).isTrue();
            t.check("the resource packs are as before", t.onClientThread(() ->
                List.copyOf(t.mc().getResourcePackRepository().getSelectedIds()))).isEqualTo(game);
        } finally {
            restore(t, saved);
            if (Files.exists(folder)) {
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
        }
    }

    private void dragEdgeCases(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            List<String> ids = makePacks(t, "a", "b", "doomed", "doomed_on");
            String a = ids.get(0);
            String doomed = ids.get(2);
            String doomedOn = ids.get(3);
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, a, ids.get(1));
            TransferableSelectionList selectedList = selectedList(screen);
            TransferableSelectionList availableList = availableList(screen);
            List<String> order = selected(t, screen);
            int screenHeight = t.onClientThread(() -> t.mc().screen.height);
            drag(t, nameX(selectedList), rowY(t, selectedList, rowOf(t, selectedList, a)), nameX(selectedList),
                screenHeight - 10.0);
            t.check("dropped on the bottom row: nothing changed", selected(t, screen)).isEqualTo(order);
            drag(t, nameX(availableList), rowY(t, availableList, 0), nameX(selectedList), 5);
            t.check("dropped on the title: nothing changed", selected(t, screen)).isEqualTo(order);
            drag(t, nameX(availableList), rowY(t, availableList, 0), nameX(availableList),
                availableList.getY() + availableList.getHeight() / 2.0);
            t.check("Available dropped on Available: nothing changed", selected(t, screen)).isEqualTo(order);

            // a pack whose file is deleted while it is dragged
            String file = PREFIX + "doomed";
            pressAndMove(t, nameX(availableList), rowY(t, availableList, rowOf(t, availableList, doomed)),
                nameX(selectedList), slotY(t, selectedList, 0));
            deleteFolder(t.mc().getResourcePackDirectory().resolve(file));
            t.waitUntil("the screen drops the deleted pack", () -> !t.onClientThread(() ->
                PackManagerInternals.isListed(screen, doomed)), 200);
            letGo(t, nameX(selectedList), slotY(t, selectedList, 0));
            t.check("dropped after its file was deleted: not brought back", t.onClientThread(() ->
                PackManagerInternals.isListed(screen, doomed))).isFalse();
            select(t, screen, doomedOn);
            pressAndMove(t, nameX(selectedList), rowY(t, selectedList, rowOf(t, selectedList, doomedOn)),
                availableList.getX() + availableList.getWidth() / 2.0, availableList.getY() + 50.0);
            deleteFolder(t.mc().getResourcePackDirectory().resolve(PREFIX + "doomed_on"));
            t.waitUntil("the screen drops the deleted pack", () -> !t.onClientThread(() ->
                PackManagerInternals.isListed(screen, doomedOn)), 200);
            letGo(t, availableList.getX() + availableList.getWidth() / 2.0, availableList.getY() + 50.0);
            t.check("dropped on Available after its file was deleted: not brought back", t.onClientThread(() ->
                PackManagerInternals.isListed(screen, doomedOn))).isFalse();
            t.check("every row once", t.onClientThread(() -> {
                List<String> rows = new ArrayList<>();
                selectedList.children().forEach(entry -> rows.add(entry.getPackId()));
                availableList.children().forEach(entry -> rows.add(entry.getPackId()));
                return rows.size() == new HashSet<>(rows).size();
            })).isTrue();

            List<String> many = makePacks(t, "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11");
            PackSelectionScreen tall = openPackScreen(t);
            select(t, tall, many.toArray(String[]::new));
            TransferableSelectionList tallList = selectedList(tall);
            t.onClientThread(() -> {
                tallList.setScrollAmount(tallList.getMaxScroll());
                return null;
            });
            double scrolled = t.onClientThread(tallList::getScrollAmount);
            List<String> tallOrder = selected(t, tall);
            double x = nameX(tallList);
            int last = rowCount(t, tallList) - 1;
            pressAndMove(t, x, rowY(t, tallList, last), x, tallList.getY() + 4.0);
            t.ticks(10);
            t.check("held near the top edge, the list scrolls up", scrolled - t.onClientThread(
                tallList::getScrollAmount)).atLeast(40);
            t.pressKey(GLFW.GLFW_KEY_ESCAPE);
            letGo(t, x, tallList.getY() + 4.0);
            t.check("Escape: nothing moved", selected(t, tall)).isEqualTo(tallOrder);
        } finally {
            restore(t, saved);
        }
    }

    private void wording(TestContext t) throws Exception {
        Saved saved = start(t);
        try {
            t.check("no packs", PackManagerInternals.packCount(0)).isEqualTo("0 packs");
            t.check("one pack", PackManagerInternals.packCount(1)).isEqualTo("1 pack");
            t.check("two packs", PackManagerInternals.packCount(2)).isEqualTo("2 packs");
            List<String> ids = makePacks(t, "a", "b");
            writePresets(t, preset("One", ids.get(0)));
            // turning b on after a would leave the preset "One"; that question is tested on its own
            PackManagerFeature.getConfig().askBeforeLeavingPreset = false;
            PackSelectionScreen screen = openPackScreen(t);
            select(t, screen, ids.get(0), ids.get(1));
            int current = selected(t, screen).size();
            openPresets(t);
            t.saveScreenshot("one_pack_row");
            rowButton(t, 0, EDIT);
            press(t, KEY + "edit.use_current.button");
            String question = t.onClientThread(() -> t.mc().screen.getNarrationMessage().getString());
            t.check("Use Current names the preset's count and the current count", question.contains(
                "Its packs (1) are replaced by the packs you use now (" + current + ")")).isTrue();
            t.saveScreenshot("use_current_counts");
        } finally {
            restore(t, saved);
        }
    }

    private static void deleteFolder(Path folder) throws IOException {
        try (Stream<Path> found = Files.walk(folder)) {
            for (Path file : found.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    /** Writes a zip pack for 1.21.1 at any place */
    private static void writeZipFile(Path file, String description) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            ZipEntry entry = new ZipEntry("pack.mcmeta");
            entry.setTime(ZIP_TIME);
            zip.putNextEntry(entry);
            zip.write(("{\"pack\":{\"pack_format\":34,\"description\":\"" + description + "\"}}")
                .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
