package io.github.firstone.framework.features.legacymechanics.sword;

/**
 * What the server the client is playing on said about sword blocking ({@link SwordBlockingPayload})
 *
 * <p>Only the client side of the game reads this; it starts every connection as "not allowed" and changes when the
 * server tells. Written on the client's network handling, read on the client thread.</p>
 */
public final class SwordBlockingSupport {

    /** Whether the server lets swords block; false until it says so */
    private static volatile boolean serverAllows;

    private SwordBlockingSupport() {}

    /**
     * Tells whether the server the client plays on lets swords block
     *
     * @return true after the server said so, false on servers without the mod
     */
    public static boolean serverAllows() {
        return serverAllows;
    }

    /**
     * Keeps what the server said (or false when a connection starts or ends)
     *
     * @param allowed whether swords block on the server
     */
    public static void setServerAllows(boolean allowed) {
        serverAllows = allowed;
    }
}
