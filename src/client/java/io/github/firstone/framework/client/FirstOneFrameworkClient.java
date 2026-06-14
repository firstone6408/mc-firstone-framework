package io.github.firstone.framework.client;

import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import io.github.firstone.framework.features.animatium.client.screen.AnimatiumConfigScreen;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint of FirstOne Framework
 *
 * <p>Responsible for calling {@link Feature#initializeClient()} for every feature
 * and for registering each feature's config screen in {@link FeatureScreenRegistry}</p>
 *
 * <p>Only called on the client, never on a dedicated server</p>
 */
public class FirstOneFrameworkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FirstOneFramework.LOGGER.info("FirstOne Framework initializing client...");

        FeatureScreenRegistry.register("animatium", AnimatiumConfigScreen::new);

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeClient();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework client ready");
    }
}
