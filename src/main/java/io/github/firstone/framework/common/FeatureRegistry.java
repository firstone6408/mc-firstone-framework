package io.github.firstone.framework.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of all features in the system
 *
 * <p>Used to add features to the framework and to get the list of all features
 * when calling the various initialize methods</p>
 *
 * <p>Usage: call {@link #register(Feature)} from a mod entrypoint
 * before the framework calls the feature's initialize methods</p>
 *
 * <pre>{@code
 * // Example: registering features
 * FeatureRegistry.register(new FallingTreeFeature());
 * FeatureRegistry.register(new VeinMinerFeature());
 * }</pre>
 */
public final class FeatureRegistry {

    private static final List<Feature> FEATURES = new ArrayList<>();

    private FeatureRegistry() {}

    /**
     * Registers a feature
     *
     * <p>Must always be called before the framework calls initialize,
     * usually from {@code ModInitializer.onInitialize()}</p>
     *
     * @param feature the feature to register
     * @throws IllegalArgumentException if a feature with the same ID is already registered
     */
    public static void register(Feature feature) {
        String id = feature.getId();
        for (Feature existing : FEATURES) {
            if (existing.getId().equals(id)) {
                throw new IllegalArgumentException("Duplicate feature ID: " + id);
            }
        }
        FEATURES.add(feature);
    }

    /**
     * Returns all registered features
     *
     * <p>The returned list is read-only and cannot be modified directly</p>
     *
     * @return all features (read-only)
     */
    public static List<Feature> getAll() {
        return Collections.unmodifiableList(FEATURES);
    }

}
