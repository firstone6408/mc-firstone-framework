package io.github.firstone.framework.features.packmanager.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.HeadedList;
import io.github.firstone.framework.client.screen.ListScreen;
import io.github.firstone.framework.client.screen.Questions;
import io.github.firstone.framework.client.screen.RowDrag;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.preset.PackFiles;
import io.github.firstone.framework.features.packmanager.preset.PresetGuard;
import io.github.firstone.framework.features.packmanager.preset.PresetStatus;
import io.github.firstone.framework.features.packmanager.preset.PresetStore;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The presets: apply, create, edit, copy, delete and order them
 *
 * <p>Opened from the preset button of the Resource Packs screen (the "pack screen"). Applying a preset fills the pack
 * screen's lists with the preset's packs through the screen's own functions ({@link PackLists#apply}) and goes back to
 * it, so the player sees the result; pressing Done there makes the game save and load the packs as after choosing
 * them by hand. Applying over custom packs (matching no preset) asks first, packs made for another version ask as in
 * the game, and missing or changed packs are skipped and named in a message.</p>
 *
 * <p>Each row shows the preset's icon, name, description and packs (with how many are missing, changed or made for
 * another version). The preset in use has a green bar, a light green background and "In Use". On a hovered or
 * selected row, the arrow on the icon applies it (as in the pack lists) and small buttons on the right edit, copy and
 * delete it. A double click edits, Enter applies, Delete deletes. Presets are ordered by dragging them by their
 * name ({@link RowDrag}). Below the list: New from Current, Open Folder, Back.</p>
 */
public class PresetListScreen extends ListScreen<PresetListScreen.PresetList> {

    /** Key prefix of this screen */
    private static final String PREFIX = "firstone-framework.pack_manager.presets.";

    /** Width and height of a row */
    private static final int ROW_WIDTH = 300;
    private static final int ROW_HEIGHT = 36;

    /** Size of the icon area at the left of a row; a click there applies the preset */
    private static final int ICON_SIZE = 32;

    /** Size of the edit, copy and delete buttons of a row, the gap between them and the room they keep */
    private static final int ACTION_SIZE = 16;
    private static final int ACTION_GAP = 2;
    private static final int ACTIONS_WIDTH = 3 * ACTION_SIZE + 2 * ACTION_GAP;

    /** Width of New from Current (its text is longer than the other buttons') */
    private static final int NEW_BUTTON_WIDTH = 120;

    /** Distance of the "no presets yet" text below the heading */
    private static final int EMPTY_TOP = 20;

    /** Two clicks on one row within this time are a double click (as in the game's world list) */
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    /** The preset in use: a green bar on the left and a light green background */
    private static final int IN_USE_BAR = 0xFF55FF55;
    private static final int IN_USE_BACKGROUND = 0x2855FF55;
    private static final int IN_USE_BAR_WIDTH = 2;

    /** Darkening of the icon under the mouse (as in the pack lists) */
    private static final int ICON_HOVER = 0xA0909090;

    /** Arrow drawn on the icon under the mouse (the game's "select" arrow of the pack lists) */
    private static final ResourceLocation SELECT_SPRITE =
        ResourceLocation.withDefaultNamespace("transferable_list/select");
    private static final ResourceLocation SELECT_HIGHLIGHTED_SPRITE =
        ResourceLocation.withDefaultNamespace("transferable_list/select_highlighted");

    /** The game's button, under the pictures of the edit, copy and delete buttons */
    private static final ResourceLocation BUTTON_SPRITE = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    /** The Resource Packs screen the presets were opened from; applying fills its lists */
    private final PackSelectionScreen packScreen;

    /** Preset to select when the screen is shown again (one just created or saved in the form), or null */
    @Nullable
    private PackPreset selectNext;

    /** {@link PackFiles#version} the rows were made with */
    private int filesVersion;

