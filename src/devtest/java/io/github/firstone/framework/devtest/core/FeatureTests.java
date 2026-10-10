package io.github.firstone.framework.devtest.core;

import java.util.List;

/**
 * The in-game tests of one feature
 *
 * <p>Each feature gets its own flat world, {@code devtest_<id>}, deleted and created again on every run. Before each
 * test the session resets the world's test site, the player and the {@link #configs()} to their defaults, so tests
 * never depend on each other.</p>
 */
public interface FeatureTests {

    /**
     * Returns the feature id, as in {@code Feature.getId()}; also used for the world, report and screenshot names
     *
     * @return the feature id, e.g. {@code legacy_vehicles}
     */
    String id();

    /**
     * Returns the live config objects the tests change; each is reset to its defaults before every test
     *
     * @return the config objects, e.g. {@code LegacyVehiclesFeature.getConfig()}
     */
    List<Object> configs();

    /**
     * Returns the feature's tests, in the order they run
     *
     * @return the tests
     */
    List<TestCase> tests();

    /**
     * Called once after the feature's tests (also when one failed), e.g. to delete files the tests made
     *
     * @param t the test context
     * @throws Exception any error is reported
     */
    default void cleanUp(TestContext t) throws Exception {
    }
}
