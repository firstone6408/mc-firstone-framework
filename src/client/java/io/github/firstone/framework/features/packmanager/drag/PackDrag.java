package io.github.firstone.framework.features.packmanager.drag;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.Questions;
import io.github.firstone.framework.client.screen.RowDrag;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.PackManagerFeature;
import io.github.firstone.framework.features.packmanager.preset.PresetGuard;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Dragging packs by their name in the pack screens (Resource Packs and data packs)
 *
 * <p>{@code PackListDragMixin} passes the mouse events of the two pack list widgets here; the press, the drag and
 * the drawing are the mod's shared {@link RowDrag}. A press on a pack's name
 * (not on its icon, which keeps the arrow buttons) becomes a drag once the mouse moves {@link #START_DISTANCE}
 * pixels; a press that does not move stays a normal click. On release:</p>
 * <ul>
 *   <li>within Selected: the pack moves to the place under the mouse ({@link PackLists#moveTo})</li>
 *   <li>from Available onto Selected: the pack is turned on and moved there (a pack made for another version asks
 *       first, like a click)</li>
 *   <li>from Selected onto Available: the pack is turned off (not possible for a required pack)</li>
 * </ul>
 * <p>Everything goes through the screen's own functions, as with the arrow buttons. A white line shows where the
 * pack will go; the list scrolls while the mouse is near its top or bottom; Escape cancels. Packs cannot be dragged
 * while searching, and fixed packs (sent by a server) never move.</p>
 */
public final class PackDrag {

    /** Size of the icon at the left of a row; a press there belongs to the arrow buttons */
    private static final int ICON_SIZE = 32;

    /** Height of the band at the top and bottom of the Selected list that scrolls it while dragging */
    private static final int SCROLL_EDGE = 16;

    /** Pixels the Selected list scrolls each tick while the mouse is in a scroll band */
    private static final int SCROLL_STEP = 8;

    /** Depth of the frame around Available, above the lists */
    private static final int Z_LEVEL = 400;

    /** The press and drag; a row is the pressed pack of the screen's model */
    private static final RowDrag<PackSelectionModel.Entry> DRAG = new RowDrag<>();

    /** List widget the press started in, or null */
    @Nullable
    private static TransferableSelectionList source;

    private PackDrag() {}

    /**
     * Registers the screen events that draw a drag, scroll the list and let Escape cancel
     *
     * <p>Called once from {@link PackManagerFeature#initializeClient()}. Opening any screen ends a drag.</p>
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            cancel();
            if (screen instanceof PackSelectionScreen packScreen) {
                ScreenEvents.afterRender(screen).register((s, graphics, x, y, partialTick) -> render(packScreen,
                    graphics));
                ScreenEvents.afterTick(screen).register(s -> tick(packScreen));
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) ->
                    !cancelOnEscape(key));
            }
        });
    }

    /**
     * Notes a press in a pack list; called after the list handled the click
     *
     * <p>A press counts only on a pack's name, with Drag to Reorder on, while not searching, and never for a fixed
     * pack.</p>
     *
     * @param list the list widget that was clicked
     * @param row  the row the press focused, or null if the press was not on a row (or was on the scrollbar)
     * @param x    mouse x
     * @param y    mouse y
     */
    public static void press(TransferableSelectionList list, @Nullable TransferableSelectionList.PackEntry row,
                             double x, double y) {
        cancel();
        if (row == null || !PackManagerFeature.getConfig().dragToReorder || PackLists.isSearching()
            || x - list.getRowLeft() <= ICON_SIZE
            || !(Minecraft.getInstance().screen instanceof PackSelectionScreen screen)) {
            return;
        }
        PackSelectionModel.Entry entry = PackLists.entries(PackLists.model(screen)).get(row.getPackId());
        int index = list.children().indexOf(row);
        if (entry == null || entry.isFixedPosition() || index < 0) {
            return;
        }
        source = list;
        DRAG.press(entry, x, y, list.getRowLeft(), ((RowDrag.Rows) list).rowDrag$rowTop(index));
    }

    /**
     * Follows the pressed mouse
     *
     * @param list the list widget that gets the event
     * @param x    mouse x
     * @param y    mouse y
     * @return true if a drag is going on (the list must not handle the event)
     */
    public static boolean drag(TransferableSelectionList list, double x, double y) {
        return list == source && DRAG.move(x, y);
    }

    /**
     * Ends a press: drops the pack if it was dragged
     *
     * @param list the list widget that gets the event
     * @param x    mouse x
     * @param y    mouse y
     * @return true if a drag ended (the list must not handle the event); false for a normal click
     */
    public static boolean release(TransferableSelectionList list, double x, double y) {
        if (list != source) {
            return false;
        }
        PackSelectionModel.Entry entry = DRAG.release();
        source = null;
        if (entry != null && Minecraft.getInstance().screen instanceof PackSelectionScreen screen) {
            drop(screen, list, entry, x, y);
        }
        return entry != null;
    }

    /** Forgets the press, without changing anything */
    private static void cancel() {
        DRAG.cancel();
        source = null;
    }

    /** Escape during a drag cancels it (instead of closing the screen); returns true if it did */
    private static boolean cancelOnEscape(int key) {
        if (DRAG.isDragging() && key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return false;
    }

    /**
     * Moves, turns on or turns off the dragged pack, depending on where it was dropped; a pack made for another
     * version asks first (as a click does), then the drop asks once if it leaves the preset in use
     * ({@link PresetGuard#change})
     */
    private static void drop(PackSelectionScreen screen, TransferableSelectionList from,
                             PackSelectionModel.Entry dragged, double x, double y) {
        PackLists.Parts parts = PackLists.parts(screen);
        TransferableSelectionList selectedList = parts.packManager$selectedList();
        PackSelectionModel model = parts.packManager$model();
        String id = dragged.getId();
        // the screen reads its folder again when a file changes, also while dragging: use the pack as listed now
        PackSelectionModel.Entry entry = PackLists.entries(model).get(id);
        if (entry == null) {
            return;
        }
        if (selectedList.isMouseOver(x, y)) {
            int slot = RowDrag.slot((RowDrag.Rows) selectedList, selectedList.children().size(), y);
            if (from == selectedList) {
                int index = PackLists.selectedIndex(model, id);
                PresetGuard.change(screen, () -> PackLists.moveTo(model, id, slot > index ? slot - 1 : slot));
            } else if (entry.getCompatibility().isCompatible()) {
                PresetGuard.change(screen, () -> selectAt(model, entry, slot));
            } else {
                Questions.ask(screen, Component.translatable("pack.incompatible.confirm.title"),
                    entry.getCompatibility().getConfirmation(),
                    () -> PresetGuard.change(screen, () -> selectAt(model, entry, slot)));
            }
        } else if (from == selectedList && droppedOnAvailable(parts, entry, x, y)) {
            PresetGuard.change(screen, () -> entry.unselect());
        }
    }

    /** Tells whether a pack from Selected is over Available and can be turned off there */
    private static boolean droppedOnAvailable(PackLists.Parts parts, PackSelectionModel.Entry entry,
                                              double x, double y) {
        return parts.packManager$availableList().isMouseOver(x, y) && entry.canUnselect();
    }

    /** Turns a pack on (the game puts it at its usual place) and moves it to a place of the Selected list */
    private static void selectAt(PackSelectionModel model, PackSelectionModel.Entry entry, int slot) {
        entry.select();
        PackLists.moveTo(model, entry.getId(), slot);
    }

    /** Scrolls the Selected list while the dragged pack is held near its top or bottom */
    private static void tick(PackSelectionScreen screen) {
        if (!DRAG.isDragging()) {
            return;
        }
        TransferableSelectionList list = PackLists.parts(screen).packManager$selectedList();
        if (DRAG.mouseX() < list.getX() || DRAG.mouseX() > list.getRight()) {
            return;
        }
        if (DRAG.mouseY() < list.getY() + SCROLL_EDGE) {
            list.setScrollAmount(list.getScrollAmount() - SCROLL_STEP);
        } else if (DRAG.mouseY() > list.getBottom() - SCROLL_EDGE) {
            list.setScrollAmount(list.getScrollAmount() + SCROLL_STEP);
        }
    }

    /** Draws where the pack will go and the pack itself under the mouse */
    private static void render(PackSelectionScreen screen, GuiGraphics graphics) {
        TransferableSelectionList from = source;
        PackSelectionModel.Entry entry = DRAG.pressed();
        if (!DRAG.isDragging() || from == null || entry == null) {
            return;
        }
        PackLists.Parts parts = PackLists.parts(screen);
        TransferableSelectionList selectedList = parts.packManager$selectedList();
        TransferableSelectionList availableList = parts.packManager$availableList();
        if (selectedList.isMouseOver(DRAG.mouseX(), DRAG.mouseY())) {
            RowDrag.Rows rows = (RowDrag.Rows) selectedList;
            RowDrag.drawLine(graphics, rows, RowDrag.slot(rows, selectedList.children().size(), DRAG.mouseY()),
                selectedList.getRowLeft(), selectedList.getRowRight() - 8, selectedList.getY(),
                selectedList.getBottom());
        } else if (from == selectedList && droppedOnAvailable(parts, entry, DRAG.mouseX(), DRAG.mouseY())) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, Z_LEVEL);
            graphics.renderOutline(availableList.getX(), availableList.getY(), availableList.getWidth(),
                availableList.getHeight(), RowDrag.LINE_COLOR);
            graphics.pose().popPose();
        }
        Font font = Minecraft.getInstance().font;
        int width = from.getRowWidth() - 8;
        DRAG.drawDragged(graphics, width, ICON_SIZE, (left, top) -> {
            graphics.blit(entry.getIconTexture(), left, top, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            graphics.drawString(font, ConfigList.fit(font, entry.getTitle(), width - ICON_SIZE - 4),
                left + ICON_SIZE + 2, top + 1, ConfigList.TEXT_COLOR);
        });
    }
}
