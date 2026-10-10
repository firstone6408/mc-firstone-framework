package io.github.firstone.framework.devtest.core;

/**
 * One named test
 *
 * @param name what the test checks, shown in the report
 * @param body the test's code
 * @param slow the test takes long (about 8 seconds or more); fast mode leaves it out
 */
public record TestCase(String name, Body body, boolean slow) {

    /**
     * Creates a test
     *
     * @param name what the test checks, shown in the report
     * @param body the test's code, usually a method reference ({@code this::waterSpeed})
     * @return the test
     */
    public static TestCase of(String name, Body body) {
        return new TestCase(name, body, false);
    }

    /**
     * Creates a test that takes long (about 8 seconds or more); fast mode ({@code FAST=true}) leaves it out
     *
     * @param name what the test checks, shown in the report
     * @param body the test's code
     * @return the test
     */
    public static TestCase slow(String name, Body body) {
        return new TestCase(name, body, true);
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
