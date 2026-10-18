package io.github.firstone.framework.client.mixin.packmanager.drag;

import io.github.firstone.framework.client.screen.RowDrag;
import io.github.firstone.framework.features.packmanager.drag.PackDrag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Passes the mouse events of the two pack list widgets to {@link PackDrag}
 *
 * <p>Only the pack lists ({@code TransferableSelectionList}) get these methods; every other list of the game is left
 * alone. A press is noted after the list handled it (the row is then focused; a press on the scrollbar does not
 * count, found as the game finds it). While a pack is dragged the list does not see the drag and release events; a
 * press that does not move stays a normal click. Also gives the rows' geometry ({@link RowDrag.Rows}).</p>
 */
@Mixin(TransferableSelectionList.class)
public abstract class PackListDragMixin extends ObjectSelectionList<TransferableSelectionList.PackEntry>
    implements RowDrag.Rows {

    /** Width of the scrollbar, as the game checks a press on it */
    @Unique
    private static final int SCROLLBAR_WIDTH = 6;

    private PackListDragMixin(Minecraft minecraft, int width, int height, int y, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean clicked = super.mouseClicked(x, y, button);
        boolean onScrollbar = x >= getScrollbarPosition() && x < getScrollbarPosition() + SCROLLBAR_WIDTH;
        PackDrag.press((TransferableSelectionList) (Object) this,
            clicked && button == 0 && !onScrollbar ? getFocused() : null, x, y);
        return clicked;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (button == 0 && PackDrag.drag((TransferableSelectionList) (Object) this, x, y)) {
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && PackDrag.release((TransferableSelectionList) (Object) this, x, y)) {
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public int rowDrag$rowTop(int index) {
        return getRowTop(index);
    }

    @Override
    public int rowDrag$itemHeight() {
        return this.itemHeight;
    }
}
