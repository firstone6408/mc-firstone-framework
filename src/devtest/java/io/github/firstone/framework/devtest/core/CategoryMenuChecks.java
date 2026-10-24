package io.github.firstone.framework.devtest.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.firstone.framework.client.screen.CategoryMenuScreen;
import io.github.firstone.framework.client.screen.ConfigCategory;
import io.github.firstone.framework.client.screen.ConfigList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Checks of a feature whose settings are split into categories ({@link CategoryMenuScreen}), used by the tests of
 * those features
 *
 * <p>The screens are opened as a player opens them (the feature's screen from {@code FeatureScreenRegistry}, then a
 * category from its tile) and their buttons are pressed; the options are read back from the feature's config.</p>
 */
public final class CategoryMenuChecks {

    /** Fewest and most options a category (or a group of it) may hold, so no list is nearly empty or long (7 ± 2) */
    private static final int FEWEST = 2;
    private static final int MOST = 7;

    /** Folder of the mod's config files, inside the game's config folder */
    private static final String CONFIG_FOLDER = "firstone-framework";

    private CategoryMenuChecks() {}

    /**
     * Runs every check of a feature's category menu
     *
     * @param t          the test
     * @param featureId  the feature's id, as registered in {@code FeatureScreenRegistry}
     * @param keyPrefix  the feature's translation key prefix, ending with a dot
     * @param config     reads the feature's current config
     * @param defaults   creates a config with the default values
     * @param configFile the feature's config file, e.g. {@code "animatium.json"}
     */
    public static void check(TestContext t, String featureId, String keyPrefix, Supplier<Object> config,
                             Supplier<Object> defaults, String configFile) {
        t.check(featureId + ": settings screen open", t.openSettings(featureId)).isTrue();
        Screen opened = t.mc().screen;
        t.require(opened instanceof CategoryMenuScreen<?>, "The settings screen has no categories");
        @SuppressWarnings("unchecked")
        CategoryMenuScreen<Object> menu = (CategoryMenuScreen<Object>) opened;
        t.saveScreenshot("settings_" + featureId);
        List<String> headings = t.onClientThread(() -> headings(menu));
        t.check(featureId + ": the tiles stand under one \"Categories\" heading " + headings,
            headings.equals(List.of(Component.translatable("firstone-framework.config.categories").getString())))
            .isTrue();
        checkEveryOptionOnce(t, featureId, menu, config.get());
        checkTexts(t, featureId, keyPrefix, menu);
        for (ConfigCategory<Object> category : menu.categories()) {
            checkCategory(t, featureId, keyPrefix, menu, category, config, defaults);
            checkButtons(t, featureId, menu, category, config, defaults, configFile);
        }
        t.closeScreen();
    }

    /** Every on/off option of the config is in exactly one category, and every group of a category is a good size */
    private static void checkEveryOptionOnce(TestContext t, String featureId, CategoryMenuScreen<Object> menu,
                                             Object config) {
        Set<String> listed = new HashSet<>();
        int repeated = 0;
        for (ConfigCategory<Object> category : menu.categories()) {
            for (ConfigCategory.Group<Object> group : category.groups()) {
                String heading = group.heading() != null ? group.heading() : category.key();
                t.check(featureId + ", " + heading + ": options (between " + FEWEST + " and " + MOST + ")",
                    group.options().size()).between(FEWEST, MOST);
            }
            for (ConfigCategory.Toggle<Object> toggle : category.toggles()) {
                repeated += listed.add(toggle.key()) ? 0 : 1;
            }
        }
        List<String> missing = new ArrayList<>();
        for (Field field : config.getClass().getFields()) {
            if (field.getType() == boolean.class && !listed.remove(snakeCase(field.getName()))) {
                missing.add(field.getName());
            }
        }
        t.check(featureId + ": options listed twice", repeated).near(0, 0);
        t.check(featureId + ": options of the config in no category " + missing, missing.size()).near(0, 0);
        t.check(featureId + ": listed options the config does not have " + listed, listed.size()).near(0, 0);
    }

    /**
     * Every category has a name and a description, every group a heading, every option (on/off or slider) a label
     * and a tooltip
     */
    private static void checkTexts(TestContext t, String featureId, String keyPrefix, CategoryMenuScreen<Object> menu) {
        List<String> missing = new ArrayList<>();
        Language language = Language.getInstance();
        for (ConfigCategory<Object> category : menu.categories()) {
            List<String> keys = new ArrayList<>(List.of("category." + category.key(),
                "category." + category.key() + ".description"));
            for (ConfigCategory.Group<Object> group : category.groups()) {
                if (group.heading() != null) {
                    keys.add("category." + group.heading());
                }
            }
            for (String key : keys) {
                if (!language.has(keyPrefix + key)) {
                    missing.add(key);
                }
            }
            for (ConfigCategory.Option<Object> option : category.options()) {
                for (String key : List.of(option.key(), option.key() + ".tooltip")) {
                    if (!language.has(keyPrefix + key)) {
                        missing.add(key);
                    }
                }
            }
        }
        t.check(featureId + ": texts missing from the language file " + missing, missing.size()).near(0, 0);
    }

