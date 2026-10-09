package io.github.firstone.framework.client.screen;

import io.github.firstone.framework.client.FeatureScreenRegistry;
import io.github.firstone.framework.client.FeatureScreenRegistry.Side;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Main settings screen of FirstOne Framework: the features as tiles, grouped into Client and Server
 *
 * <p>Each group starts with a section title that says where its settings apply (hover it for details). Each tile
 * shows the icon and name registered in {@link FeatureScreenRegistry}; hovering it shows the feature's description
 * ({@code firstone-framework.<id>.description}) and clicking it opens the feature's screen. Features are listed in
 * ID order; features without a registered screen are not shown.</p>
 *
 * <p>How to open this screen (Mod Menu does this through {@code ModMenuApiImpl}):</p>
 * <pre>{@code
 * Minecraft.getInstance().setScreen(new MainConfigScreen(currentScreen));
 * }</pre>
 */
public class MainConfigScreen extends ConfigScreen {

    /**
     * Creates the framework's main settings screen
     *
     * @param parent screen to return to when this screen is closed; may be {@code null}
     */
    public MainConfigScreen(Screen parent) {
        super(parent, "firstone-framework.", null, null);
    }

    @Override
    protected int rowHeight() {
        return ConfigList.TILE_ROW_HEIGHT;
    }

    @Override
    protected void addOptions() {
        List<Feature> features = new ArrayList<>(FeatureRegistry.getAll());
        features.sort(Comparator.comparing(feature -> feature.getId()));
        boolean empty = true;
        for (Side side : Side.values()) {
            empty &= !addGroup(side, features);
        }
        if (empty) {
            addNotice("no_features");
        }
    }

    /**
     * Adds the section title and the tiles of one side, two tiles per row
     *
     * @param side     the group to add
     * @param features every feature, in display order
     * @return true if the group had at least one feature (otherwise nothing is added)
     */
    private boolean addGroup(Side side, List<Feature> features) {
        List<ConfigList.Tile> tiles = new ArrayList<>();
        for (Feature feature : features) {
            FeatureScreenRegistry.Entry entry = FeatureScreenRegistry.get(feature.getId());
            if (entry != null && entry.side() == side) {
                tiles.add(new ConfigList.Tile(
                    new ItemStack(entry.icon()),
                    text(feature.getId() + ".name"),
                    text(feature.getId() + ".description"),
                    button -> openFeatureScreen(feature)));
            }
        }
        if (tiles.isEmpty()) {
            return false;
        }

        String key = "side." + side.name().toLowerCase(Locale.ROOT);
        addRow(new ConfigList.SectionRow(text(key), text(key + ".note"), text(key + ".tooltip")));
        for (int i = 0; i < tiles.size(); i += 2) {
            addRow(new ConfigList.TileRow(tiles.subList(i, Math.min(i + 2, tiles.size()))));
        }
        return true;
    }

    /**
     * Opens the config screen of a feature
     *
     * <p>Does nothing if no screen is registered for the feature.</p>
     *
     * @param feature the selected feature
     */
    protected void openFeatureScreen(Feature feature) {
        Screen screen = FeatureScreenRegistry.createScreen(feature.getId(), this);
        if (screen != null) {
            this.minecraft.setScreen(screen);
        }
    }
}
