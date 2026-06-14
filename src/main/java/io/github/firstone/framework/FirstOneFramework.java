package io.github.firstone.framework;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Main entrypoint of FirstOne Framework
 *
 * <p>Responsible for initializing the framework and calling {@link Feature#initialize()}
 * together with {@link Feature#initializeServer()} for every registered feature</p>
 *
 * <p>Features are registered by calling {@code FeatureRegistry.register()} in
 * {@link #onInitialize()} before the initialization loop runs</p>
 */
public class FirstOneFramework implements ModInitializer {

    /** Main ID of the mod, used as namespace and logger name */
    public static final String MOD_ID = "firstone-framework";

    /** Logger used by the whole framework */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FirstOne Framework initializing...");

        FeatureRegistry.register(new AnimatiumFeature());

        List<Feature> features = FeatureRegistry.getAll();
        for (Feature feature : features) {
            feature.initialize();
            feature.initializeServer();
            LOGGER.info("Initialized feature: {}", feature.getId());
        }

        LOGGER.info("FirstOne Framework ready ({} features loaded)", features.size());
    }
}
