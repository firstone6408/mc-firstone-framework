package io.github.firstone.framework.devtest.features.legacymechanics;

import io.github.firstone.framework.common.sword.SwordBlockingSupport;

/**
 * The only place where the Legacy Mechanics tests reach into the mod's inner classes
 *
 * <p>The tests check what players do and take and only change the feature config; what the client was told about
 * sword blocking has no other way in. After a refactor only this file changes. Public: the tests of the feature's
 * parts live in their own packages.</p>
 */
public final class LegacyMechanicsInternals {

    private LegacyMechanicsInternals() {}

    /**
     * Tells what the client was told about sword blocking ({@code SwordBlockingSupport.serverAllows})
     *
     * @return true if the server said swords block
     */
    public static boolean clientAllowsSwordBlocking() {
        return SwordBlockingSupport.serverAllows();
    }

    /**
     * Changes what the client believes about sword blocking, as if the server had said so
     *
     * @param allowed whether swords block
     */
    public static void setClientAllowsSwordBlocking(boolean allowed) {
        SwordBlockingSupport.setServerAllows(allowed);
    }
}
