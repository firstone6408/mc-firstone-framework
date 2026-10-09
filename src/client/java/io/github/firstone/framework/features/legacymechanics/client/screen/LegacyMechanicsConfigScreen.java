package io.github.firstone.framework.features.legacymechanics.client.screen;

import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Settings screen of the Legacy Mechanics feature
 *
 * <p>Built on the vanilla {@link OptionsSubScreen}. These are server rules: changes apply to singleplayer
 * immediately; a dedicated server uses its own {@code legacy_mechanics.json}, which the notice at the top explains.
 * Texts come from {@code assets/firstone-framework/lang/en_us.json}.</p>
 */
public class LegacyMechanicsConfigScreen extends OptionsSubScreen {

    /** Prefix of every translation key used by this screen */
    private static final String KEY = "firstone-framework.legacy_mechanics.";

    /** Width of a full-width row */
    private static final int ROW_WIDTH = 310;

    /**
     * Creates the Legacy Mechanics settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public LegacyMechanicsConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(KEY + "title"));
    }

    @Override
    protected void addOptions() {
        LegacyMechanicsConfig config = LegacyMechanicsFeature.getConfig();
        MultiLineTextWidget notice = new MultiLineTextWidget(Component.translatable(KEY + "notice"), this.font);
        notice.setMaxWidth(ROW_WIDTH).setCentered(true);
        this.list.addSmall(notice, null);
        this.list.addBig(toggle("legacy_knockback", config.legacyKnockback, value -> config.legacyKnockback = value));
        this.list.addBig(toggle("legacy_attack_knockback", config.legacyAttackKnockback, value -> config.legacyAttackKnockback = value));
        this.list.addBig(toggle("legacy_crouch_hitbox", config.legacyCrouchHitbox, value -> config.legacyCrouchHitbox = value));
    }

    /**
     * Creates an on/off option that saves the config when changed
     *
     * @param name   option name used in the translation keys ({@code <name>} and {@code <name>.tooltip})
     * @param value  current value
     * @param setter writes the new value into the config
     * @return the option to add to the list
     */
    private static OptionInstance<Boolean> toggle(String name, boolean value, Consumer<Boolean> setter) {
        return OptionInstance.createBoolean(
            KEY + name,
            OptionInstance.cachedConstantTooltip(Component.translatable(KEY + name + ".tooltip")),
            value,
            newValue -> {
                setter.accept(newValue);
                LegacyMechanicsFeature.saveConfig();
            }
        );
    }
}
