package io.github.firstone.framework.features.cosmetics.client.screen;

import io.github.firstone.framework.client.screen.ConfigList;
import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.Function;

/**
 * Main settings screen of the Cosmetics feature: one tile per category, and the files folder
 *
 * <ul>
 *   <li><b>Categories</b> — Death Effect, Item Break, Music Discs; each opens its own screen</li>
 *   <li><b>Files</b> — open the files folder, reload the files</li>
 * </ul>
 *
 * <p>Reset here restores every category; each category screen has its own Reset.</p>
 */
public class CosmeticsConfigScreen extends ConfigScreen {

    /**
     * Creates the Cosmetics main settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public CosmeticsConfigScreen(Screen parent) {
        super(parent, "firstone-framework.cosmetics.", CosmeticsFeature::saveConfig, CosmeticsFeature::resetConfig);
    }

    @Override
    protected int rowHeight() {
        return ConfigList.TILE_ROW_HEIGHT;
    }

    @Override
    protected void addOptions() {
        addSection("category.settings");
        addRow(new ConfigList.TileRow(List.of(
            tile("death", Items.SKELETON_SKULL, CosmeticsDeathScreen::new),
            tile("item_break", Items.WOODEN_PICKAXE, CosmeticsItemBreakScreen::new))));
        addRow(new ConfigList.TileRow(List.of(
            tile("music_discs", Items.MUSIC_DISC_CAT, CosmeticsMusicDiscsScreen::new))));

        addSection("category.files");
        addButton("folder", () -> Util.getPlatform().openPath(CosmeticsFiles.root()));
        addButton("reload", () -> this.minecraft.reloadResourcePacks());
    }

    /**
     * Creates the tile of a category
     *
     * @param category key of the category (name {@code <category>.name}, tooltip {@code <category>.description})
     * @param icon     item shown on the tile
     * @param screen   creates the category screen, with this screen as parent
     */
    private ConfigList.Tile tile(String category, Item icon, Function<Screen, Screen> screen) {
        return new ConfigList.Tile(new ItemStack(icon), text(category + ".name"), text(category + ".description"),
            button -> this.minecraft.setScreen(screen.apply(this)));
    }
}
