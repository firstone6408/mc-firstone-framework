package io.github.firstone.framework.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetTooltipHolder;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Scrolling list used by every config screen, and the single place that defines how config rows look
 *
 * <p>All sizes, colors and the tooltip delay live here, so every screen built on {@link ConfigScreen} looks the
 * same. Rows are added through {@link ConfigScreen} ({@code addSection}, {@code addToggle}…), not directly.</p>
 *
 * <p>Row types:</p>
 * <ul>
 *   <li>{@link SectionRow} — gold category title, an optional gray note, then a thin line</li>
 *   <li>{@link OptionRow} — label on the left, an optional control (toggle, cycle button, text box, button) on the
 *       right, and a tooltip shown after hovering the row for {@link #TOOLTIP_DELAY}</li>
 *   <li>{@link TileRow} — up to two tiles (item icon + name) side by side (main screen)</li>
 *   <li>{@link ButtonRow} — one action button in the middle of the row, without a label (open a folder, reload…)</li>
 * </ul>
 *
 * <p>The colors and {@link #fit} are public, so lists of other screens can draw their texts the same way.</p>
 */
public class ConfigList extends ContainerObjectSelectionList<ConfigList.Row> {

    /** Height of a normal row, including the 4 px gap to the next row */
    public static final int ROW_HEIGHT = 20;

    /** Height of a {@link TileRow}, including the 4 px gap to the next row */
    public static final int TILE_ROW_HEIGHT = 28;

    /** Space between a tile's border and its icon */
    private static final int TILE_PADDING = 4;

    /** Horizontal gap between the two tiles of a {@link TileRow} */
    private static final int TILE_GAP = 6;

    /** Widest a row may be, so the list stays compact on large screens */
    private static final int MAX_ROW_WIDTH = 300;

    /** Space kept free on each side of a row on small screens (room for the scrollbar) */
    private static final int SIDE_MARGIN = 20;

    /** Width of an on/off toggle */
    public static final int TOGGLE_WIDTH = 44;

    /** Width of a wider control (cycle button, text box, action button) */
    public static final int WIDE_CONTROL_WIDTH = 130;

    /** Height of a control inside a row */
    public static final int CONTROL_HEIGHT = 16;

    /** How long the mouse must rest on a row before its tooltip appears */
    public static final Duration TOOLTIP_DELAY = Duration.ofMillis(600);

    /** Label color */
    public static final int TEXT_COLOR = 0xFFFFFFFF;

    /** Label color of a disabled row, and the color of descriptions and hints */
    public static final int MUTED_COLOR = 0xFFA0A0A0;

    /** Color of the second line of a list entry, as the game's pack lists draw descriptions */
    public static final int SUBTLE_COLOR = 0xFF808080;

    /** Widest action button of a {@link ButtonRow} */
    private static final int MAX_ACTION_WIDTH = 200;

    /** Color of section titles (gold) */
    private static final int SECTION_COLOR = 0xFFFFAA00;

    /** Color of the line after a section title */
    private static final int SECTION_LINE_COLOR = 0x40FFFFFF;

    /** Opacity of the control of a disabled row */
    private static final float DISABLED_ALPHA = 0.5F;

    /** Background of the row under the mouse, so the label and its control are easy to match */
    private static final int HOVER_COLOR = 0x18FFFFFF;

    /** Row width set by {@link #setFixedRowWidth}, or 0 to follow the list's width */
    private int fixedRowWidth;

    /**
     * Creates an empty list
     *
     * @param minecraft the game instance
     * @param width     list width (normally the screen width)
     * @param height    list height (the space between header and footer)
     * @param y         top of the list
     * @param rowHeight height of every row: {@link #ROW_HEIGHT} or {@link #TILE_ROW_HEIGHT}
     */
    public ConfigList(Minecraft minecraft, int width, int height, int y, int rowHeight) {
        super(minecraft, width, height, y, rowHeight);
        this.centerListVertically = false;
    }

    /**
     * Gives every row a fixed width instead of one that follows the list's width (for a list in a column of a screen,
     * e.g. the preset form of Pack Manager)
     *
     * @param width row width in pixels, or 0 to follow the list's width again
     */
    public void setFixedRowWidth(int width) {
        this.fixedRowWidth = width;
    }

    /**
     * Returns the left edge of the rows; with a fixed row width the rows sit 1 pixel left of center, so their contents
     * (labels 2 pixels from the left, controls 4 pixels from the right) are as far from both edges of the list
     *
     * @return x of the rows' left edge
     */
    @Override
    public int getRowLeft() {
        return this.fixedRowWidth > 0 ? getX() + (this.width - this.fixedRowWidth) / 2 + 1 : super.getRowLeft();
    }

    /**
     * Adds a row at the end of the list
     *
     * @param row the row to add
     * @param <R> row type
     * @return the same row, for chaining
     */
    public <R extends Row> R add(R row) {
        this.addEntry(row);
        return row;
    }

    /**
     * Returns the row width: the screen width minus the margins, but never wider than {@value #MAX_ROW_WIDTH} (or the
     * width given to {@link #setFixedRowWidth})
     *
     * @return width of every row in pixels
     */
    @Override
    public int getRowWidth() {
        return this.fixedRowWidth > 0 ? this.fixedRowWidth : Math.min(MAX_ROW_WIDTH, this.width - 2 * SIDE_MARGIN);
    }

    /**
     * Shortens a text with "..." so it fits a width; every part keeps its own style (colors of appended texts)
     *
     * @param font     font used to draw the text
     * @param text     text to fit
     * @param maxWidth available width in pixels
     * @return the text itself if it fits, otherwise the cut text followed by "..."
     */
    public static FormattedCharSequence fit(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(CommonComponents.ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS));
    }

    /**
     * Base class of every row
     *
     * <p>{@code render} arguments follow the vanilla order: row index, top, left, width, height, mouse x, mouse y,
     * hovered, partial tick. {@code left}/{@code width} already include the list's 2 px inner padding.</p>
     */
    public abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
    }

    /** Gold category title, an optional gray note after it, then a thin line, e.g. "Animation ──────" */
    public static class SectionRow extends Row {

        private final Component title;
        @Nullable
        private final Component note;
        private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();

        /**
         * Creates a section title row
         *
         * @param title   the category name
         * @param note    short gray text after the title, or {@code null}
         * @param tooltip text shown after hovering the row, or {@code null}
         */
        public SectionRow(Component title, @Nullable Component note, @Nullable Component tooltip) {
            this.title = title.copy().withStyle(ChatFormatting.BOLD);
            this.note = note;
            this.tooltip.setDelay(TOOLTIP_DELAY);
            if (tooltip != null) {
                this.tooltip.set(Tooltip.create(tooltip));
            }
        }

        /**
         * Creates a section title row without note or tooltip
         *
         * @param title the category name
         */
        public SectionRow(Component title) {
            this(title, null, null);
        }

        /**
         * Returns the title of this section
         *
         * @return the category name, as shown (bold)
         */
        public Component title() {
            return this.title;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int right = left + width - 2;
            int textY = top + height - font.lineHeight;
            graphics.drawString(font, fit(font, this.title, width - 4), left, textY, SECTION_COLOR);
            int x = left + font.width(this.title) + 6;
            if (this.note != null && x < right) {
                graphics.drawString(font, fit(font, this.note, right - x), x, textY, MUTED_COLOR);
                x += font.width(this.note) + 6;
            }
            int lineY = textY + font.lineHeight / 2 - 1;
            if (x < right) {
                graphics.fill(x, lineY, right, lineY + 1, SECTION_LINE_COLOR);
            }
            this.tooltip.refreshTooltipForNextRenderPass(hovered, false, new ScreenRectangle(left, top, width, height));
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /**
     * A labeled option: label on the left, an optional control on the right, delayed tooltip on the whole row
     *
     * <p>Without a control the row is a plain text line (used for notices). Clicking anywhere on a toggle or cycle
     * row presses its button. The row can be disabled with {@link #enabledWhen}: its control becomes inactive and
     * faded, and its label gray.</p>
     */
    public static class OptionRow extends Row {

        private final Component label;
        private final int labelColor;
        @Nullable
        private final AbstractWidget control;
        private final WidgetTooltipHolder tooltip = new WidgetTooltipHolder();
        private BooleanSupplier enabled = () -> true;

        /**
         * Creates an option row
         *
         * @param label      text on the left
         * @param labelColor color of the label (ARGB), e.g. white for options or yellow for notices
         * @param tooltip    text shown after hovering the row, or {@code null} for none
         * @param control    widget on the right (its x/y are set by the row), or {@code null} for a text-only row
         */
        public OptionRow(Component label, int labelColor, @Nullable Component tooltip,
                         @Nullable AbstractWidget control) {
            this.label = label;
            this.labelColor = labelColor;
            this.control = control;
            this.tooltip.setDelay(TOOLTIP_DELAY);
            if (tooltip != null) {
                this.tooltip.set(Tooltip.create(tooltip));
            }
        }

        /**
         * Creates a white option row
         *
         * @param label   text on the left
         * @param tooltip text shown after hovering the row, or {@code null} for none
         * @param control widget on the right, or {@code null} for a text-only row
         */
        public OptionRow(Component label, @Nullable Component tooltip, @Nullable AbstractWidget control) {
            this(label, TEXT_COLOR, tooltip, control);
        }

        /**
         * Makes the row usable only while a condition is true (checked every frame)
         *
         * <p>Example: "Sweeping Edge Required" only matters while "Disable Sweeping Attack" is on.</p>
         *
         * @param condition returns true while the option has an effect
         * @return this row, for chaining
         */
        public OptionRow enabledWhen(BooleanSupplier condition) {
            this.enabled = condition;
            return this;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            boolean active = this.enabled.getAsBoolean();
            int right = left + width - 4;

            if (hovered) {
                graphics.fill(left - 2, top - 2, left - 2 + width, top + height + 2, HOVER_COLOR);
            }

            int labelRight = right;
            if (this.control != null) {
                this.control.active = active;
                this.control.setAlpha(active ? 1.0F : DISABLED_ALPHA);
                this.control.setPosition(right - this.control.getWidth(),
                    top + (height - this.control.getHeight()) / 2);
                this.control.render(graphics, mouseX, mouseY, partialTick);
                labelRight = this.control.getX() - 6;
            }

            int labelY = top + (height - font.lineHeight) / 2 + 1;
            graphics.drawString(font, fit(font, this.label, labelRight - left - 2), left + 2, labelY,
                active ? this.labelColor : MUTED_COLOR);

            boolean focused = this.control != null && this.control.isFocused();
            this.tooltip.refreshTooltipForNextRenderPass(hovered, focused,
                new ScreenRectangle(left, top, width, height));
        }

        /**
         * Clicking anywhere on the row of a toggle or cycle option presses its button, so the small button does not
         * have to be hit exactly
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (button == 0 && this.control instanceof CycleButton<?> cycle && cycle.active) {
                cycle.playDownSound(Minecraft.getInstance().getSoundManager());
                cycle.onPress();
                setFocused(cycle);
                return true;
            }
            return false;
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.control == null ? List.of() : List.of(this.control);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.control == null ? List.of() : List.of(this.control);
        }
    }

    /**
     * One clickable tile: an item icon and a name, an optional status on the right, with a delayed tooltip
     *
     * @param icon    item drawn on the left of the tile
     * @param name    text next to the icon
     * @param tooltip text shown after hovering the tile, or {@code null}
     * @param onPress action when the tile is clicked
     * @param status  short gray text on the right (for example how many options are on), or {@code null}
     */
    public record Tile(ItemStack icon, Component name, @Nullable Component tooltip, Button.OnPress onPress,
                       @Nullable Component status) {

        /**
         * Creates a tile without a status
         *
         * @param icon    item drawn on the left of the tile
         * @param name    text next to the icon
         * @param tooltip text shown after hovering the tile, or {@code null}
         * @param onPress action when the tile is clicked
         */
        public Tile(ItemStack icon, Component name, @Nullable Component tooltip, Button.OnPress onPress) {
            this(icon, name, tooltip, onPress, null);
        }
    }

    /** Up to two {@link Tile}s side by side, each half of the row wide (used for the features on the main screen) */
    public static class TileRow extends Row {

        private final List<Tile> tiles;
        private final List<Button> buttons;

        /**
         * Creates a tile row
         *
         * @param tiles one or two tiles, drawn left to right
         */
        public TileRow(List<Tile> tiles) {
            this.tiles = List.copyOf(tiles);
            this.buttons = this.tiles.stream().map(TileRow::createButton).toList();
        }

        /**
         * Returns the tiles of this row
         *
         * @return one or two tiles, left to right
         */
        public List<Tile> tiles() {
            return this.tiles;
        }

        /** Creates the button behind a tile; its text is drawn by the row, next to the icon */
        private static Button createButton(Tile tile) {
            Button button = Button.builder(CommonComponents.EMPTY, tile.onPress())
                .createNarration(message -> tile.name().copy())
                .build();
            if (tile.tooltip() != null) {
                button.setTooltip(Tooltip.create(tile.tooltip()));
                button.setTooltipDelay(TOOLTIP_DELAY);
            }
            return button;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int tileWidth = (width - 4 - TILE_GAP) / 2;
            for (int i = 0; i < this.buttons.size(); i++) {
                Button button = this.buttons.get(i);
                Tile tile = this.tiles.get(i);
                int x = left + i * (tileWidth + TILE_GAP);
                button.setRectangle(tileWidth, height, x, top);
                button.render(graphics, mouseX, mouseY, partialTick);
                graphics.renderItem(tile.icon(), x + TILE_PADDING, top + (height - 16) / 2);
                int textX = x + TILE_PADDING + 16 + 5;
                int textY = top + (height - font.lineHeight) / 2 + 1;
                int textEnd = x + tileWidth - TILE_PADDING;
                if (tile.status() != null) {
                    int statusX = textEnd - font.width(tile.status());
                    graphics.drawString(font, tile.status(), statusX, textY, MUTED_COLOR);
                    textEnd = statusX - TILE_GAP;
                }
                graphics.drawString(font, fit(font, tile.name(), textEnd - textX), textX, textY, TEXT_COLOR);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.buttons;
        }
    }

    /**
     * One action button in the middle of a row, without a label: its text says what it does (e.g. "Open Icons
     * Folder"); tooltip after {@link #TOOLTIP_DELAY}
     *
     * <p>The button is as wide as the row's content (the space labels and controls use), at most
     * {@value #MAX_ACTION_WIDTH} pixels, and centered in it. It can be made usable only while a condition holds.</p>
     */
    public static class ButtonRow extends Row {

        private final Button button;
        private BooleanSupplier enabled = () -> true;

        /**
         * Creates a button row
         *
         * @param text    text of the button
         * @param tooltip text shown after hovering the button, or {@code null} for none
         * @param action  what the button does
         */
        public ButtonRow(Component text, @Nullable Component tooltip, Runnable action) {
            this.button = Button.builder(text, pressed -> action.run()).size(MAX_ACTION_WIDTH, CONTROL_HEIGHT).build();
            if (tooltip != null) {
                this.button.setTooltip(Tooltip.create(tooltip));
                this.button.setTooltipDelay(TOOLTIP_DELAY);
            }
        }

        /**
         * Makes the button usable only while a condition is true (checked when the row is drawn)
         *
         * @param condition returns true while the button can be used
         * @return this row, for chaining
         */
        public ButtonRow enabledWhen(BooleanSupplier condition) {
            this.enabled = condition;
            return this;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            // the content of a row spans left + 2 to left + width - 4 (labels and controls of OptionRow)
            int contentWidth = width - 6;
            this.button.setWidth(Math.min(width - 8, MAX_ACTION_WIDTH));
            this.button.active = this.enabled.getAsBoolean();
            this.button.setPosition(left + 2 + (contentWidth - this.button.getWidth()) / 2,
                top + (height - this.button.getHeight()) / 2);
            this.button.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }
}
