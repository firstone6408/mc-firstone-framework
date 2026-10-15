package io.github.firstone.framework.features.packmanager.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.client.screen.HeadedList;
import io.github.firstone.framework.client.screen.Questions;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.preset.PackFiles;
import io.github.firstone.framework.features.packmanager.preset.PresetStatus;
import io.github.firstone.framework.features.packmanager.preset.PresetStore;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The preset form: name, description, icon and packs of a new or a saved preset
 *
 * <p>Built like every settings screen of the mod, but it works like a form: every change goes into a draft, and only
 * Create (new preset) or Save (saved preset) writes it to the presets file. Save is usable once something changed,
 * Create and Save only with a name. Cancel and Escape ask first when something changed. Replacing the packs (Use
 * Current) and removing the missing ones ask first too.</p>
 *
 * <p>Two columns, as wide as the Resource Packs screen: the form on the left, and on the right the preset's packs
 * as the Resource Packs screen shows them, top first (a pack higher up wins), read only. A pack's state is a colored
 * note after its name: (Missing) when its file is gone, (Changed) when its file is not the one the preset was saved
 * with ({@link PackFiles}; Review Changed chooses which new files to accept), (Other version) when it was made for
 * another version. The packs are chosen in the game's Resource Packs screen ({@link PresetPacksScreen}), or taken
 * from the Resource Packs screen the presets were opened from.</p>
 */
public class PresetEditScreen extends ConfigScreen {

    /** Key prefix of this screen */
    private static final String PREFIX = "firstone-framework.pack_manager.edit.";

    /** Most characters of a name and of a description */
    private static final int MAX_NAME = 64;
    private static final int MAX_DESCRIPTION = 256;

    /** Width of a column (a list of the Resource Packs screen) and the gap between the columns */
    private static final int COLUMN_WIDTH = 200;
    private static final int COLUMN_GAP = 30;

    /** Space between a card's edge and its content (rows, headings and their lines), on every side */
    private static final int CARD_PADDING = 6;

    /**
     * Width of the rows of both columns: with {@link ConfigList#getRowLeft} this leaves {@link #CARD_PADDING} between
     * the card's edges and the headings and their lines on both sides
     */
    private static final int ROW_WIDTH = COLUMN_WIDTH - 2 * CARD_PADDING + 2;

    /** Width of the text boxes and the icon button, so the longest label (Description) fits beside them */
    private static final int CONTROL_WIDTH = 116;

    /** The saved preset being edited, or null for a new preset */
    @Nullable
    private final PackPreset saved;

    /** The form's values; written to the file only by Create or Save */
    private final PackPreset draft;

    /** The values when the form opened, to tell whether something changed */
    private final PackPreset initial;

    /** The Resource Packs screen the presets were opened from (its packs, icons and names) */
    private final PackSelectionScreen packScreen;

    /** Gets the created or saved preset (e.g. to select it in the list) */
    private final Consumer<PackPreset> onSaved;

    /** The packs of the draft (right column), rebuilt with the screen */
    @Nullable
    private PackList packList;

    /** The draft's packs whose file is not the one the preset was saved with */
    private List<String> changedFiles = List.of();

    /** The Create or Save button, rebuilt with the screen */
    @Nullable
    private Button saveButton;

    /** True once the rows were built, so they are built again when the screen is shown again */
    private boolean built;

    private PresetEditScreen(Screen parent, @Nullable PackPreset saved, PackPreset values,
                             PackSelectionScreen packScreen, Consumer<PackPreset> onSaved) {
        super(parent, Component.translatable("firstone-framework.pack_manager.name"), PREFIX, null, null);
        this.saved = saved;
        this.draft = values.copy();
        this.initial = values.copy();
        this.packScreen = packScreen;
        this.onSaved = onSaved;
    }

    /**
     * Makes the form for a new preset; nothing is saved until Create
     *
     * @param parent     screen to return to (the presets)
     * @param values     the values the form starts with (e.g. the packs used now, or a copy of a preset)
     * @param packScreen the Resource Packs screen the presets were opened from
     * @param onSaved    gets the new preset after Create
     * @return the form
     */
    public static PresetEditScreen create(Screen parent, PackPreset values, PackSelectionScreen packScreen,
                                          Consumer<PackPreset> onSaved) {
        return new PresetEditScreen(parent, null, values, packScreen, onSaved);
    }

    /**
     * Makes the form for a saved preset; nothing is saved until Save
     *
     * @param parent     screen to return to (the presets)
     * @param preset     the saved preset
     * @param packScreen the Resource Packs screen the presets were opened from
     * @param onSaved    gets the preset after Save
     * @return the form
     */
    public static PresetEditScreen edit(Screen parent, PackPreset preset, PackSelectionScreen packScreen,
                                        Consumer<PackPreset> onSaved) {
        return new PresetEditScreen(parent, preset, preset, packScreen, onSaved);
    }

