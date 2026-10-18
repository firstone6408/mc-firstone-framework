package io.github.firstone.framework.features.packmanager.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.HeadedList;
import io.github.firstone.framework.client.screen.ListScreen;
import io.github.firstone.framework.features.packmanager.preset.PackFiles;
import io.github.firstone.framework.features.packmanager.preset.PresetStatus;
import io.github.firstone.framework.features.packmanager.preset.PresetStore.PackPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Review Changed: chooses which changed pack files a preset accepts ({@link PackFiles})
 *
 * <p>Opened from the preset form when some of the preset's zip packs are not the files the preset was saved with.
 * Lists only those packs, in the preset's order, each with a box to tick, its icon, its name and its size then and
 * now. Nothing is ticked at first. Accept (usable once something is ticked) accepts the ticked files into the form's
 * draft and goes back; the form's Save keeps them. Cancel and Escape change nothing. A click on a row, Space or Enter
 * ticks it; Select All ticks every row, Select None clears them.</p>
 */
public class ChangedFilesScreen extends ListScreen<ChangedFilesScreen.FileList> {

    private static final String KEY = "firstone-framework.pack_manager.changed.";

    /** Row width and height, the size of the box and of the icon */
    private static final int ROW_WIDTH = 300;
    private static final int ROW_HEIGHT = 24;
    private static final int BOX_SIZE = 16;
    private static final int ICON_SIZE = 20;

    /** The game's check box (as in the game's options) */
    private static final ResourceLocation BOX_SPRITE = ResourceLocation.withDefaultNamespace("widget/checkbox");
    private static final ResourceLocation BOX_TICKED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/checkbox_selected");
    private static final ResourceLocation BOX_HIGHLIGHTED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");
    private static final ResourceLocation BOX_TICKED_HIGHLIGHTED_SPRITE =
        ResourceLocation.withDefaultNamespace("widget/checkbox_selected_highlighted");

    /** Screen to return to (the preset form) */
    private final Screen back;

    /** The form's draft, which gets the accepted files */
    private final PackPreset draft;

    /** The packs of the Resource Packs screen, by id (icons and names) */
    private final Map<String, PackSelectionModel.Entry> entries;

    /** The changed packs, in the preset's order */
    private final List<String> changed;

    /** The ticked packs */
    private final List<String> ticked = new ArrayList<>();

    private Button selectAllButton;
    private Button acceptButton;

    /**
     * Creates the screen
     *
     * @param back    the preset form
     * @param draft   the form's draft
     * @param entries the packs of the Resource Packs screen, by id
     * @param changed the draft's changed packs, in its order
     */
    public ChangedFilesScreen(Screen back, PackPreset draft, Map<String, PackSelectionModel.Entry> entries,
                              List<String> changed) {
        super(Component.translatable("firstone-framework.pack_manager.name"));
        this.back = back;
        this.draft = draft;
        this.entries = entries;
        this.changed = List.copyOf(changed);
    }

    @Override
    protected FileList createList(int width, int height, int top) {
        return new FileList(this.minecraft, width, height, top);
    }

    @Override
    protected void fillList() {
        for (String id : this.changed) {
            list().addRow(new FileRow(id));
        }
    }

