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
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.client.screen.CosmeticsConfigScreen;
import io.github.firstone.framework.features.legacyhud.LegacyHudFeature;
import io.github.firstone.framework.features.legacyhud.client.screen.LegacyHudConfigScreen;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsClientFeature;
import io.github.firstone.framework.features.legacymechanics.client.screen.LegacyMechanicsConfigScreen;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import io.github.firstone.framework.features.legacyvehicles.client.screen.LegacyVehiclesConfigScreen;
import io.github.firstone.framework.features.mobtweaks.client.screen.MobTweaksConfigScreen;
import io.github.firstone.framework.features.packmanager.PackManagerFeature;
import io.github.firstone.framework.features.packmanager.client.screen.PackManagerConfigScreen;
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
        FeatureRegistry.register(new CosmeticsFeature());
        FeatureRegistry.register(new LegacyVehiclesFeature());
        FeatureRegistry.register(new PackManagerFeature());

        // client side of server features: only the setup that lets this client work with a server that has them
        // (the features themselves live in src/main and cannot reach client code); no settings, no tile
        FeatureRegistry.register(new LegacyMechanicsClientFeature());

        // settings screens: the tile of each feature in the main settings screen (icon, client or server group) and the
        // screen it opens; client-only and server features alike
        FeatureScreenRegistry.register("animatium", Items.ARMOR_STAND, Side.CLIENT, AnimatiumConfigScreen::new);
        FeatureScreenRegistry.register("appearance", Items.PAINTING, Side.CLIENT, AppearanceConfigScreen::new);
        FeatureScreenRegistry.register("legacy_hud", Items.MAP, Side.CLIENT, LegacyHudConfigScreen::new);
        FeatureScreenRegistry.register("cosmetics", Items.AMETHYST_SHARD, Side.CLIENT, CosmeticsConfigScreen::new);
        FeatureScreenRegistry.register("legacy_vehicles", Items.OAK_BOAT, Side.CLIENT, LegacyVehiclesConfigScreen::new);
        FeatureScreenRegistry.register("pack_manager", Items.BOOKSHELF, Side.CLIENT, PackManagerConfigScreen::new);
        FeatureScreenRegistry.register("combat_tweaks", Items.IRON_SWORD, Side.SERVER, CombatTweaksConfigScreen::new);
        FeatureScreenRegistry.register("legacy_mechanics", Items.CLOCK, Side.SERVER, LegacyMechanicsConfigScreen::new);
        FeatureScreenRegistry.register("mob_tweaks", Items.ZOMBIE_HEAD, Side.SERVER, MobTweaksConfigScreen::new);

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeClient();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework client ready");
    }
}
