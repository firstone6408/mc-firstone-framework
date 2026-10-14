package io.github.firstone.framework;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * Dedicated server entrypoint of FirstOne Framework
 *
 * <p>Responsible for calling {@link Feature#initializeDedicatedServer()} for every feature.
 * Only called on a dedicated server, never on the client and never in singleplayer
 * (singleplayer uses an integrated server inside the client)</p>
 *
 * <p>Used for setup that only makes sense on a standalone server. Server logic that must also
 * work in singleplayer belongs in {@link Feature#initialize()} instead</p>
 */
public class FirstOneFrameworkDedicatedServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        FirstOneFramework.LOGGER.info("FirstOne Framework initializing dedicated server...");

        for (Feature feature : FeatureRegistry.getAll()) {
            feature.initializeDedicatedServer();
        }

        FirstOneFramework.LOGGER.info("FirstOne Framework dedicated server ready");
    }
}
