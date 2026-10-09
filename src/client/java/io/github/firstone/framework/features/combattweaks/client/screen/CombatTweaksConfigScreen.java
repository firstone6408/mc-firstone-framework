package io.github.firstone.framework.features.combattweaks.client.screen;

import io.github.firstone.framework.client.screen.ConfigScreen;
import io.github.firstone.framework.features.combattweaks.CombatTweaksConfig;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.minecraft.client.gui.screens.Screen;

/**
 * Settings screen of the Combat Tweaks feature
 *
 * <p>These are server rules: changes apply to singleplayer right away; a dedicated server uses its own
 * {@code combat_tweaks.json}, which the notice at the top explains. "Sweeping Edge Required" is grayed out while
 * "Disable Sweeping Attack" is off, because it has no effect then.</p>
 */
public class CombatTweaksConfigScreen extends ConfigScreen {

    /**
     * Creates the Combat Tweaks settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public CombatTweaksConfigScreen(Screen parent) {
        super(parent, "firstone-framework.combat_tweaks.", CombatTweaksFeature::saveConfig,
            CombatTweaksFeature::resetConfig);
    }

    @Override
    protected void addOptions() {
        CombatTweaksConfig config = CombatTweaksFeature.getConfig();
        addServerNotice("combat_tweaks.json");

        addSection("category.attack");
        addToggle("no_attack_cooldown", config.noAttackCooldown, value -> config.noAttackCooldown = value);

        addSection("category.sweeping");
        addToggle("disable_sweeping_attack", config.disableSweepingAttack, value -> config.disableSweepingAttack = value);
        addToggle("sweeping_edge_required", config.sweepingEdgeRequired, value -> config.sweepingEdgeRequired = value)
            .enabledWhen(() -> config.disableSweepingAttack);
    }
}
