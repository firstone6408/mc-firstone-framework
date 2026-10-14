package io.github.firstone.framework.devtest;

import io.github.firstone.framework.devtest.core.TestSession;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Entrypoint of the dev test mod: starts a test session when the game was launched by the {@code devTest} task
 *
 * <p>The task passes the features to test in the {@value #FEATURES_PROPERTY} system property ({@code all} or ids
 * separated by commas). Without it the test mod does nothing.</p>
 */
public final class DevTestClient implements ClientModInitializer {

    /** System property with the features to test */
    public static final String FEATURES_PROPERTY = "firstone.devtest.features";

    /** System property: run only the tests whose name contains this text (empty: all) */
    public static final String TESTS_PROPERTY = "firstone.devtest.tests";

    /** System property: feature ids to leave out, separated by commas */
    public static final String SKIP_PROPERTY = "firstone.devtest.skip";

    /** System property: {@code true} leaves out the slow tests ({@code TestCase.slow}) */
    public static final String FAST_PROPERTY = "firstone.devtest.fast";

    /** System property: {@code true} replaces the baseline screenshots with the new ones */
    public static final String UPDATE_BASELINES_PROPERTY = "firstone.devtest.updateBaselines";

    /**
     * Registers the test session on the client tick, if tests were asked for
     */
    @Override
    public void onInitializeClient() {
        String features = System.getProperty(FEATURES_PROPERTY);
        if (features == null || features.isBlank()) {
            return;
        }
        TestSession session = new TestSession(
            DevTests.select(features, System.getProperty(SKIP_PROPERTY, "")),
            System.getProperty(TESTS_PROPERTY, ""), Boolean.getBoolean(FAST_PROPERTY),
            Boolean.getBoolean(UPDATE_BASELINES_PROPERTY));
        ClientTickEvents.END_CLIENT_TICK.register(session::onClientTick);
    }
}