    /**
     * Creates the screen and reads the presets file again (so changes made by hand show up)
     *
     * @param packScreen the Resource Packs screen to fill and to return to
     */
    public PresetListScreen(PackSelectionScreen packScreen) {
        super(Component.translatable("firstone-framework.pack_manager.name"));
        this.packScreen = packScreen;
        PresetStore.load();
    }

    /**
     * Tells whether a pack screen offers presets: the Resource Packs screen, not a data pack screen and not the
     * screen that chooses a preset's packs
     *
     * @param screen a pack screen
     * @return true if it gets the preset button
     */
    public static boolean isOffered(PackSelectionScreen screen) {
        return PackLists.isResourcePackScreen(screen) && !(screen instanceof PresetPacksScreen);
    }

    @Override
    protected PresetList createList(int width, int height, int top) {
        return new PresetList(this.minecraft, width, height, top);
    }

    @Override
    protected void fillList() {
        refresh(null);
    }

    /** New from Current, Open Folder, Back */
    @Override
    protected void addFooterButtons(LinearLayout footer) {
        footer.addChild(footerButton(Component.translatable(PREFIX + "new"), NEW_BUTTON_WIDTH,
            this::createFromCurrent));
        footer.addChild(footerButton(Component.translatable(PREFIX + "folder"), FOOTER_BUTTON_WIDTH,
            this::openFolder));
        footer.addChild(footerButton(CommonComponents.GUI_BACK, FOOTER_BUTTON_WIDTH, this::onClose));
    }

    @Override
    public void added() {
        super.added();
        // coming back from the form or a question: show the changes
        if (isBuilt()) {
            PackPreset select = this.selectNext != null ? this.selectNext : selected();
            this.selectNext = null;
            refresh(select);
        }
    }

