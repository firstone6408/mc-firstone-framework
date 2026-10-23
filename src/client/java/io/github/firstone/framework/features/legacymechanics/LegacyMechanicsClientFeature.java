package io.github.firstone.framework.features.legacymechanics;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.features.legacymechanics.enchanting.EnchantingClues;
import io.github.firstone.framework.features.legacymechanics.sword.SwordBlockingSync;

/**
 * Client side of Legacy Mechanics, a server feature: only sets up what lets this client work with a server that has
 * it
 *
 * <p>Legacy Mechanics itself ({@link LegacyMechanicsFeature}) lives in {@code src/main} and runs on the server, so it
 * cannot reach client code. Its client side is registered in {@code FirstOneFrameworkClient} like a client-only
 * feature and only uses {@link #initializeClient()}: it listens to the rules the server sends. It has no settings and
 * no tile in the settings menu (that is Legacy Mechanics' own). Each part is in the same folder (and package) as
 * its server side:</p>
 * <ul>
 *   <li>{@code sword/} — what the server said about sword blocking ({@link SwordBlockingSync})</li>
 *   <li>{@code enchanting/} — the enchanting rules, the Show Clues button and the real levels a line takes
 *       ({@link EnchantingClues})</li>
 * </ul>
 *
 * <p>Nothing here is a setting of the client: it follows what the server said, and does nothing on a server without
 * the mod.</p>
 */
public class LegacyMechanicsClientFeature implements Feature {

    @Override
    public String getId() {
        return "legacy_mechanics_client";
    }

    /**
     * Listens to what a server with Legacy Mechanics sends: sword blocking and the enchanting rules
     */
    @Override
    public void initializeClient() {
        SwordBlockingSync.register();
        EnchantingClues.register();
    }
}
