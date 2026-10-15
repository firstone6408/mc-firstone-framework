package io.github.firstone.framework.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/**
 * Dragging a row of a list to another place: the press, the move that turns it into a drag, where it would go, and
 * the drawing (a white line at that place and the row following the mouse)
 *
 * <p>The list keeps one {@code RowDrag} and passes its mouse events: {@link #press} when a row is pressed where it
 * can be dragged, {@link #move} while the mouse moves (true once it moved {@link #START_DISTANCE} pixels: a drag, not
 * a click), {@link #release} when it is let go. {@link #slot} tells the place under the mouse. Escape should call
 * {@link #cancel}. Used by the presets list and the pack lists of Pack Manager.</p>
 *
 * @param <T> what a row is (the row itself, or an id)
 */
public final class RowDrag<T> {

    /** Pixels the mouse must move while pressed before a press becomes a drag */
    public static final int START_DISTANCE = 4;

    /** Color of the line that shows where the row goes */
    public static final int LINE_COLOR = 0xFFFFFFFF;

    /** Background and frame of the dragged row */
    private static final int BACKGROUND = 0xFF101010;
    private static final int FRAME = 0xFF808080;

    /** Depth of what is drawn while dragging, above the lists */
    private static final int Z_LEVEL = 400;

    /** Space between rows, below each row's content */
    private static final int ROW_GAP = 4;

    @Nullable
    private T pressed;
    private double pressX;
    private double pressY;
    private double mouseX;
    private double mouseY;
    private int grabX;
    private int grabY;
    private boolean dragging;

    /**
     * Geometry of a list's rows, for {@link #slot} and the line (the game's lists keep it protected)
     */
    public interface Rows {

        /**
         * Returns the top of a row on screen (as the list draws it, with the scroll)
         *
         * @param index row index; the row count gives the place below the last row
         * @return the y of the row's top
         */
        int rowDrag$rowTop(int index);

        /**
         * Returns the distance from one row to the next
         *
         * @return the row height in pixels, the gap below a row included
         */
        int rowDrag$itemHeight();
    }

    /**
     * Notes a press on a row that can be dragged
     *
     * @param row     the pressed row
     * @param x       mouse x
     * @param y       mouse y
     * @param rowLeft left edge of the row
     * @param rowTop  top of the row
     */
    public void press(T row, double x, double y, int rowLeft, int rowTop) {
        this.pressed = row;
        this.pressX = x;
        this.pressY = y;
        this.mouseX = x;
        this.mouseY = y;
        this.grabX = (int) x - rowLeft;
        this.grabY = (int) y - rowTop;
        this.dragging = false;
    }

    /**
     * Follows the pressed mouse
     *
     * @param x mouse x
     * @param y mouse y
     * @return true while a row is dragged (the list must not handle the event)
     */
    public boolean move(double x, double y) {
        if (this.pressed == null) {
            return false;
        }
        this.mouseX = x;
        this.mouseY = y;
        if (!this.dragging && Math.hypot(x - this.pressX, y - this.pressY) >= START_DISTANCE) {
            this.dragging = true;
        }
        return this.dragging;
    }

    /**
     * Ends a press
     *
     * @return the dragged row if a drag ended (drop it), or null for a normal click
     */
    @Nullable
    public T release() {
        @Nullable T row = this.dragging ? this.pressed : null;
        cancel();
        return row;
    }

    /** Forgets the press without dropping anything */
    public void cancel() {
        this.pressed = null;
        this.dragging = false;
    }

    /**
     * Tells whether a row is dragged
     *
     * @return true from the moment the pressed mouse moved far enough until it is let go
     */
    public boolean isDragging() {
        return this.dragging;
    }

    /**
     * Returns the pressed row
     *
     * @return the row, or null if nothing is pressed
     */
    @Nullable
    public T pressed() {
        return this.pressed;
    }

    /**
     * Returns the mouse height while pressed (the place the row would go)
     *
     * @return the last mouse y
     */
    public double mouseY() {
        return this.mouseY;
    }

    /**
     * Returns the mouse x while pressed
     *
     * @return the last mouse x
     */
    public double mouseX() {
        return this.mouseX;
    }

    /**
     * Returns the place under a mouse height: the index of the first row whose middle is below it, or the row count
     * (below the last row)
     *
     * @param rows  the list's rows
     * @param count how many rows the list has
     * @param y     mouse y
     * @return the place, 0 to {@code count}
     */
    public static int slot(Rows rows, int count, double y) {
        for (int index = 0; index < count; index++) {
            if (y < rows.rowDrag$rowTop(index) + (rows.rowDrag$itemHeight() - ROW_GAP) / 2.0) {
                return index;
            }
        }
        return count;
    }

    /**
     * Draws the line that shows where the row goes: in the gap above the row at a place, inside the list only
     *
     * @param graphics the graphics
     * @param rows     the list's rows
     * @param slot     the place ({@link #slot})
     * @param left     left end of the line
     * @param right    right end of the line
     * @param top      top of the list
     * @param bottom   bottom of the list
     */
    public static void drawLine(GuiGraphics graphics, Rows rows, int slot, int left, int right, int top,
                                int bottom) {
        int lineY = rows.rowDrag$rowTop(slot) - 3;
        if (lineY >= top && lineY < bottom) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, Z_LEVEL);
            graphics.fill(left, lineY, right, lineY + 2, LINE_COLOR);
            graphics.pose().popPose();
        }
    }

    /**
     * Draws the dragged row under the mouse, where it was grabbed, on a dark framed background above everything
     *
     * @param graphics the graphics
     * @param width    width of the row's content
     * @param height   height of the row's content
     * @param content  draws the row's content at (left, top)
     */
    public void drawDragged(GuiGraphics graphics, int width, int height, BiConsumer<Integer, Integer> content) {
        int left = (int) this.mouseX - this.grabX;
        int top = (int) this.mouseY - this.grabY;
        graphics.flush(); // draw the lists' texts first, so the dragged row covers them
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, Z_LEVEL);
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, BACKGROUND);
        graphics.renderOutline(left - 2, top - 2, width + 4, height + 4, FRAME);
        content.accept(left, top);
        graphics.pose().popPose();
    }
}
