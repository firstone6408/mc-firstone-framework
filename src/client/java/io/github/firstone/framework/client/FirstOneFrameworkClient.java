package io.github.firstone.framework.client;

import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.client.FeatureScreenRegistry.Side;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.client.screen.AnimatiumConfigScreen;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import io.github.firstone.framework.features.appearance.client.screen.AppearanceConfigScreen;
import io.github.firstone.framework.features.combattweaks.client.screen.CombatTweaksConfigScreen;
import io.github.firstone.framework.features.legacyhud.LegacyHudFeature;
import io.github.firstone.framework.features.legacyhud.client.screen.LegacyHudConfigScreen;
import io.github.firstone.framework.features.legacymechanics.client.screen.LegacyMechanicsConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.item.Items;

/**
 * Client entrypoint of FirstOne Framework
 *
 * <p>Responsible for calling {@link Feature#initializeClient()} for every feature
 * and for registering client-only features and each feature's config screen in {@link FeatureScreenRegistry}</p>
 *
 * <p>Only called on the client, never on a dedicated server</p>
 */
public class FirstOneFrameworkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FirstOneFramework.LOGGER.info("FirstOne Framework initializing client...");

        // client-only features are registered here (not through FirstOneFramework)
        FeatureRegistry.register(new AnimatiumFeature());
        FeatureRegistry.register(new AppearanceFeature());
        FeatureRegistry.register(new LegacyHudFeature());

        FeatureScreenRegistry.register("animatium", Items.ARMOR_STAND, Side.CLIENT, AnimatiumConfigScreen::new);
        FeatureScreenRegistry.register("appearance", Items.PAINTING, Side.CLIENT, AppearanceConfigScreen::new);
        FeatureScreenRegistry.register("legacy_hud", Items.MAP, Side.CLIENT, LegacyHudConfigScreen::new);
        FeatureScreenRegistry.register("combat_tweaks", Items.IRON_SWORD, Side.SERVER, CombatTweaksConfigScreen::new);
        FeatureScreenRegistry.register("legacy_mechanics", Items.CLOCK, Side.SERVER, LegacyMechanicsConfigScreen::new);

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeClient();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework client ready");
    }
}