    /**
     * Opens one category from the menu: its title and headings, its options, a click on each, the menu's count after
     * Done, and its own Reset
     */
    private static void checkCategory(TestContext t, String featureId, String keyPrefix,
                                      CategoryMenuScreen<Object> menu, ConfigCategory<Object> category,
                                      Supplier<Object> config, Supplier<Object> defaults) {
        String name = featureId + ", " + category.key();
        Screen screen = t.onClientThread(() -> {
            Screen created = menu.categoryScreen(category);
            t.mc().setScreen(created);
            return created;
        });
        t.ticks(3);
        t.saveScreenshot("settings_" + featureId + "_" + category.key());
        String title = t.onClientThread(() -> screen.getTitle().getString());
        t.check(name + ": title is the feature's name, as on every settings screen (" + title + ")",
            title.equals(menu.getTitle().getString())).isTrue();
        List<String> headings = t.onClientThread(() -> headings(screen));
        List<String> expected = new ArrayList<>();
        for (ConfigCategory.Group<Object> group : category.groups()) {
            String heading = group.heading() != null ? group.heading() : category.key();
            expected.add(Component.translatable(keyPrefix + "category." + heading).getString());
        }
        t.check(name + ": headings " + headings + " (the category's name, or its groups')", headings.equals(expected))
            .isTrue();
        List<CycleButton<?>> toggles = t.onClientThread(() -> toggles(screen));
        t.check(name + ": on/off buttons on the screen", toggles.size()).near(category.toggles().size(), 0);

        // each button writes its option (every option is set to on first, so a grayed-out one can be pressed too)
        t.onClientThread(() -> {
            category.toggles().forEach(toggle -> toggle.setter().accept(config.get(), true));
            return null;
        });
        int written = t.onClientThread(() -> {
            t.mc().setScreen(menu.categoryScreen(category));
            List<CycleButton<?>> fresh = toggles(t.mc().screen);
            int flipped = 0;
            for (int i = 0; i < fresh.size(); i++) {
                fresh.get(i).onPress();
                flipped += category.toggles().get(i).value().test(config.get()) ? 0 : 1;
            }
            return flipped;
        });
        t.check(name + ": options turned off by their buttons", written).near(category.toggles().size(), 0);

        // Done goes back to the menu, whose tile shows the new count at once (here: none on)
        String status = t.onClientThread(() -> {
            t.mc().screen.onClose();
            return tileStatus(t.mc().screen, menu, category);
        });
        t.check(name + ": back on the menu, its tile shows the new count (" + status + ")",
            status.equals("0/" + category.toggles().size())).isTrue();

        // Reset (and Yes) puts this category back to its defaults and leaves every other option as it was
        Set<String> before = t.onClientThread(() -> {
            menu.categories().forEach(other -> other.toggles().forEach(toggle -> toggle.setter().accept(config.get(),
                !toggle.value().test(defaults.get()))));
            menu.categories().forEach(other -> sliders(other).forEach(slider -> slider.setter().accept(config.get(),
                notDefault(slider, defaults.get()))));
            return snapshot(menu, config.get());
        });
        t.onClientThread(() -> {
            t.mc().setScreen(menu.categoryScreen(category));
            press(t.mc().screen, Component.translatable("firstone-framework.config.reset"));
            return null;
        });
        String question = t.onClientThread(() -> t.mc().screen.getTitle().getString());
        String categoryName = Component.translatable(keyPrefix + "category." + category.key()).getString();
        t.check(name + ": Reset asks about the category (" + question + ")", question.contains(categoryName)).isTrue();
        t.onClientThread(() -> {
            press(t.mc().screen, CommonComponents.GUI_YES);
            return null;
        });
        t.ticks(2);
        int[] result = t.onClientThread(() -> {
            int reset = 0;
            int kept = 0;
            for (ConfigCategory<Object> other : menu.categories()) {
                for (ConfigCategory.Toggle<Object> toggle : other.toggles()) {
                    boolean now = toggle.value().test(config.get());
                    if (other == category) {
                        reset += now == toggle.value().test(defaults.get()) ? 1 : 0;
                    } else {
                        kept += now == before.contains(toggle.key()) ? 1 : 0;
                    }
                }
            }
            return new int[] {reset, kept};
        });
        int others = menu.categories().stream().filter(other -> other != category)
            .mapToInt(other -> other.toggles().size()).sum();
        t.check(name + ": Reset put its options back to the defaults", result[0]).near(category.toggles().size(), 0);
        t.check(name + ": Reset left the other categories' options", result[1]).near(others, 0);
        int allSliders = menu.categories().stream().mapToInt(other -> sliders(other).size()).sum();
        if (allSliders > 0) {
            int[] sliders = t.onClientThread(() -> {
                int reset = 0;
                int kept = 0;
                for (ConfigCategory<Object> other : menu.categories()) {
                    for (ConfigCategory.Slider<Object> slider : sliders(other)) {
                        double now = slider.value().applyAsDouble(config.get());
                        if (other == category) {
                            reset += now == slider.value().applyAsDouble(defaults.get()) ? 1 : 0;
                        } else {
                            kept += now == notDefault(slider, defaults.get()) ? 1 : 0;
                        }
                    }
                }
                return new int[] {reset, kept};
            });
            int own = sliders(category).size();
            t.check(name + ": Reset put its sliders back to the defaults", sliders[0]).near(own, 0);
            t.check(name + ": Reset left the other categories' sliders", sliders[1]).near(allSliders - own, 0);
        }
        t.onClientThread(() -> {
            menu.categories().forEach(other -> other.reset(config.get(), defaults.get()));
            t.mc().setScreen(menu);
            return null;
        });
    }

