package io.github.firstone.framework.devtest.core;

/**
 * Stops a test that cannot go on (e.g. the player could not get into the boat); the test is reported as failed
 */
public class TestFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the failure
     *
     * @param message what went wrong
     */
    public TestFailure(String message) {
        super(message);
    }
}
