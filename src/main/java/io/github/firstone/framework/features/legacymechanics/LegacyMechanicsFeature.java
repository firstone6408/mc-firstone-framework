package io.github.firstone.framework.features.legacymechanics;

import io.github.firstone.framework.common.Feature;
import io.github.firstone.framework.common.boat.BoatPushPayload;
import io.github.firstone.framework.common.config.ConfigManager;
import io.github.firstone.framework.common.sword.SwordBlockingPayload;
import io.github.firstone.framework.features.legacymechanics.sword.LegacySwordBlocking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Legacy Mechanics — game rules decided by the server, as in Minecraft 1.7.10
 *
 * <p>Server-side counterpart of the 1.7.10 experience: it changes what the server calculates (knockback, the hitbox of
 * crouching players), so every player on the server gets it, with or without the mod. It is independent of the
 * client-only Animatium feature. Registered in {@code FirstOneFramework}; runs on the dedicated server and on the
 * integrated server of singleplayer.</p>
 *
 * <p>Each part has its own folder, here and in {@code mixin/legacymechanics/}: {@code knockback/} (knockback of hits
 * and attacks), {@code player/} (the hitbox of crouching players, mixin only), {@code food/} (natural
 * regeneration), {@code sword/} (sword blocking) and {@code boat/} (boat breaking and coasting).</p>
 */
public class LegacyMechanicsFeature implements Feature {

    /** Name of the config file */
    private static final String CONFIG_FILE = "legacy_mechanics.json";

    /** Current config, starts with the default values */
    private static LegacyMechanicsConfig config = new LegacyMechanicsConfig();

    @Override
    public String getId() {
        return "legacy_mechanics";
    }

    @Override
    public void initialize() {
        config = ConfigManager.load(CONFIG_FILE, LegacyMechanicsConfig.class, new LegacyMechanicsConfig());
        PayloadTypeRegistry.playS2C().register(BoatPushPayload.TYPE, BoatPushPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SwordBlockingPayload.TYPE, SwordBlockingPayload.CODEC);
        LegacySwordBlocking.registerSync();
    }

    /**
     * Returns the current Legacy Mechanics config
     *
     * <p>Used by the mixins and the config screen.</p>
     *
     * @return the current config, never null
     */
    public static LegacyMechanicsConfig getConfig() {
        return config;
    }

    /**
     * Saves the current config to its JSON file
     *
     * <p>Called after a setting is changed in the config screen.</p>
     */
    public static void saveConfig() {
        ConfigManager.save(CONFIG_FILE, config);
    }

    /**
     * Restores the default settings and saves them to the config file
     *
     * <p>Called by the Reset button of the config screen.</p>
     */
    public static void resetConfig() {
        config = new LegacyMechanicsConfig();
        saveConfig();
    }
}
