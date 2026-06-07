package io.github.firstone.framework.client;

import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint of FirstOne Framework
 *
 * <p>Responsible for calling {@link Feature#initializeClient()} for every feature
 * when the client starts</p>
 *
 * <p>Only called on the client, never on a dedicated server</p>
 */
public class FirstOneFrameworkClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FirstOneFramework.LOGGER.info("FirstOne Framework initializing client...");

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeClient();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework client ready");
    }
}
