package io.github.firstone.framework.features.animatium.hand;

import io.github.firstone.framework.common.sword.SwordBlockingPayload;
import io.github.firstone.framework.common.sword.SwordBlockingSupport;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Keeps the client in step with the server about sword blocking (the ability itself is Legacy Mechanics')
 *
 * <p>Every connection starts with swords not blocking; the server's {@link SwordBlockingPayload} says otherwise when
 * it has the mod and the setting on. While swords block, the first-person hand takes the 1.7.10 block pose
 * ({@link LegacyFirstPersonItem}).</p>
 */
public final class SwordBlockingSync {

    private SwordBlockingSync() {}

    /**
     * Listens to the server's sword blocking setting and forgets it between connections
     *
     * <p>Called once by {@code AnimatiumFeature.initializeClient()}.</p>
     */
    public static void register() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> SwordBlockingSupport.setServerAllows(false));
        ClientPlayConnectionEvents.DISCONNECT.register(
            (handler, client) -> SwordBlockingSupport.setServerAllows(false));
        ClientPlayNetworking.registerGlobalReceiver(SwordBlockingPayload.TYPE,
            (payload, context) -> SwordBlockingSupport.setServerAllows(payload.allowed()));
    }
}
