package io.github.firstone.framework.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Settings screen of a big feature: one tile per category ({@link ConfigCategory}), two per row
 *
 * <p>Each tile shows the category's icon and name and how many of its options are on (for example {@code 3/4}); on
 * hover it tells what the category holds and the same count in words. Clicking it opens the category's screen
 * ({@link CategoryOptionsScreen}). The tiles stand under a "Categories" heading, as the
 * categories of Cosmetics do. Reset here restores every option of the feature; each category screen resets only its
 * own options. Server-side features show the server notice on top of this screen. Coming back from a category, the
 * tiles are built again, so their counts show the changes.</p>
 *
 * @param <C> the feature's config class
 */
public class CategoryMenuScreen<C> extends ConfigScreen {

    /** Prefix of the feature's translation keys */
    private final String keyPrefix;

    /** Reads the feature's current config (it may be replaced when it is reset or loaded) */
    private final Supplier<C> config;

    /** Creates a config with the default values */
    private final Supplier<C> defaults;

    /** Saves the feature's config */
    private final Runnable save;

    /** The feature's config file when its options are server rules, else null */
    @Nullable
    private final String serverConfigFile;

    /** The categories, in the order of their tiles */
    private final List<ConfigCategory<C>> categories;

    /** Whether the tiles were built once (so they are built again when the screen is shown again) */
    private boolean built;

    /**
     * Creates the category menu of a feature
     *
     * @param parent           screen to return to when this screen is closed
     * @param keyPrefix        the feature's translation key prefix, ending with a dot
     * @param config           reads the feature's current config
     * @param defaults         creates a config with the default values
     * @param save             saves the feature's config
     * @param reset            restores and saves the feature's default config (every category)
     * @param serverConfigFile the config file when the options are server rules (shows the server notice), or null
     * @param categories       the categories, in the order of their tiles
     */
    protected CategoryMenuScreen(Screen parent, String keyPrefix, Supplier<C> config, Supplier<C> defaults,
                                 Runnable save, Runnable reset, @Nullable String serverConfigFile,
                                 List<ConfigCategory<C>> categories) {
        super(parent, keyPrefix, save, reset);
        this.keyPrefix = keyPrefix;
        this.config = config;
        this.defaults = defaults;
        this.save = save;
        this.serverConfigFile = serverConfigFile;
        this.categories = List.copyOf(categories);
    }

    /**
     * Returns the categories of this feature
     *
     * @return the categories, in the order of their tiles
     */
    public List<ConfigCategory<C>> categories() {
        return this.categories;
    }

    @Override
    protected int rowHeight() {
        return ConfigList.TILE_ROW_HEIGHT;
    }

    @Override
    protected Component resetMessage() {
        return Component.translatable("firstone-framework.config.reset.message_all");
    }

    @Override
    public void added() {
        super.added();
        // coming back from a category: build the tiles again, with the new counts
        if (this.built) {
            rebuildWidgets();
        }
    }

    @Override
    protected void addOptions() {
        this.built = true;
        if (this.serverConfigFile != null) {
            addServerNotice(this.serverConfigFile);
        }
        addRow(new ConfigList.SectionRow(Component.translatable("firstone-framework.config.categories")));
        List<ConfigList.Tile> tiles = new ArrayList<>();
        C current = this.config.get();
        for (ConfigCategory<C> category : this.categories) {
            String key = "category." + category.key();
            int on = category.countOn(current);
            int all = category.toggles().size();
            Component status = Component.translatable("firstone-framework.config.category_status", on, all);
            Component tooltip = Component.empty().append(text(key + ".description")).append("\n")
                .append(Component.translatable("firstone-framework.config.category_status.tooltip", on, all)
                    .withStyle(ChatFormatting.GRAY));
            tiles.add(new ConfigList.Tile(new ItemStack(category.icon()), text(key), tooltip,
                button -> this.minecraft.setScreen(categoryScreen(category)), status));
        }
        for (int i = 0; i < tiles.size(); i += 2) {
            addRow(new ConfigList.TileRow(tiles.subList(i, Math.min(i + 2, tiles.size()))));
        }
    }

    /**
     * Creates the screen of one category, with this screen as its parent
     *
     * @param category the category
     * @return its screen
     */
    public Screen categoryScreen(ConfigCategory<C> category) {
        return new CategoryOptionsScreen<>(this, this.title, this.keyPrefix, category, this.config, this.defaults,
            this.save);
    }
}