    /**
     * Presses every button of a category off and on again, as a player clicks it: each press must flip exactly the
     * config field its label names (and no other), show the new value, be saved in the config file, and leave every
     * button grayed out exactly when its condition is false; then the category goes back to its defaults
     */
    private static void checkButtons(TestContext t, String featureId, CategoryMenuScreen<Object> menu,
                                     ConfigCategory<Object> category, Supplier<Object> config,
                                     Supplier<Object> defaults, String configFile) {
        String name = featureId + ", " + category.key();
        // every option of the category on, so no button starts grayed out; GUI scale 1, so every row fits on the
        // screen and is drawn each frame (a row updates its gray state when it is drawn)
        int scale = t.onClientThread(() -> {
            int before = t.mc().options.guiScale().get();
            t.mc().options.guiScale().set(1);
            t.mc().resizeDisplay();
            category.toggles().forEach(toggle -> toggle.setter().accept(config.get(), true));
            t.mc().setScreen(menu.categoryScreen(category));
            return before;
        });
        t.ticks(2);
        List<String> wrong = new ArrayList<>(t.onClientThread(() -> scrolls(t.mc().screen)
            ? List.of("not every row fits on the screen") : List.<String>of()));
        int presses = 0;
        for (int i = 0; i < category.toggles().size(); i++) {
            for (boolean wanted : new boolean[] {false, true}) {
                int index = i;
                String key = category.toggles().get(i).key();
                List<String> found = t.onClientThread(() -> {
                    List<String> problems = new ArrayList<>();
                    Map<String, Boolean> before = booleans(config.get());
                    CycleButton<?> button = toggles(t.mc().screen).get(index);
                    button.onPress();
                    Map<String, Boolean> after = booleans(config.get());
                    List<String> changed = new ArrayList<>();
                    after.forEach((field, value) -> {
                        if (!value.equals(before.get(field))) {
                            changed.add(snakeCase(field));
                        }
                    });
                    if (!changed.equals(List.of(key)) || after.get(camelCase(key)) != wanted) {
                        problems.add(key + " -> " + wanted + " changed " + changed);
                    }
                    if (!Boolean.valueOf(wanted).equals(button.getValue())) {
                        problems.add(key + ": button shows " + button.getValue() + ", not " + wanted);
                    }
                    Boolean saved = savedValue(configFile, camelCase(key));
                    if (!Boolean.valueOf(wanted).equals(saved)) {
                        problems.add(key + ": " + configFile + " holds " + saved + ", not " + wanted);
                    }
                    return problems;
                });
                wrong.addAll(found);
                presses++;
                // a frame is drawn, which grays out the rows whose condition is false
                t.ticks(1);
                wrong.addAll(t.onClientThread(() -> grayedOut(t.mc().screen, category, config.get())));
            }
        }
        t.check(name + ": presses that flip their own option only, show it and save it (problems " + wrong + ")",
            presses - wrong.size()).near(category.toggles().size() * 2, 0);
        t.onClientThread(() -> {
            category.reset(config.get(), defaults.get());
            t.mc().options.guiScale().set(scale);
            t.mc().resizeDisplay();
            t.mc().setScreen(menu);
            return null;
        });
    }

