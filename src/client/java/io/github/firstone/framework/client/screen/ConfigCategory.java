package io.github.firstone.framework.client.screen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * One category of a feature's options, shown as a tile on the feature's {@link CategoryMenuScreen}
 *
 * <p>Big features split their options into categories of a few options each, grouped by what the player sees in the
 * game, so no screen gets long. A category lists its options once; the menu, the category's screen
 * ({@link CategoryOptionsScreen}), the "on" count of its tile and its Reset all come from that list. Its screen shows
 * the options under a heading with the category's name, or, for a category made of groups ({@link #grouped}), under
 * one heading per group. An option is an on/off button ({@link Toggle}) or a slider for a number ({@link Slider});
 * the "on" count only counts the on/off buttons:</p>
 * <pre>{@code
 * ConfigCategory.of("boats", Items.OAK_BOAT, List.of(
 *     ConfigCategory.toggle("legacy_boat_breaking", c -> c.legacyBoatBreaking, (c, v) -> c.legacyBoatBreaking = v),
 *     ConfigCategory.toggle("boats_safe_on_ice", c -> c.boatsSafeOnIce, (c, v) -> c.boatsSafeOnIce = v,
 *         c -> c.legacyBoatBreaking)));
 * }</pre>
 *
 * <p>Texts come from the feature's language keys: {@code <prefix>category.<key>} (name) and
 * {@code <prefix>category.<key>.description} (tile tooltip), {@code <prefix>category.<group>} for group headings;
 * the options keep their own keys.</p>
 *
 * @param key    key of the category, e.g. {@code "items"}
 * @param icon   item drawn on its tile
 * @param groups its options in groups, in the order they are shown
 * @param <C>    the feature's config class
 */
public record ConfigCategory<C>(String key, Item icon, List<Group<C>> groups) {

    /**
     * Options of a category shown under one heading
     *
     * @param heading key of the heading ({@code <prefix>category.<heading>}), or null for the category's own name
     * @param options the options, in the order they are shown
     * @param <C>     the feature's config class
     */
    public record Group<C>(@Nullable String heading, List<Option<C>> options) {

        /**
         * Returns the on/off options of this group
         *
         * @return the on/off options, in the order they are shown
         */
        public List<Toggle<C>> toggles() {
            List<Toggle<C>> toggles = new ArrayList<>();
            for (Option<C> option : this.options) {
                if (option instanceof Toggle<C> toggle) {
                    toggles.add(toggle);
                }
            }
            return toggles;
        }
    }

    /**
     * One option of a category: an on/off button ({@link Toggle}) or a slider ({@link Slider})
     *
     * <p>Every kind of option has a key for its texts, may depend on another option, and can be put back to its
     * default; the category's screen adds the control that fits the kind.</p>
     *
     * @param <C> the feature's config class
     */
    public sealed interface Option<C> permits Toggle, Slider {

        /**
         * Returns the option's key
         *
         * @return the key (label {@code <prefix><key>}, tooltip {@code <prefix><key>.tooltip})
         */
        String key();

        /**
         * Returns when the option can be changed
         *
         * @return the condition (the option only matters while another option is on), or null if always
         */
        @Nullable
        Predicate<C> enabledWhen();

        /**
         * Puts the option back to its default
         *
         * @param config   the config to change
         * @param defaults a config with the default values
         */
        void reset(C config, C defaults);
    }

    /**
     * One on/off option of a category
     *
     * @param key         option key (label {@code <prefix><key>}, tooltip {@code <prefix><key>.tooltip})
     * @param value       reads the option from a config
     * @param setter      writes the option into a config
     * @param enabledWhen when the option can be changed (it only matters while another option is on), or null if
     *                    always
     * @param <C>         the feature's config class
     */
    public record Toggle<C>(String key, Predicate<C> value, BiConsumer<C, Boolean> setter,
                            @Nullable Predicate<C> enabledWhen) implements Option<C> {

        @Override
        public void reset(C config, C defaults) {
            this.setter.accept(config, this.value.test(defaults));
        }
    }

    /**
     * One number option of a category, shown as a slider
     *
     * @param key         option key (label {@code <prefix><key>}, tooltip {@code <prefix><key>.tooltip})
     * @param min         smallest value
     * @param max         largest value
     * @param step        distance between two values (1 for whole numbers)
     * @param value       reads the option from a config
     * @param setter      writes the option into a config
     * @param valueText   text shown on the slider for a value, e.g. {@code v -> Component.literal((int) v + " s")}
     * @param enabledWhen when the option can be changed, or null if always
     * @param <C>         the feature's config class
     */
    public record Slider<C>(String key, double min, double max, double step, ToDoubleFunction<C> value,
                            ObjDoubleConsumer<C> setter, DoubleFunction<Component> valueText,
                            @Nullable Predicate<C> enabledWhen) implements Option<C> {

        @Override
        public void reset(C config, C defaults) {
            this.setter.accept(config, this.value.applyAsDouble(defaults));
        }
    }

