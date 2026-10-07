package io.github.firstone.framework.common;

/**
 * Core interface that every feature must implement
 *
 * <p>Usage: create a new class in features/&lt;feature name&gt;/ and implement this interface,
 * then register it in {@link FeatureRegistry} from one of the entrypoints below</p>
 *
 * <h2>Entrypoints and lifecycle methods</h2>
 * <p>The framework has one entrypoint per environment (declared in {@code fabric.mod.json}).
 * Each entrypoint calls exactly one lifecycle method of this interface:</p>
 * <ul>
 *   <li>{@code FirstOneFramework} ("main") — runs in every environment, physical client and
 *       dedicated server → calls {@link #initialize()}</li>
 *   <li>{@code FirstOneFrameworkClient} ("client") — runs only on the physical client
 *       (the normal game, including singleplayer) → calls {@link #initializeClient()}</li>
 *   <li>{@code FirstOneFrameworkDedicatedServer} ("server") — runs only on a dedicated server
 *       → calls {@link #initializeDedicatedServer()}</li>
 * </ul>
 *
 * <p>The three methods are <b>independent hooks, not steps that belong together</b>. Each one exists
 * for one environment. A feature overrides only the hook of the environment it works in (normally
 * just one) and leaves the others as the default no-op, so the overridden method shows at a glance
 * where the feature runs.</p>
 *
 * <h2>Choosing the hook</h2>
 * <ul>
 *   <li><b>Client-only feature</b> (the default for this mod) — class in {@code src/client},
 *       registered in {@code FirstOneFrameworkClient}, overrides {@link #initializeClient()}.
 *       Examples: {@code AnimatiumFeature}, {@code AppearanceFeature}</li>
 *   <li><b>Feature that changes game logic decided by the server</b> (only when really needed) —
 *       class in {@code src/main}, registered in {@code FirstOneFramework}, overrides {@link #initialize()}.
 *       This also covers singleplayer, because the integrated server runs inside the client.
 *       Example: {@code CombatTweaksFeature}</li>
 *   <li><b>Setup that must exist only on a standalone dedicated server</b> — overrides
 *       {@link #initializeDedicatedServer()}. No feature needs this yet</li>
 * </ul>
 *
 * <p><b>Note:</b> features registered in {@code FirstOneFrameworkClient} are registered after the
 * "main" entrypoint has run, so they never receive {@link #initialize()}, and they do not exist on a
 * dedicated server at all.</p>
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
     * <p>Shown on the button in {@code MainConfigScreen}.
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
     * Hook of the "main" entrypoint: setup for a feature that changes game logic decided by the server
     *
     * <p><b>Called by:</b> {@code FirstOneFramework.onInitialize()}, once, in every environment
     * (physical client and dedicated server), only for features registered in {@code FirstOneFramework}.</p>
     *
     * <p><b>Use it for:</b> things that must exist wherever the game logic runs — configs read by
     * mixins in {@code src/main}, blocks, items, networking payloads, commands
     * ({@code CommandRegistrationCallback}) and server events ({@code ServerLifecycleEvents}).
     * Because it also runs on the physical client, this logic works in singleplayer too.</p>
     *
     * <p><b>Rules:</b> never use client-only classes ({@code net.minecraft.client.*}); the feature's
     * client parts (such as its config screen) live in {@code src/client}.</p>
     *
     * <p><b>Example:</b> {@code CombatTweaksFeature} loads {@code combat_tweaks.json} here, because its
     * mixins need it on the server to calculate damage.</p>
     */
    default void initialize() {}

    /**
     * Hook of the "client" entrypoint: setup for a client-only feature
     *
     * <p><b>Called by:</b> {@code FirstOneFrameworkClient.onInitializeClient()}, once, only on the
     * physical client (the normal game, including singleplayer), for every registered feature.
     * Never called on a dedicated server.</p>
     *
     * <p><b>Use it for:</b> all setup of a client-only feature — loading its config, client events
     * such as {@code ClientTickEvents} / {@code ClientLifecycleEvents}, renderers and key bindings.</p>
     *
     * <p><b>Rules:</b> only a feature class in {@code src/client} can use client classes here;
     * a class in {@code src/main} cannot (it does not compile).</p>
     *
     * <p><b>Example:</b> {@code AnimatiumFeature} and {@code AppearanceFeature} load their configs here.</p>
     */
    default void initializeClient() {}

    /**
     * Hook of the "server" entrypoint: setup that must exist only on a standalone dedicated server
     *
     * <p><b>Called by:</b> {@code FirstOneFrameworkDedicatedServer.onInitializeServer()}, once, only on
     * a dedicated server, for every registered feature (only features registered in
     * {@code FirstOneFramework} exist there).</p>
     *
     * <p><b>Not called in singleplayer:</b> singleplayer runs an integrated server inside the client,
     * which is not a dedicated server. Server logic that must also work in singleplayer belongs in
     * {@link #initialize()}.</p>
     *
     * <p><b>Use it for:</b> things that only make sense on a standalone server, such as
     * dedicated-server-only settings or console setup.</p>
     *
     * <p><b>Rules:</b> never use client-only classes here.</p>
     *
     * <p>No feature needs it yet.</p>
     */
    default void initializeDedicatedServer() {}
}