    @Override
    public void added() {
        super.added();
        // coming back from choosing the packs or from a question: show the draft again
        if (this.built) {
            rebuildWidgets();
        }
    }

    /** Narrow controls, so the labels fit beside them in the column */
    @Override
    protected int controlWidth() {
        return CONTROL_WIDTH;
    }

    @Override
    protected void addOptions() {
        this.built = true;
        Map<String, PackSelectionModel.Entry> entries = PackLists.entries(PackLists.model(this.packScreen));
        this.changedFiles = PackFiles.changed(this.draft).stream().filter(entries::containsKey).toList();

        addSection(this.saved == null ? "new_title" : "edit_title");
        addText("preset_name", this.draft.name, MAX_NAME, value -> changed(() -> this.draft.name = value));
        addText("description", this.draft.description, MAX_DESCRIPTION,
            value -> changed(() -> this.draft.description = value));
        List<String> icons = new ArrayList<>();
        icons.add("");
        this.draft.packs.stream().filter(entries::containsKey).forEach(icons::add);
        addCycle("icon", icons, icons.contains(this.draft.icon) ? this.draft.icon : "",
            id -> id.isEmpty() ? text("icon.auto") : entries.get(id).getTitle(),
            value -> changed(() -> this.draft.icon = value));

        addSection("category.packs");
        addActionButton("edit_packs", () -> PresetPacksScreen.open(this, this.draft));
        addActionButton("use_current", this::useCurrent).enabledWhen(() -> !currentPacks().equals(this.draft.packs));
        List<String> missing = this.draft.packs.stream().filter(id -> !entries.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            addActionButton("remove_missing", () -> removeMissing(missing));
        }
        if (!this.changedFiles.isEmpty()) {
            addActionButton("review_changed", () -> this.minecraft.setScreen(
                new ChangedFilesScreen(this, this.draft, entries, this.changedFiles)));
        }
    }

    /** Writes a value into the draft and updates Save */
    private void changed(Runnable write) {
        write.run();
        updateSaveButton();
    }

    @Override
    protected void init() {
        super.init();
        this.packList = addRenderableWidget(new PackList(this.minecraft, this.draft,
            PackLists.entries(PackLists.model(this.packScreen)), this.changedFiles, text("category.list")));
        repositionElements();
    }

