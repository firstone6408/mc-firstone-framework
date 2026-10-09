package io.github.firstone.framework.features.legacymechanics.client.screen;

import io.github.firstone.framework.client.screen.FeatureOptionsScreen;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen of the Legacy Mechanics feature
 *
 * <p>These are server rules: changes apply to singleplayer right away; a dedicated server uses its own
 * {@code legacy_mechanics.json}, which the notice at the top explains.</p>
 */
public class LegacyMechanicsConfigScreen extends FeatureOptionsScreen {

    /**
     * Creates the Legacy Mechanics settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public LegacyMechanicsConfigScreen(Screen parent) {
        super(parent, "firstone-framework.legacy_mechanics.", LegacyMechanicsFeature::saveConfig);
    }

    @Override
    protected void addOptions() {
        LegacyMechanicsConfig config = LegacyMechanicsFeature.getConfig();
        addNotice("notice");
        addToggle("legacy_knockback", config.legacyKnockback, value -> config.legacyKnockback = value);
        addToggle("legacy_attack_knockback", config.legacyAttackKnockback, value -> config.legacyAttackKnockback = value);
        addToggle("legacy_crouch_hitbox", config.legacyCrouchHitbox, value -> config.legacyCrouchHitbox = value);
    }
}