    /** The sliders of a category */
    private static List<ConfigCategory.Slider<Object>> sliders(ConfigCategory<Object> category) {
        List<ConfigCategory.Slider<Object>> sliders = new ArrayList<>();
        for (ConfigCategory.Option<Object> option : category.options()) {
            if (option instanceof ConfigCategory.Slider<Object> slider) {
                sliders.add(slider);
            }
        }
        return sliders;
    }

    /** A value of a slider that is not its default: its largest, or its smallest when that is the default */
    private static double notDefault(ConfigCategory.Slider<Object> slider, Object defaults) {
        return slider.value().applyAsDouble(defaults) == slider.max() ? slider.min() : slider.max();
    }

    /** Whether the option list of a config screen scrolls (some rows are not drawn) */
    private static boolean scrolls(Screen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ConfigList list && list.getMaxScroll() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Buttons whose gray state does not match their condition ("enabled when") */
    private static List<String> grayedOut(Screen screen, ConfigCategory<Object> category, Object config) {
        List<String> problems = new ArrayList<>();
        List<CycleButton<?>> buttons = toggles(screen);
        for (int i = 0; i < buttons.size(); i++) {
            ConfigCategory.Toggle<Object> toggle = category.toggles().get(i);
            boolean enabled = toggle.enabledWhen() == null || toggle.enabledWhen().test(config);
            if (buttons.get(i).active != enabled) {
                problems.add(toggle.key() + (enabled ? " grayed out" : " not grayed out"));
            }
        }
        return problems;
    }

    /** Every on/off field of a config by its name */
    private static Map<String, Boolean> booleans(Object config) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (Field field : config.getClass().getFields()) {
            if (field.getType() == boolean.class) {
                try {
                    values.put(field.getName(), field.getBoolean(config));
                } catch (IllegalAccessException e) {
                    throw new TestFailure("Cannot read " + field.getName() + ": " + e.getMessage());
                }
            }
        }
        return values;
    }

    /** The value of an option in the feature's config file, as the game reads it when it starts, or null */
    @Nullable
    private static Boolean savedValue(String configFile, String field) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FOLDER).resolve(configFile);
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement value = JsonParser.parseReader(reader).getAsJsonObject().get(field);
            return value == null ? null : value.getAsBoolean();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The keys of the options that are on */
    private static Set<String> snapshot(CategoryMenuScreen<Object> menu, Object config) {
        Set<String> on = new HashSet<>();
        menu.categories().forEach(category -> category.toggles().forEach(toggle -> {
            if (toggle.value().test(config)) {
                on.add(toggle.key());
            }
        }));
        return on;
    }

    /** The section headings of a config screen, top to bottom */
    private static List<String> headings(Screen screen) {
        List<String> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ConfigList list) {
                for (ConfigList.Row row : list.children()) {
                    if (row instanceof ConfigList.SectionRow section) {
                        found.add(section.title().getString());
                    }
                }
            }
        }
        return found;
    }

    /** The status text of a category's tile on the menu (the tiles are in the order of the categories) */
    private static String tileStatus(Screen screen, CategoryMenuScreen<Object> menu, ConfigCategory<Object> category) {
        int index = menu.categories().indexOf(category);
        List<ConfigList.Tile> tiles = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ConfigList list) {
                for (ConfigList.Row row : list.children()) {
                    if (row instanceof ConfigList.TileRow tileRow) {
                        tiles.addAll(tileRow.tiles());
                    }
                }
            }
        }
        if (screen != menu || index >= tiles.size()) {
            throw new TestFailure("The menu is not shown after Done (" + screen.getClass().getSimpleName() + ")");
        }
        Component status = tiles.get(index).status();
        return status == null ? "" : status.getString();
    }

    /** The on/off buttons of a config screen, top to bottom */
    private static List<CycleButton<?>> toggles(Screen screen) {
        List<CycleButton<?>> found = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ConfigList list) {
                for (ConfigList.Row row : list.children()) {
                    for (GuiEventListener control : row.children()) {
                        // on/off buttons only (a list option is a cycling button too)
                        if (control instanceof CycleButton<?> button && button.getValue() instanceof Boolean) {
                            found.add(button);
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Presses the button of a screen that shows a text */
    private static void press(Screen screen, Component text) {
        String wanted = text.getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(wanted)) {
                button.onPress();
                return;
            }
        }
        throw new TestFailure("No button \"" + wanted + "\" on " + screen.getClass().getSimpleName());
    }

    /** {@code legacy_first_person_items} → {@code legacyFirstPersonItems} */
    private static String camelCase(String snakeCase) {
        StringBuilder name = new StringBuilder();
        boolean upper = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                name.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return name.toString();
    }

    /** {@code legacyFirstPersonItems} → {@code legacy_first_person_items} */
    private static String snakeCase(String camelCase) {
        return camelCase.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }
}