    /** Select All, Cancel, Accept */
    @Override
    protected void addFooterButtons(LinearLayout footer) {
        this.selectAllButton = footer.addChild(footerButton(Component.empty(), FOOTER_BUTTON_WIDTH, this::tickAll));
        footer.addChild(footerButton(CommonComponents.GUI_CANCEL, FOOTER_BUTTON_WIDTH, this::onClose));
        this.acceptButton = footer.addChild(footerButton(Component.translatable(KEY + "accept"), FOOTER_BUTTON_WIDTH,
            this::accept));
        updateButtons();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.back);
    }

    /** Ticks a row, or clears its tick */
    private void toggle(String id) {
        if (!this.ticked.remove(id)) {
            this.ticked.add(id);
        }
        updateButtons();
    }

    /** Ticks every row, or clears every tick when all are ticked */
    private void tickAll() {
        boolean all = this.ticked.size() == this.changed.size();
        this.ticked.clear();
        if (!all) {
            this.ticked.addAll(this.changed);
        }
        updateButtons();
    }

    /** Accept needs a tick; Select All becomes Select None when everything is ticked */
    private void updateButtons() {
        this.acceptButton.active = !this.ticked.isEmpty();
        this.selectAllButton.setMessage(Component.translatable(KEY
            + (this.ticked.size() == this.changed.size() ? "select_none" : "select_all")));
    }

    /** Accepts the ticked files into the draft, in the preset's order, and goes back to the form */
    private void accept() {
        PackFiles.accept(this.draft, this.changed.stream().filter(this.ticked::contains).toList());
        this.minecraft.setScreen(this.back);
    }

    /** Returns a size in bytes as KB or MB */
    private static String size(long bytes) {
        if (bytes < 0) {
            return "?";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** The changed packs, under a heading and a short explanation; Space or Enter ticks the selected row */
    final class FileList extends HeadedList<FileRow> {

        private FileList(Minecraft minecraft, int width, int height, int top) {
            super(minecraft, width, height, top, ROW_HEIGHT, ROW_WIDTH, Component.translatable(KEY + "heading"),
                Component.translatable(KEY + "message"), null);
        }

        /** Adds a row */
        private void addRow(FileRow row) {
            addEntry(row);
        }

        @Override
        public boolean keyPressed(int key, int scancode, int modifiers) {
            FileRow row = getSelected();
            if (row != null && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER)) {
                toggle(row.id);
                return true;
            }
            return super.keyPressed(key, scancode, modifiers);
        }
    }

    /** One changed pack: box, icon, name, size then and now */
    final class FileRow extends ObjectSelectionList.Entry<FileRow> {

        private final String id;
        private final Component name;
        @Nullable
        private final ResourceLocation icon;
        private final Component sizes;

        private FileRow(String id) {
            this.id = id;
            PackSelectionModel.Entry entry = ChangedFilesScreen.this.entries.get(id);
            this.name = entry != null ? entry.getTitle() : Component.literal(PresetStatus.fileName(id));
            this.icon = entry != null ? entry.getIconTexture() : null;
            this.sizes = Component.translatable(KEY + "sizes",
                size(PackFiles.savedSize(ChangedFilesScreen.this.draft, id)), size(PackFiles.currentSize(id)));
        }

        @Override
        public Component getNarration() {
            return Component.translatable(KEY + (ChangedFilesScreen.this.ticked.contains(this.id)
                ? "narration_ticked" : "narration_not_ticked"), this.name);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                           int mouseY, boolean hovered, float partialTick) {
            Font font = ChangedFilesScreen.this.font;
            boolean isTicked = ChangedFilesScreen.this.ticked.contains(this.id);
            ResourceLocation box = isTicked
                ? (hovered ? BOX_TICKED_HIGHLIGHTED_SPRITE : BOX_TICKED_SPRITE)
                : (hovered ? BOX_HIGHLIGHTED_SPRITE : BOX_SPRITE);
            graphics.blitSprite(box, left, top + (height - BOX_SIZE) / 2, BOX_SIZE, BOX_SIZE);
            int iconX = left + BOX_SIZE + 4;
            if (this.icon != null) {
                graphics.blit(this.icon, iconX, top, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            }
            int textX = iconX + ICON_SIZE + 4;
            int sizesWidth = font.width(this.sizes);
            int textY = top + (height - font.lineHeight) / 2 + 1;
            graphics.drawString(font, ConfigList.fit(font, this.name, left + width - 8 - sizesWidth - textX), textX,
                textY, ConfigList.TEXT_COLOR);
            graphics.drawString(font, this.sizes, left + width - 4 - sizesWidth, textY, ConfigList.SUBTLE_COLOR);
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            if (button == 0) {
                toggle(this.id);
            }
            return true;
        }
    }
}
