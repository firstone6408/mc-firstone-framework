package io.github.firstone.framework.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class of every config screen: title on top, a scrolling list of options, and Reset / Done at the bottom
 *
 * <p>A feature screen only lists its options in {@link #addOptions()}; layout, sizes, colors and tooltips come from
 * this class and {@link ConfigList}, so every screen looks and behaves the same:</p>
 * <pre>{@code
 * public class FallingTreeConfigScreen extends ConfigScreen {
 *     public FallingTreeConfigScreen(Screen parent) {
 *         super(parent, "firstone-framework.falling_tree.", FallingTreeFeature::saveConfig, FallingTreeFeature::resetConfig);
 *     }
 *
 *     protected void addOptions() {
 *         FallingTreeConfig config = FallingTreeFeature.getConfig();
 *         addSection("category.general");
 *         addToggle("enabled", config.enabled, value -> config.enabled = value);
 *     }
 * }
 * }</pre>
 *
 * <p>Every text comes from the language file ({@code assets/firstone-framework/lang/en_us.json}) under the screen's
 * key prefix: {@code <prefix>name} (screen title), {@code <prefix><option>} (label) and the optional
 * {@code <prefix><option>.tooltip}. Each change is written to the config right away and saved to its file.</p>
 */
public abstract class ConfigScreen extends Screen {

    /** Width of the Reset and Done buttons */
    private static final int FOOTER_BUTTON_WIDTH = 100;

    /** Color of notice rows (yellow) */
    private static final int NOTICE_COLOR = 0xFFFFFF55;

    /** Screen to return to when this screen is closed */
    protected final Screen parent;

    /** Prefix of every translation key of this screen, ending with a dot */
    private final String keyPrefix;

    /** Saves the feature config after a change, or null for a screen without a config */
    @Nullable
    private final Runnable saveConfig;

    /** Restores and saves the default config, or null to hide the Reset button */
    @Nullable
    private final Runnable resetConfig;

    /** Header / list / footer layout, rebuilt by {@link #init()} */
    private HeaderAndFooterLayout layout;

    /** The option list, rebuilt by {@link #init()} */
    private ConfigList list;

    /**
     * Creates a config screen
     *
     * @param parent      screen to return to when this screen is closed
     * @param keyPrefix   translation key prefix, ending with a dot (e.g. {@code "firstone-framework.animatium."})
     * @param saveConfig  saves the feature config (e.g. {@code AnimatiumFeature::saveConfig}), or null if nothing
     *                    is saved
     * @param resetConfig restores and saves the default config (e.g. {@code AnimatiumFeature::resetConfig}), or
     *                    null to hide the Reset button
     */
    protected ConfigScreen(Screen parent, String keyPrefix, @Nullable Runnable saveConfig, @Nullable Runnable resetConfig) {
        super(Component.translatable(keyPrefix + "name"));
        this.parent = parent;
        this.keyPrefix = keyPrefix;
        this.saveConfig = saveConfig;
        this.resetConfig = resetConfig;
    }

    /**
     * Adds the rows of this screen, in order, using the {@code add…} methods
     *
     * <p>Called every time the screen is built (opening it and after Reset), so read the config here, not in the
     * constructor.</p>
     */
    protected abstract void addOptions();

    /**
     * Returns the height of the rows of this screen
     *
     * @return {@link ConfigList#ROW_HEIGHT} by default; the main screen uses {@link ConfigList#TILE_ROW_HEIGHT}
     */
    protected int rowHeight() {
        return ConfigList.ROW_HEIGHT;
    }

    @Override
    protected void init() {
        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addTitleHeader(this.title, this.font);
        this.list = this.layout.addToContents(new ConfigList(
            this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight(), rowHeight()));
        addOptions();

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        if (this.resetConfig != null) {
            footer.addChild(Button.builder(Component.translatable("firstone-framework.config.reset"), button -> confirmReset())
                .width(FOOTER_BUTTON_WIDTH).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(FOOTER_BUTTON_WIDTH).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.list.updateSize(this.width, this.layout);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    // ─── Rows ───────────────────────────────────────────────────────────────

    /**
     * Adds a category title
     *
     * @param name key of the title, e.g. {@code "category.animation"}
     */
    protected void addSection(String name) {
        this.list.add(new ConfigList.SectionRow(text(name)));
    }

    /**
     * Adds an on/off option
     *
     * @param name   option key (label {@code <name>}, tooltip {@code <name>.tooltip})
     * @param value  current value
     * @param setter writes the new value into the config
     * @return the row, e.g. to call {@link ConfigList.OptionRow#enabledWhen}
     */
    protected ConfigList.OptionRow addToggle(String name, boolean value, Consumer<Boolean> setter) {
        CycleButton<Boolean> toggle = CycleButton.booleanBuilder(
                CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN),
                CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.RED))
            .withInitialValue(value)
            .displayOnlyValue()
            .create(0, 0, ConfigList.TOGGLE_WIDTH, ConfigList.CONTROL_HEIGHT, text(name),
                (button, newValue) -> changed(setter, newValue));
        return addOption(name, toggle);
    }

    /**
     * Adds an option that cycles through a list of values when clicked
     *
     * @param name      option key (label {@code <name>}, tooltip {@code <name>.tooltip})
     * @param values    the values to cycle through, in order
     * @param value     current value
     * @param valueText text shown on the button for a value
     * @param setter    writes the new value into the config
     * @param <T>       value type
     * @return the row
     */
    protected <T> ConfigList.OptionRow addCycle(String name, List<T> values, T value, Function<T, Component> valueText,
                                                Consumer<T> setter) {
        CycleButton<T> button = CycleButton.builder(valueText)
            .withValues(values)
            .withInitialValue(value)
            .displayOnlyValue()
            .create(0, 0, ConfigList.WIDE_CONTROL_WIDTH, ConfigList.CONTROL_HEIGHT, text(name),
                (cycle, newValue) -> changed(setter, newValue));
        return addOption(name, button);
    }

    /**
     * Adds a text field; the optional {@code <name>.hint} key is shown in gray while it is empty
     *
     * @param name      option key (label {@code <name>}, tooltip {@code <name>.tooltip}, hint {@code <name>.hint})
     * @param value     current text
     * @param maxLength maximum number of characters
     * @param setter    writes the new text into the config
     * @return the row
     */
    protected ConfigList.OptionRow addText(String name, String value, int maxLength, Consumer<String> setter) {
        EditBox box = new EditBox(this.font, 0, 0, ConfigList.WIDE_CONTROL_WIDTH, ConfigList.CONTROL_HEIGHT, text(name));
        box.setMaxLength(maxLength);
        box.setValue(value);
        Component hint = optionalText(name + ".hint");
        if (hint != null) {
            box.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        box.setResponder(newValue -> changed(setter, newValue));
        return addOption(name, box);
    }

    /**
     * Adds a row with a button that runs an action (for example opening a folder)
     *
     * @param name   option key (label {@code <name>}, button text {@code <name>.button}, tooltip {@code <name>.tooltip})
     * @param action what the button does
     * @return the row
     */
    protected ConfigList.OptionRow addButton(String name, Runnable action) {
        Button button = Button.builder(text(name + ".button"), pressed -> action.run())
            .size(ConfigList.WIDE_CONTROL_WIDTH, ConfigList.CONTROL_HEIGHT)
            .build();
        return addOption(name, button);
    }

    /**
     * Adds a yellow line of text, with an optional tooltip ({@code <name>.tooltip})
     *
     * @param name key of the text
     */
    protected void addNotice(String name) {
        this.list.add(new ConfigList.OptionRow(text(name), NOTICE_COLOR, optionalText(name + ".tooltip"), null));
    }

    /**
     * Adds the notice for server-side features: the screen changes singleplayer only, a server uses its own file
     *
     * @param configFile name of the feature's config file, e.g. {@code "combat_tweaks.json"}
     */
    protected void addServerNotice(String configFile) {
        this.list.add(new ConfigList.OptionRow(
            Component.translatable("firstone-framework.config.server_notice"), NOTICE_COLOR,
            Component.translatable("firstone-framework.config.server_notice.tooltip", configFile), null));
    }

    /**
     * Adds any row (for screens that need a row type the {@code add…} helpers do not cover)
     *
     * @param row the row to add
     * @param <R> row type
     * @return the same row
     */
    protected <R extends ConfigList.Row> R addRow(R row) {
        return this.list.add(row);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Adds a row with the label and tooltip of an option and the given control */
    private ConfigList.OptionRow addOption(String name, AbstractWidget control) {
        return this.list.add(new ConfigList.OptionRow(text(name), optionalText(name + ".tooltip"), control));
    }

    /** Writes a new value into the config and saves the file */
    private <T> void changed(Consumer<T> setter, T value) {
        setter.accept(value);
        if (this.saveConfig != null) {
            this.saveConfig.run();
        }
    }

    /** Asks before restoring the defaults; on "yes" resets the config and rebuilds the list */
    private void confirmReset() {
        this.minecraft.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed && this.resetConfig != null) {
                    this.resetConfig.run();
                    rebuildWidgets();
                }
                this.minecraft.setScreen(this);
            },
            Component.translatable("firstone-framework.config.reset.title", this.title),
            Component.translatable("firstone-framework.config.reset.message")));
    }

    /**
     * Returns the translated text of a key of this screen
     *
     * @param name key without the prefix
     * @return the translatable component
     */
    protected Component text(String name) {
        return Component.translatable(this.keyPrefix + name);
    }

    /** Returns the text of a key of this screen, or null if the language file has no such key */
    @Nullable
    private Component optionalText(String name) {
        String key = this.keyPrefix + name;
        return Language.getInstance().has(key) ? Component.translatable(key) : null;
    }
}
