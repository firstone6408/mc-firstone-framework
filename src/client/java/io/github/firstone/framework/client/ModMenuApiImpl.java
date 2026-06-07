package io.github.firstone.framework.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.firstone.framework.client.screen.MainConfigScreen;

/**
 * Connects the framework to Mod Menu
 *
 * <p>Registers {@link MainConfigScreen} as the mod's config screen,
 * so players can open the settings from the mod list in Mod Menu</p>
 *
 * <p>This class is only called by Mod Menu. If Mod Menu is not installed,
 * this class is never loaded and the mod still works normally</p>
 */
public class ModMenuApiImpl implements ModMenuApi {

    /**
     * Returns the factory that creates the mod's config screen
     *
     * @return a factory that creates {@link MainConfigScreen} with its parent screen
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MainConfigScreen::new;
    }
}
