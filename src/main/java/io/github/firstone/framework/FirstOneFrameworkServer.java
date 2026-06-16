package io.github.firstone.framework;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * Dedicated server entrypoint of FirstOne Framework
 *
 * <p>Responsible for calling {@link Feature#initializeServer()} for every feature.
 * Only called on a dedicated server, never on the client</p>
 *
 * <p>Used to register commands, persistent data or server-only events
 * that must not run on the client</p>
 */
public class FirstOneFrameworkServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        FirstOneFramework.LOGGER.info("FirstOne Framework initializing server...");

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeServer();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework server ready");
    }
}
