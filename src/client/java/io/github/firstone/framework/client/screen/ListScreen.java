package io.github.firstone.framework.client.screen;

import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the mod's screens that hold one list: the title on top, the list in the middle, a row of buttons at the
 * bottom ({@link ConfigScreen}, the presets, the changed pack files)
 *
 * <p>A screen makes its list ({@link #createList}), fills it ({@link #fillList}) and adds its buttons
 * ({@link #addFooterButtons}); the layout, sizes and resizing come from here, so all such screens look alike.</p>
 *
 * @param <L> the list type
 */
public abstract class ListScreen<L extends AbstractSelectionList<?>> extends Screen {

    /** Width of the buttons of the bottom row */
    protected static final int FOOTER_BUTTON_WIDTH = 100;

    /** Space between the buttons of the bottom row */
    private static final int FOOTER_SPACING = 8;

    /** Header / list / footer layout, rebuilt by {@link #init()} */
    private HeaderAndFooterLayout layout;

    /** The list, rebuilt by {@link #init()}; null before the screen is built */
    @Nullable
    private L list;

    /**
     * Creates the screen
     *
     * @param title title shown on top
     */
    protected ListScreen(Component title) {
        super(title);
    }

    /**
     * Makes the list
     *
     * @param width  list width (the screen width)
     * @param height list height (between header and footer)
     * @param top    top of the list
     * @return the list
     */
    protected abstract L createList(int width, int height, int top);

    /**
     * Fills the list; called every time the screen is built, after {@link #createList}
     */
    protected abstract void fillList();

    /**
     * Adds the buttons of the bottom row, normally {@link #FOOTER_BUTTON_WIDTH} wide
     *
     * @param footer the bottom row
     */
    protected abstract void addFooterButtons(LinearLayout footer);

    @Override
    protected void init() {
        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addTitleHeader(this.title, this.font);
        L created = createList(this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight());
        this.list = this.layout.addToContents(created);
        fillList();
        addFooterButtons(this.layout.addToFooter(LinearLayout.horizontal().spacing(FOOTER_SPACING)));
        this.layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        list().updateSize(this.width, this.layout);
    }

    /**
     * Returns the list
     *
     * @return the list built by the last {@link #init()}
     * @throws IllegalStateException before the screen was built
     */
    protected @NotNull L list() {
        @Nullable L current = this.list;
        if (current == null) {
            throw new IllegalStateException("The screen is not built yet");
        }
        return current;
    }

    /**
     * Tells whether the screen was built (its list exists), e.g. in {@code added()}, which runs before the first
     * {@link #init()}
     *
     * @return true once the screen was built
     */
    protected boolean isBuilt() {
        return this.list != null;
    }

    /**
     * Makes a button for the bottom row
     *
     * @param text   its text
     * @param width  its width
     * @param action what it does
     * @return the button
     */
    protected static Button footerButton(Component text, int width, Runnable action) {
        return Button.builder(text, button -> action.run()).width(width).build();
    }
}
