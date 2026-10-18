package io.github.firstone.framework.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A list of entries under a heading drawn like the headings of the settings screens ({@link ConfigList.SectionRow}),
 * with an optional gray explanation under it and a gray text while the list is empty
 *
 * <p>For screens that list things (not options) but should look like the rest of the mod: the presets, the changed
 * pack files, a preset's packs. Rows have a fixed width. The rows' geometry is open for dragging ({@link RowDrag}).</p>
 *
 * @param <E> entry type
 */
public class HeadedList<E extends ObjectSelectionList.Entry<E>> extends ObjectSelectionList<E>
    implements RowDrag.Rows {

    /** Distance of the empty text below the heading, unless a list says otherwise ({@link #emptyTextTop}) */
    private static final int EMPTY_TOP = 12;

    private final ConfigList.SectionRow heading;
    private final List<FormattedCharSequence> explanation;
    @Nullable
    private final Component emptyText;
    private final int rowWidth;

    /** Height of the heading and the explanation */
    private final int wantedHeaderHeight;

    /**
     * Creates the list
     *
     * @param minecraft   the game
     * @param width       list width
     * @param height      list height
     * @param top         top of the list
     * @param itemHeight  height of a row, the 4-pixel gap below it included
     * @param rowWidth    width of every row
     * @param heading     the heading
     * @param explanation gray text under the heading, or null for none
     * @param emptyText   gray text shown while the list has no rows, or null for none
     */
    public HeadedList(Minecraft minecraft, int width, int height, int top, int itemHeight, int rowWidth,
                      Component heading, @Nullable Component explanation, @Nullable Component emptyText) {
        super(minecraft, width, height, top, itemHeight);
        this.heading = new ConfigList.SectionRow(heading);
        this.explanation = explanation == null ? List.of() : minecraft.font.split(explanation, rowWidth);
        this.emptyText = emptyText;
        this.rowWidth = rowWidth;
        this.centerListVertically = false;
        this.wantedHeaderHeight = ConfigList.ROW_HEIGHT + (this.explanation.isEmpty() ? 0
            : this.explanation.size() * minecraft.font.lineHeight + 4);
    }

    /**
     * Sizes and places the list; also turns the header on (here rather than in the constructor, which must not hand
     * the half-built list to the game's code), before the list is drawn or clicked for the first time
     */
    @Override
    public void updateSizeAndPosition(int width, int height, int top) {
        setRenderHeader(true, this.wantedHeaderHeight);
        super.updateSizeAndPosition(width, height, top);
    }

    @Override
    public int getRowWidth() {
        return this.rowWidth;
    }

    @Override
    protected void renderHeader(GuiGraphics graphics, int x, int y) {
        this.heading.render(graphics, 0, y, x, getRowWidth(), ConfigList.ROW_HEIGHT - 4, 0, 0, false, 0.0F);
        Font font = this.minecraft.font;
        for (int index = 0; index < this.explanation.size(); index++) {
            graphics.drawString(font, this.explanation.get(index), x, y + ConfigList.ROW_HEIGHT
                + index * font.lineHeight, ConfigList.MUTED_COLOR);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (this.emptyText != null && children().isEmpty()) {
            Font font = this.minecraft.font;
            List<FormattedCharSequence> lines = font.split(this.emptyText, this.rowWidth);
            for (int index = 0; index < lines.size(); index++) {
                graphics.drawCenteredString(font, lines.get(index), getX() + this.width / 2,
                    getY() + this.headerHeight + emptyTextTop() + index * font.lineHeight, ConfigList.MUTED_COLOR);
            }
        }
    }

    /**
     * Returns the distance of the empty text below the heading
     *
     * @return pixels
     */
    protected int emptyTextTop() {
        return EMPTY_TOP;
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