    /**
     * Creates a category whose options are shown under its own name
     *
     * @param key     key of the category
     * @param icon    item drawn on its tile
     * @param options its options, in the order they are shown
     * @param <C>     the feature's config class
     * @return the category
     */
    public static <C> ConfigCategory<C> of(String key, Item icon, List<? extends Option<C>> options) {
        return new ConfigCategory<>(key, icon, List.of(new Group<>(null, List.copyOf(options))));
    }

    /**
     * Creates a category whose options are shown in groups, each under its own heading
     *
     * @param key    key of the category
     * @param icon   item drawn on its tile
     * @param groups its groups, in the order they are shown
     * @param <C>    the feature's config class
     * @return the category
     */
    public static <C> ConfigCategory<C> grouped(String key, Item icon, List<Group<C>> groups) {
        return new ConfigCategory<>(key, icon, List.copyOf(groups));
    }

    /**
     * Creates a group of options under a heading
     *
     * @param heading key of the heading ({@code <prefix>category.<heading>})
     * @param options the options, in the order they are shown
     * @param <C>     the feature's config class
     * @return the group
     */
    public static <C> Group<C> group(String heading, List<? extends Option<C>> options) {
        return new Group<>(heading, List.copyOf(options));
    }

    /**
     * Returns every option of this category, group after group
     *
     * @return the options of every kind, in the order they are shown
     */
    public List<Option<C>> options() {
        List<Option<C>> all = new ArrayList<>();
        for (Group<C> group : this.groups) {
            all.addAll(group.options());
        }
        return all;
    }

    /**
     * Returns every on/off option of this category, group after group
     *
     * @return the on/off options, in the order they are shown
     */
    public List<Toggle<C>> toggles() {
        List<Toggle<C>> all = new ArrayList<>();
        for (Group<C> group : this.groups) {
            all.addAll(group.toggles());
        }
        return all;
    }

    /**
     * Creates an option that can always be changed
     *
     * @param key    option key
     * @param value  reads the option from a config
     * @param setter writes the option into a config
     * @param <C>    the feature's config class
     * @return the option
     */
    public static <C> Toggle<C> toggle(String key, Predicate<C> value, BiConsumer<C, Boolean> setter) {
        return new Toggle<>(key, value, setter, null);
    }

    /**
     * Creates an option that can only be changed while a condition holds (it is grayed out otherwise)
     *
     * @param key         option key
     * @param value       reads the option from a config
     * @param setter      writes the option into a config
     * @param enabledWhen when the option can be changed
     * @param <C>         the feature's config class
     * @return the option
     */
    public static <C> Toggle<C> toggle(String key, Predicate<C> value, BiConsumer<C, Boolean> setter,
                                       Predicate<C> enabledWhen) {
        return new Toggle<>(key, value, setter, enabledWhen);
    }

    /**
     * Creates a number option shown as a slider, that can always be changed
     *
     * @param key       option key
     * @param min       smallest value
     * @param max       largest value
     * @param step      distance between two values (1 for whole numbers)
     * @param value     reads the option from a config
     * @param setter    writes the option into a config
     * @param valueText text shown on the slider for a value
     * @param <C>       the feature's config class
     * @return the option
     */
    public static <C> Slider<C> slider(String key, double min, double max, double step, ToDoubleFunction<C> value,
                                       ObjDoubleConsumer<C> setter, DoubleFunction<Component> valueText) {
        return new Slider<>(key, min, max, step, value, setter, valueText, null);
    }

    /**
     * Creates a number option shown as a slider, that can only be changed while a condition holds (it is grayed
     * out otherwise)
     *
     * @param key         option key
     * @param min         smallest value
     * @param max         largest value
     * @param step        distance between two values (1 for whole numbers)
     * @param value       reads the option from a config
     * @param setter      writes the option into a config
     * @param valueText   text shown on the slider for a value
     * @param enabledWhen when the option can be changed
     * @param <C>         the feature's config class
     * @return the option
     */
    public static <C> Slider<C> slider(String key, double min, double max, double step, ToDoubleFunction<C> value,
                                       ObjDoubleConsumer<C> setter, DoubleFunction<Component> valueText,
                                       Predicate<C> enabledWhen) {
        return new Slider<>(key, min, max, step, value, setter, valueText, enabledWhen);
    }

    /**
     * Counts the options of this category that are on
     *
     * @param config the config to read
     * @return how many are on
     */
    public int countOn(C config) {
        int on = 0;
        for (Toggle<C> toggle : toggles()) {
            if (toggle.value().test(config)) {
                on++;
            }
        }
        return on;
    }

    /**
     * Puts the options of this category back to their defaults, leaving every other option as it is
     *
     * @param config   the config to change
     * @param defaults a config with the default values
     */
    public void reset(C config, C defaults) {
        for (Option<C> option : options()) {
            option.reset(config, defaults);
        }
    }
}