    /** Shows the rows again when a pack file read in the background is known (not while a row is dragged) */
    @Override
    public void tick() {
        if (this.filesVersion != PackFiles.version() && list().drag.pressed() == null) {
            this.filesVersion = PackFiles.version();
            refresh(selected());
        }
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && list().drag.isDragging()) {
            list().drag.cancel();
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.packScreen);
    }

    /** Returns the selected preset, or null */
    @Nullable
    private PackPreset selected() {
        Row row = list().getSelected();
        return row == null ? null : row.preset;
    }

    /** Fills the list again (statuses may have changed) and selects a preset again */
    private void refresh(@Nullable PackPreset select) {
        this.filesVersion = PackFiles.version();
        PackSelectionModel model = PackLists.model(this.packScreen);
        Map<String, PackSelectionModel.Entry> entries = PackLists.entries(model);
        PackPreset inUse = PresetStatus.current(model);
        List<Row> rows = new ArrayList<>();
        for (PackPreset preset : PresetStore.getPresets()) {
            rows.add(new Row(preset, PresetStatus.of(preset, model), preset == inUse, icons(preset, entries)));
        }
        list().fill(rows, select);
    }

    // ─── Actions ────────────────────────────────────────────────────────────

    /**
     * Fills the Resource Packs screen with a preset's packs and goes back to it; asks first over custom packs, and
     * if packs made for another version would be turned on
     */
    private void apply(PackPreset preset) {
        PackFiles.awaitAll(PresetStore.getPresets()); // decide on the files as they are
        if (PresetStatus.current(PackLists.model(this.packScreen)) != null) {
            applyChecked(preset);
            return;
        }
        // custom packs are not kept anywhere: once Done is pressed after applying, they are gone
        Questions.ask(this, Component.translatable(PREFIX + "replace_custom_title", preset.name),
            Component.translatable(PREFIX + "replace_custom_message"), Component.translatable(PREFIX + "apply"),
            () -> applyChecked(preset));
    }

    /** Applies a preset, asking first if packs made for another version would be turned on */
    private void applyChecked(PackPreset preset) {
        PresetStatus status = PresetStatus.of(preset, PackLists.model(this.packScreen));
        List<PackSelectionModel.Entry> asking = status.incompatible().stream().filter(entry -> !entry.isSelected())
            .toList();
        if (asking.isEmpty()) {
            fill(preset, status);
        } else {
            Questions.ask(this, Component.translatable("pack.incompatible.confirm.title"),
                Component.translatable(PREFIX + "incompatible_confirm", names(asking)), () -> fill(preset, status));
        }
    }

    /**
     * Fills the Resource Packs screen with the preset's packs that are neither missing nor changed (a changed file is
     * used only after it was accepted), names the skipped packs, and shows the screen
     */
    private void fill(PackPreset preset, PresetStatus status) {
        PackLists.parts(this.packScreen).packManager$clearSearch();
        PresetGuard.run(() -> PackLists.apply(PackLists.model(this.packScreen), status.toApply()));
        this.minecraft.setScreen(this.packScreen);
        if (!status.missing().isEmpty() || !status.changed().isEmpty()) {
            MutableComponent skipped = Component.empty();
            if (!status.missing().isEmpty()) {
                skipped.append(Component.translatable(PREFIX + "skipped_missing", fileNames(status.missing())));
            }
            if (!status.changed().isEmpty()) {
                if (!status.missing().isEmpty()) {
                    skipped.append(" ");
                }
                skipped.append(Component.translatable(PREFIX + "skipped_changed", fileNames(status.changed())));
            }
            this.minecraft.getToasts().addToast(SystemToast.multiline(this.minecraft,
                SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                Component.translatable(PREFIX + "skipped_toast", preset.name), skipped));
        }
    }

    /** Opens the form of a saved preset */
    private void edit(PackPreset preset) {
        this.minecraft.setScreen(PresetEditScreen.edit(this, preset, this.packScreen, this::selectNext));
    }

    /** Opens the form for a new preset that starts as a copy of a preset (named "… (Copy)") */
    private void copy(PackPreset preset) {
        PackPreset values = preset.copy();
        values.name = Component.translatable(PREFIX + "copy_name", preset.name).getString();
        this.minecraft.setScreen(PresetEditScreen.create(this, values, this.packScreen, this::selectNext));
    }

    /** Asks, then deletes a preset */
    private void delete(PackPreset preset) {
        Questions.ask(this, Component.translatable(PREFIX + "delete_title", preset.name),
            Component.translatable(PREFIX + "delete_message"), () -> {
                PresetStore.delete(preset);
                refresh(null);
            });
    }

    /** Selects a preset when the screen is shown again */
    private void selectNext(PackPreset preset) {
        this.selectNext = preset;
    }

    /** Opens the form for a new preset with the packs the Selected list holds now */
    private void createFromCurrent() {
        PackPreset values = new PackPreset();
        values.name = Component.translatable(PREFIX + "new_name").getString();
        values.packs = new ArrayList<>(PackLists.selectedIds(PackLists.model(this.packScreen)));
        this.minecraft.setScreen(PresetEditScreen.create(this, values, this.packScreen, this::selectNext));
    }

    /** Opens the folder of the presets file in the system's file browser */
    private void openFolder() {
        try {
            Files.createDirectories(PresetStore.folder());
        } catch (IOException e) {
            return;
        }
        Util.getPlatform().openPath(PresetStore.folder());
    }

    // ─── Rows ───────────────────────────────────────────────────────────────

    /**
     * Returns the icons of a preset: the chosen pack's icon, else the icons of its top packs that are not always on
     * (up to four), else the top pack's icon (also used by {@link PresetButton})
     *
     * @param preset  the preset
     * @param entries the packs of the Resource Packs screen, by id
     * @return one to four icon textures, never empty
     */
    static List<ResourceLocation> icons(PackPreset preset, Map<String, PackSelectionModel.Entry> entries) {
        PackSelectionModel.Entry chosen = entries.get(preset.icon);
        if (chosen != null) {
            return List.of(chosen.getIconTexture());
        }
        List<PackSelectionModel.Entry> packs = preset.packs.stream().map(entries::get).filter(entry -> entry != null)
            .toList();
        List<ResourceLocation> icons = packs.stream().filter(entry -> !entry.isRequired()).limit(4)
            .map(entry -> entry.getIconTexture()).toList();
        if (!icons.isEmpty()) {
            return icons;
        }
        return packs.isEmpty() ? List.of(PackLists.UNKNOWN_ICON) : List.of(packs.getFirst().getIconTexture());
    }

    /** Returns the names of packs, separated by commas */
    private static Component names(List<PackSelectionModel.Entry> packs) {
        MutableComponent names = Component.empty();
        for (int index = 0; index < packs.size(); index++) {
            if (index > 0) {
                names.append(", ");
            }
            names.append(packs.get(index).getTitle());
        }
        return names;
    }

    /** Returns the file names of packs, separated by commas */
    private static String fileNames(List<String> ids) {
        return ids.stream().map(PresetStatus::fileName).collect(Collectors.joining(", "));
    }

    /** The list of presets under the heading "Presets"; a preset is moved by dragging it by its name */
    final class PresetList extends HeadedList<Row> {

        /** Dragging a preset to another place */
        private final RowDrag<Row> drag = new RowDrag<>();

        private PresetList(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, ROW_HEIGHT, ROW_WIDTH, Component.translatable(PREFIX + "title"),
                null, Component.translatable(PREFIX + "empty"));
        }

        /** The "no presets yet" text sits a little lower than in the other lists */
        @Override
        protected int emptyTextTop() {
            return EMPTY_TOP;
        }

        /** Shows these rows, keeping the scroll, and selects the row of a preset (or none) */
        private void fill(List<Row> rows, @Nullable PackPreset select) {
            double scroll = getScrollAmount();
            replaceEntries(rows);
            setScrollAmount(scroll);
            for (Row row : rows) {
                if (row.preset == select) {
                    setSelected(row);
                }
            }
        }

        @Override
        public boolean keyPressed(int key, int scancode, int modifiers) {
            Row row = getSelected();
            if (row != null && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
                apply(row.preset);
                return true;
            }
            if (row != null && key == GLFW.GLFW_KEY_DELETE) {
                delete(row.preset);
                return true;
            }
            return super.keyPressed(key, scancode, modifiers);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            this.drag.cancel();
            boolean clicked = super.mouseClicked(x, y, button);
            Row row = getSelected();
            if (clicked && button == 0 && row != null && row.isMouseOver(x, y) && row.onName(x)) {
                this.drag.press(row, x, y, getRowLeft(), getRowTop(children().indexOf(row)));
            }
            return clicked;
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            if (button == 0 && this.drag.move(x, y)) {
                return true;
            }
            return super.mouseDragged(x, y, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            Row row = this.drag.release();
            if (row != null) {
                int from = children().indexOf(row);
                int slot = RowDrag.slot(this, children().size(), y);
                PresetStore.move(row.preset, slot > from ? slot - 1 : slot);
                refresh(row.preset);
                return true;
            }
            return super.mouseReleased(x, y, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int x, int y, float partialTick) {
            super.renderWidget(graphics, x, y, partialTick);
            Row row = this.drag.pressed();
            if (this.drag.isDragging() && row != null) {
                RowDrag.drawLine(graphics, this, RowDrag.slot(this, children().size(), this.drag.mouseY()),
                    getRowLeft(), getRowRight() - 4, getY(), getBottom());
                this.drag.drawDragged(graphics, ROW_WIDTH - 4, ICON_SIZE,
                    (left, top) -> row.drawContent(graphics, left, top, ROW_WIDTH - 4, false));
            }
        }
    }

    /** What a button of a row does */
    private enum Action {
        EDIT("edit"), COPY("copy"), DELETE("delete");

        private final ResourceLocation sprite;
        private final Component tooltip;

        Action(String name) {
            this.sprite = ResourceLocation.fromNamespaceAndPath("firstone-framework", "pack_manager/" + name);
            this.tooltip = Component.translatable(PREFIX + name);
        }
    }

    /** One preset in the list */
    final class Row extends ObjectSelectionList.Entry<Row> {

        private final PackPreset preset;
        private final PresetStatus status;
        private final boolean inUse;
        private final List<ResourceLocation> icons;

        /** Where the row was drawn last (to tell what a click hits) */
        private int left;
        private int top;
        private int width;

        /** When this row was last clicked, for double clicks */
        private long lastClick;

        private Row(PackPreset preset, PresetStatus status, boolean inUse, List<ResourceLocation> icons) {
            this.preset = preset;
            this.status = status;
            this.inUse = inUse;
            this.icons = icons;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", this.preset.name);
        }

        /** Left edge of the row's buttons */
        private int actionsLeft() {
            return this.left + this.width - 4 - ACTIONS_WIDTH;
        }

        /** Returns the button under the mouse, or null (the buttons sit in the middle band of the row) */
        @Nullable
        private Action actionAt(double x, double y) {
            int buttonTop = this.top + (ICON_SIZE - ACTION_SIZE) / 2;
            if (y < buttonTop || y >= buttonTop + ACTION_SIZE || x < actionsLeft()) {
                return null;
            }
            int index = (int) (x - actionsLeft()) / (ACTION_SIZE + ACTION_GAP);
            boolean inGap = (x - actionsLeft()) % (ACTION_SIZE + ACTION_GAP) >= ACTION_SIZE;
            return index < Action.values().length && !inGap ? Action.values()[index] : null;
        }

        /** Tells whether a mouse x is on the texts (not the icon or the buttons): a press there drags */
        private boolean onName(double x) {
            return x - this.left > ICON_SIZE && x < actionsLeft();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                           int mouseY, boolean hovered, float partialTick) {
            this.left = left;
            this.top = top;
            this.width = width;
            PresetList presets = list();
            boolean showButtons = hovered || presets.getSelected() == this;
            if (this.inUse) {
                graphics.fill(left - 1, top - 1, left + width - 3, top + height + 1, IN_USE_BACKGROUND);
                graphics.fill(left - 2 - IN_USE_BAR_WIDTH, top - 1, left - 2, top + height + 1, IN_USE_BAR);
            }
            drawContent(graphics, left, top, width, !showButtons);
            if (hovered || (presets.getSelected() == this && presets.isFocused())) {
                graphics.fill(left, top, left + ICON_SIZE, top + ICON_SIZE, ICON_HOVER);
                graphics.blitSprite(hovered && mouseX - left < ICON_SIZE ? SELECT_HIGHLIGHTED_SPRITE : SELECT_SPRITE,
                    left, top, ICON_SIZE, ICON_SIZE);
            }
            Action hoveredAction = hovered ? actionAt(mouseX, mouseY) : null;
            if (showButtons) {
                int buttonTop = top + (ICON_SIZE - ACTION_SIZE) / 2;
                for (Action action : Action.values()) {
                    int x = actionsLeft() + action.ordinal() * (ACTION_SIZE + ACTION_GAP);
                    graphics.blitSprite(action == hoveredAction ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE, x,
                        buttonTop, ACTION_SIZE, ACTION_SIZE);
                    graphics.blitSprite(action.sprite, x, buttonTop, ACTION_SIZE, ACTION_SIZE);
                }
            }
            if (hoveredAction != null) {
                setTooltipForNextRenderPass(PresetListScreen.this.font.split(hoveredAction.tooltip, ROW_WIDTH));
            } else if (hovered) {
                List<FormattedCharSequence> tooltip = tooltip();
                if (!tooltip.isEmpty()) {
                    setTooltipForNextRenderPass(tooltip);
                }
            }
        }

        /** Draws the icons and texts of the row; "In Use" (right middle) only while the buttons are not shown */
        private void drawContent(GuiGraphics graphics, int left, int top, int width, boolean showInUse) {
            if (this.icons.size() == 1) {
                graphics.blit(this.icons.getFirst(), left, top, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                    ICON_SIZE);
            } else {
                int half = ICON_SIZE / 2;
                for (int index = 0; index < this.icons.size(); index++) {
                    graphics.blit(this.icons.get(index), left + index % 2 * half, top + index / 2 * half, 0.0F, 0.0F,
                        half, half, half, half);
                }
            }

            Font font = PresetListScreen.this.font;
            int textLeft = left + ICON_SIZE + 2;
            // the room of the buttons is kept free, so the texts do not move when they show
            int textWidth = width - ICON_SIZE - 10 - ACTIONS_WIDTH;
            if (this.inUse && showInUse) {
                // right middle: where the row's buttons show while the mouse is on it
                Component inUse = Component.translatable(PREFIX + "in_use").withStyle(ChatFormatting.GREEN);
                graphics.drawString(font, inUse, left + width - 4 - font.width(inUse),
                    top + (ICON_SIZE - font.lineHeight) / 2 + 1, ConfigList.TEXT_COLOR);
            }
            graphics.drawString(font, ConfigList.fit(font, Component.literal(this.preset.name), textWidth), textLeft,
                top + 1, ConfigList.TEXT_COLOR);
            graphics.drawString(font, ConfigList.fit(font, Component.literal(this.preset.description), textWidth),
                textLeft, top + 12, ConfigList.SUBTLE_COLOR);
            graphics.drawString(font, ConfigList.fit(font, summary(), textWidth), textLeft, top + 23,
                ConfigList.SUBTLE_COLOR);
        }

        /** Returns "N packs", with how many are missing, changed or made for another version */
        private Component summary() {
            MutableComponent summary = Component.translatable(PREFIX + "packs", this.preset.packs.size());
            note(summary, this.status.missing().size(), "missing", ChatFormatting.YELLOW);
            note(summary, this.status.changed().size(), "changed", ChatFormatting.GOLD);
            note(summary, this.status.incompatible().size(), "incompatible", ChatFormatting.RED);
            return summary;
        }

        /** Adds " · N <what>" in a color, if there are any */
        private static void note(MutableComponent summary, int count, String what, ChatFormatting color) {
            if (count > 0) {
                summary.append(" · ").append(Component.translatable(PREFIX + what, count).withStyle(color));
            }
        }

        /** Returns the full description and the names of the missing, changed and other-version packs */
        private List<FormattedCharSequence> tooltip() {
            Font font = PresetListScreen.this.font;
            List<FormattedCharSequence> lines = new ArrayList<>();
            if (!this.preset.description.isBlank()) {
                lines.addAll(font.split(Component.literal(this.preset.description), ROW_WIDTH));
            }
            if (!this.status.missing().isEmpty()) {
                lines.addAll(font.split(Component.translatable(PREFIX + "missing_list",
                    fileNames(this.status.missing())).withStyle(ChatFormatting.YELLOW), ROW_WIDTH));
            }
            if (!this.status.changed().isEmpty()) {
                lines.addAll(font.split(Component.translatable(PREFIX + "changed_list",
                    fileNames(this.status.changed())).withStyle(ChatFormatting.GOLD), ROW_WIDTH));
            }
            if (!this.status.incompatible().isEmpty()) {
                lines.addAll(font.split(Component.translatable(PREFIX + "incompatible_list",
                    names(this.status.incompatible())).withStyle(ChatFormatting.RED), ROW_WIDTH));
            }
            return lines;
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) {
                return true;
            }
            Action action = actionAt(x, y);
            if (action != null) {
                switch (action) {
                    case EDIT -> edit(this.preset);
                    case COPY -> copy(this.preset);
                    case DELETE -> delete(this.preset);
                }
            } else if (x - this.left <= ICON_SIZE) {
                apply(this.preset);
            } else if (Util.getMillis() - this.lastClick < DOUBLE_CLICK_MILLIS) {
                edit(this.preset);
            }
            this.lastClick = Util.getMillis();
            return true;
        }
    }

    /**
     * The preset button above the Selected list of the Resource Packs screen
     *
     * <p>Shows the preset the Selected list matches, with its icon ("Preset: name"), or "Preset: Custom" with a sliders
     * icon when the packs match no preset; {@link #update} marks it for an update after a change of the lists, done
     * when its text is needed next. Pressing it opens the presets. Added by {@code PackScreenMixin}.</p>
     */
    public static class PresetButton extends Button {

        private static final String KEY = "firstone-framework.pack_manager.preset_button";

        /** Size of the icon and its distance from the button's left edge: inside the button's 3-pixel frame */
        private static final int BUTTON_ICON_SIZE = 14;
        private static final int ICON_MARGIN = 3;

        /**
         * Room kept on each side of the text: the icon on the left, the same on the right so the text stays centered
         */
        private static final int TEXT_MARGIN = ICON_MARGIN + BUTTON_ICON_SIZE + 2;

        /** Icon of custom packs */
        private static final ResourceLocation CUSTOM_SPRITE =
            ResourceLocation.fromNamespaceAndPath("firstone-framework", "pack_manager/custom");

        /** The Resource Packs screen's model */
        private final PackSelectionModel model;

        /** Icon of the preset in use (a pack icon texture), or null for custom packs */
        @Nullable
        private ResourceLocation icon;

        /** True when the lists changed since the button was updated */
        private boolean outdated = true;

        /** {@link PackFiles#version} the button was updated with */
        private int filesVersion;

        /** {@link PresetStore#version} the button was updated with */
        private int presetsVersion;

        /**
         * Creates the button
         *
         * @param model   the Resource Packs screen's model
         * @param width   its width
         * @param height  its height
         * @param onPress what pressing it does (open the presets)
         */
        public PresetButton(PackSelectionModel model, int width, int height, OnPress onPress) {
            super(0, 0, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.model = model;
        }

        /**
         * Notes that the lists changed; the button finds the preset again when its text is needed next (drawing or
         * narration), once however many steps the lists changed in
         */
        public void update() {
            this.outdated = true;
        }

        /** The text, found again first if the lists, the presets or the known pack files changed */
        @Override
        public Component getMessage() {
            if (this.outdated || this.filesVersion != PackFiles.version()
                || this.presetsVersion != PresetStore.version()) {
                show();
            }
            return super.getMessage();
        }

        /** Shows the preset the Selected list matches now, or Custom */
        private void show() {
            this.outdated = false;
            this.filesVersion = PackFiles.version();
            this.presetsVersion = PresetStore.version();
            PackSelectionModel model = this.model;
            PackPreset preset = PresetStatus.current(model);
            if (preset == null) {
                this.icon = null;
                setMessage(Component.translatable(KEY, Component.translatable(KEY + ".custom")
                    .withStyle(ChatFormatting.GRAY)));
                setTooltip(Tooltip.create(Component.translatable(KEY + ".custom.tooltip")));
            } else {
                this.icon = icons(preset, PackLists.entries(model)).getFirst();
                setMessage(Component.translatable(KEY, preset.name));
                setTooltip(Tooltip.create(Component.translatable(KEY + ".tooltip", preset.name)));
            }
        }

        /** Draws the icon on the left and the text centered in the room beside it */
        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            Component message = getMessage(); // first, so the icon belongs to the same preset
            int x = getX() + ICON_MARGIN;
            int y = getY() + (getHeight() - BUTTON_ICON_SIZE) / 2;
            if (this.icon == null) {
                graphics.blitSprite(CUSTOM_SPRITE, x, y, BUTTON_ICON_SIZE, BUTTON_ICON_SIZE);
            } else {
                int size = BUTTON_ICON_SIZE;
                graphics.blit(this.icon, x, y, 0.0F, 0.0F, size, size, size, size);
            }
            renderScrollingString(graphics, font, message, getX() + TEXT_MARGIN, getY(),
                getX() + getWidth() - TEXT_MARGIN, getY() + getHeight(), color);
        }
    }
}
