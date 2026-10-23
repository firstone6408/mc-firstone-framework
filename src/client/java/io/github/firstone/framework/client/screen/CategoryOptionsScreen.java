package io.github.firstone.framework.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Settings screen of one category of a feature ({@link ConfigCategory}), opened from its {@link CategoryMenuScreen}
 *
 * <p>Like every settings screen, the title is the feature's name and the options stand under headings: the category's
 * name, or one heading per group. The options use the feature's own language keys, so their labels and tooltips are
 * the same as anywhere else. Reset restores only this category's options. Done (or Escape) goes back to the menu,
 * which then shows the new "on" count.</p>
 *
 * @param <C> the feature's config class
 */
public class CategoryOptionsScreen<C> extends ConfigScreen {

    /** The category shown */
    private final ConfigCategory<C> category;

    /** Reads the feature's current config */
    private final Supplier<C> config;

    /**
     * Creates the screen of one category
     *
     * @param parent           the category menu, to return to
     * @param featureTitle     the feature's name, for the title
     * @param keyPrefix        the feature's translation key prefix, ending with a dot
     * @param category         the category to show
     * @param config           reads the feature's current config
     * @param defaults         creates a config with the default values
     * @param save             saves the feature's config
     */
    public CategoryOptionsScreen(Screen parent, Component featureTitle, String keyPrefix, ConfigCategory<C> category,
                                 Supplier<C> config, Supplier<C> defaults, Runnable save) {
        super(parent, featureTitle, keyPrefix, save, () -> {
            category.reset(config.get(), defaults.get());
            save.run();
        });
        this.category = category;
        this.config = config;
    }

    /**
     * Returns the category shown
     *
     * @return the category
     */
    public ConfigCategory<C> category() {
        return this.category;
    }

    @Override
    protected Component resetSubject() {
        return text("category." + this.category.key());
    }

    @Override
    protected void addOptions() {
        C current = this.config.get();
        for (ConfigCategory.Group<C> group : this.category.groups()) {
            String heading = group.heading();
            addSection("category." + (heading != null ? heading : this.category.key()));
            for (ConfigCategory.Option<C> option : group.options()) {
                ConfigList.OptionRow row = switch (option) {
                    case ConfigCategory.Toggle<C> toggle -> addToggle(toggle.key(), toggle.value().test(current),
                        value -> toggle.setter().accept(this.config.get(), value));
                    case ConfigCategory.Slider<C> slider -> addSlider(slider.key(), slider.min(), slider.max(),
                        slider.step(), slider.value().applyAsDouble(current), slider.valueText(),
                        value -> slider.setter().accept(this.config.get(), value));
                };
                Predicate<C> enabledWhen = option.enabledWhen();
                if (enabledWhen != null) {
                    row.enabledWhen(() -> enabledWhen.test(this.config.get()));
                }
            }
        }
    }
}