    /** The form on the left, the packs on the right, each as wide as a list of the Resource Packs screen */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        ConfigList options = list();
        int top = options.getY();
        int height = options.getHeight();
        options.setFixedRowWidth(ROW_WIDTH);
        options.updateSizeAndPosition(COLUMN_WIDTH, height, top);
        options.setX(this.width / 2 - COLUMN_GAP / 2 - COLUMN_WIDTH);
        if (this.packList != null) {
            this.packList.updateSizeAndPosition(COLUMN_WIDTH, height, top);
            this.packList.setX(this.width / 2 + COLUMN_GAP / 2);
        }
    }

    /** Cancel, and Create or Save */
    @Override
    protected void addFooterButtons(LinearLayout footer) {
        footer.addChild(footerButton(CommonComponents.GUI_CANCEL, FOOTER_BUTTON_WIDTH, this::onClose));
        this.saveButton = footer.addChild(footerButton(text(this.saved == null ? "create" : "save"),
            FOOTER_BUTTON_WIDTH, this::save));
        updateSaveButton();
    }

    /**
     * Create and Save need a name; Save also needs a change (a changed pack file counts only once it was accepted in
     * Review Changed)
     */
    private void updateSaveButton() {
        Button button = this.saveButton;
        if (button == null) {
            return;
        }
        boolean named = !this.draft.name.isBlank();
        button.active = named && (this.saved == null || !this.draft.sameValues(this.initial));
        button.setTooltip(named ? null : Tooltip.create(text("name_needed")));
    }

    /** Writes the draft to the presets file and goes back */
    private void save() {
        if (this.saved == null) {
            PresetStore.add(this.draft);
            this.onSaved.accept(this.draft);
        } else {
            PresetStore.update(this.saved, this.draft);
            this.onSaved.accept(this.saved);
        }
        this.minecraft.setScreen(this.parent);
    }

    /** Cancel and Escape: back to the presets, asking first if the draft changed */
    @Override
    public void onClose() {
        if (this.draft.sameValues(this.initial)) {
            super.onClose();
            return;
        }
        Questions.ask(this, text("discard_title"), text("discard_message"), text("discard"), text("keep_editing"),
            super::onClose);
    }

    /** Returns the packs of the Resource Packs screen's Selected list, top first */
    private List<String> currentPacks() {
        return PackLists.selectedIds(PackLists.model(this.packScreen));
    }

    /** Asks, then replaces the draft's packs with the packs used now */
    private void useCurrent() {
        List<String> current = currentPacks();
        Questions.ask(this, text("use_current.title"), Component.translatable(PREFIX + "use_current.message",
            this.draft.packs.size(), current.size()), text("use_current.confirm"), () -> {
                this.draft.packs = new ArrayList<>(current);
                rebuildWidgets();
            });
    }

    /** Asks, then removes the missing packs from the draft */
    private void removeMissing(List<String> missing) {
        String names = missing.stream().map(PresetStatus::fileName).collect(Collectors.joining(", "));
        Questions.ask(this, text("remove_missing.title"),
            Component.translatable(PREFIX + "remove_missing.message", names), text("remove_missing.confirm"), () -> {
                this.draft.packs.removeAll(missing);
                rebuildWidgets();
            });
    }

    /**
     * The packs of a preset, read only, drawn as the Resource Packs screen draws its lists (right column of
     * this form)
     *
     * <p>Under a heading like the option list's headings ({@link HeadedList}), top first, with each pack's icon, name
     * and description. A pack's state is a note after its name, in color only on the note: (Missing) yellow, (Changed)
     * gold, (Other version) red. A missing pack shows the game's unknown pack icon and its file name. The packs are
     * changed with Edit Packs, not here.</p>
     */
    private static final class PackList extends HeadedList<PackList.PackRow> {

        /** Row height and icon size, as in the pack lists */
        private static final int ROW_HEIGHT = 36;
        private static final int ICON_SIZE = 32;

        /** Widest name and description: the row less the icon and a small margin */
        private static final int TEXT_WIDTH = ROW_WIDTH - ICON_SIZE - 4;

        /**
         * Creates the list
         *
         * @param minecraft the game
         * @param preset    the preset (the form's draft) whose packs are shown
         * @param entries   the packs the Resource Packs screen knows, by id
         * @param changed   the preset's packs whose file changed since it was saved
         * @param title     header of the list
         */
        private PackList(Minecraft minecraft, PackPreset preset, Map<String, PackSelectionModel.Entry> entries,
                       List<String> changed, Component title) {
            super(minecraft, ICON_SIZE, ICON_SIZE, 0, ROW_HEIGHT, ROW_WIDTH, title, null,
                Component.translatable(PREFIX + "list.empty"));
            for (String id : preset.packs) {
                addEntry(new PackRow(minecraft.font, id, entries.get(id), changed.contains(id)));
            }
        }

        /** Rows placed as in the option list beside it ({@code ConfigList.getRowLeft}), so both cards line up */
        @Override
        public int getRowLeft() {
            return getX() + (this.width - ROW_WIDTH) / 2 + 1;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - 6;
        }

        /** One pack of the preset */
        static final class PackRow extends ObjectSelectionList.Entry<PackRow> {

            private final Component name;
            private final ResourceLocation icon;
            private final FormattedCharSequence title;
            private final MultiLineLabel description;

            private PackRow(Font font, String id, @Nullable PackSelectionModel.Entry entry, boolean changed) {
                MutableComponent notes = Component.empty();
                if (entry == null) {
                    notes.append(note("tag.missing", ChatFormatting.YELLOW));
                } else {
                    if (changed) {
                        notes.append(note("tag.changed", ChatFormatting.GOLD));
                    }
                    if (!entry.getCompatibility().isCompatible()) {
                        notes.append(note("tag.other_version", ChatFormatting.RED));
                    }
                }
                this.name = entry != null ? entry.getTitle() : Component.literal(PresetStatus.fileName(id));
                this.icon = entry != null ? entry.getIconTexture() : PackLists.UNKNOWN_ICON;
                this.title = fit(font, this.name, notes);
                this.description = MultiLineLabel.create(font, TEXT_WIDTH, 2, entry != null
                    ? entry.getExtendedDescription() : Component.translatable(PREFIX + "list.missing_description"));
            }

            /** Returns " (text)" in a color */
            private static Component note(String key, ChatFormatting color) {
                return Component.literal(" (").append(Component.translatable(PREFIX + key)).append(")")
                    .withStyle(color);
            }

            /** Returns the name, cut so the notes after it fit, followed by the notes (colors kept) */
            private static FormattedCharSequence fit(Font font, Component name, Component notes) {
                FormattedCharSequence shown = ConfigList.fit(font, name, TEXT_WIDTH - font.width(notes));
                return FormattedCharSequence.composite(shown, notes.getVisualOrderText());
            }

            @Override
            public Component getNarration() {
                return this.name;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                               int mouseY, boolean hovered, float partialTick) {
                Font font = Minecraft.getInstance().font;
                graphics.blit(this.icon, left, top, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                graphics.drawString(font, this.title, left + ICON_SIZE + 2, top + 1, ConfigList.TEXT_COLOR);
                this.description.renderLeftAligned(graphics, left + ICON_SIZE + 2, top + 12, 10,
                    ConfigList.SUBTLE_COLOR);
            }

            /** Read only: a click does nothing */
            @Override
            public boolean mouseClicked(double x, double y, int button) {
                return false;
            }
        }
    }
}
