package io.github.firstone.framework.common;

/**
 * Core interface that every feature must implement
 *
 * <p>Every feature in the system must implement this interface and all of its methods
 * so the framework can manage the feature's lifecycle consistently</p>
 *
 * <p>Usage: create a new class in features/&lt;feature name&gt;/ and implement this interface,
 * then register it in {@link FeatureRegistry} from a mod entrypoint</p>
 */
public interface Feature {

    /**
     * Returns the unique ID of this feature
     *
     * <p>The ID must not collide with other features and should use snake_case</p>
     *
     * @return the feature ID, e.g. "falling_tree" or "vein_miner"
     */
    String getId();

    /**
     * Returns the name of this feature shown in the GUI
     *
     * <p>Shown on the button in {@link io.github.firstone.framework.client.screen.MainConfigScreen}.
     * If the feature does not override it, the ID is used instead</p>
     *
     * @return display name, e.g. "Animatium" or "Falling Tree"
     */
    default String getDisplayName() {
        return getId();
    }

    /**
     * Returns a short description of this feature, shown when hovering the button
     *
     * <p>If the feature does not override it, no tooltip is shown</p>
     *
     * @return the description, or {@code null} for no tooltip
     */
    default String getDescription() {
        return null;
    }

    /**
     * Initializes the feature for both client and server (common logic)
     *
     * <p>Called from {@code ModInitializer} while the mod is loading.
     * Suitable for registering events, blocks, items or other registries
     * shared by both sides</p>
     */
    void initialize();

    /**
     * Initializes the feature on the client only
     *
     * <p>Called from {@code ClientModInitializer}, on the client only.
     * Suitable for screens, renderers, key bindings or client-only events.
     * Never called on a dedicated server</p>
     */
    default void initializeClient() {}

    /**
     * Initializes the feature's server-side logic
     *
     * <p>Called right after {@link #initialize()} from {@code ModInitializer.onInitialize()},
     * so it currently runs on both the client and the dedicated server.
     * Suitable for commands, persistent data or server-only logic</p>
     */
    default void initializeServer() {}
}
