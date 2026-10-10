package io.github.firstone.framework.devtest.core;

/**
 * One named test
 *
 * @param name what the test checks, shown in the report
 * @param body the test's code
 */
public record TestCase(String name, Body body) {

    /**
     * Creates a test
     *
     * @param name what the test checks, shown in the report
     * @param body the test's code, usually a method reference ({@code this::waterSpeed})
     * @return the test
     */
    public static TestCase of(String name, Body body) {
        return new TestCase(name, body);
    }

    /** The code of a test; it runs on the test thread, in step with the game's ticks ({@link TestContext}) */
    @FunctionalInterface
    public interface Body {

        /**
         * Runs the test
         *
         * @param t the test context (world, player, checks)
         * @throws Exception any error fails the test
         */
        void run(TestContext t) throws Exception;
    }
}
