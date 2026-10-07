package io.github.firstone.framework;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.FeatureRegistry;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Main entrypoint of FirstOne Framework
 *
 * <p>Responsible for initializing the framework and calling {@link Feature#initialize()}
 * for every registered feature; runs in every environment (physical client and dedicated server)</p>
 *
 * <p>Register here only features that change game logic decided by the server, by calling
 * {@code FeatureRegistry.register()} in {@link #onInitialize()} before the initialization loop runs.
 * Client-only features are registered in {@code FirstOneFrameworkClient} instead</p>
 */
public class FirstOneFramework implements ModInitializer {

    /** Main ID of the mod, used as namespace and logger name */
    public static final String MOD_ID = "firstone-framework";

    /** Logger used by the whole framework */
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("FirstOne Framework initializing...");

        FeatureRegistry.register(new CombatTweaksFeature());

        List<Feature> features = FeatureRegistry.getAll();
        for (Feature feature : features) {
            feature.initialize();
            LOGGER.info("Initialized feature: {}", feature.getId());
        }

        LOGGER.info("FirstOne Framework ready ({} features loaded)", features.size());
    }
}
