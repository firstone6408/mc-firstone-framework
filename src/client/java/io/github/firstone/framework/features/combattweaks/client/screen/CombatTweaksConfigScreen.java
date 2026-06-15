package io.github.firstone.framework.features.combattweaks.client.screen;

import io.github.firstone.framework.features.combattweaks.CombatTweaksConfig;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Settings screen of the Combat Tweaks feature
 *
 * <p>Shows an on/off button for each combat option:</p>
 * <ul>
 *   <li>No Attack Cooldown — every attack deals full damage without waiting</li>
 *   <li>Disable Sweeping Attack — disables AOE damage around the target</li>
 *   <li>Sweeping Edge Required — sweeping only works with the Sweeping Edge enchantment</li>
 * </ul>
 *
 * <p>Changes are saved to the file immediately when a button is pressed</p>
 */
public class CombatTweaksConfigScreen extends Screen {

    private static final int TITLE_Y = 15;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 26;
    private static final int DONE_BUTTON_WIDTH = 100;

    /** Previous screen, returned to when this screen is closed */
    private final Screen parent;

    /** Sweeping Edge Required button, kept so its label can be updated when Disable Sweeping Attack changes */
    private Button sweepingEdgeButton;

    /**
     * Creates the Combat Tweaks settings screen
     *
     * @param parent screen to return to when this screen is closed
     */
    public CombatTweaksConfigScreen(Screen parent) {
        super(Component.literal("Combat Tweaks - Legacy Combat"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CombatTweaksConfig config = CombatTweaksFeature.getConfig();

        int centerX = (this.width - BUTTON_WIDTH) / 2;
        int startY = (this.height / 2) - (BUTTON_SPACING + BUTTON_SPACING / 2);

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("No Attack Cooldown", config.noAttackCooldown),
            btn -> {
                config.noAttackCooldown = !config.noAttackCooldown;
                btn.setMessage(buildToggleLabel("No Attack Cooldown", config.noAttackCooldown));
                CombatTweaksFeature.saveConfig();
            }
        ).pos(centerX, startY).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            buildToggleLabel("Disable Sweeping Attack", config.disableSweepingAttack),
            btn -> {
                config.disableSweepingAttack = !config.disableSweepingAttack;
                btn.setMessage(buildToggleLabel("Disable Sweeping Attack", config.disableSweepingAttack));
                // update the Sweeping Edge button label to show whether it has any effect
                sweepingEdgeButton.setMessage(buildSweepingEdgeLabel(config));
                CombatTweaksFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        sweepingEdgeButton = this.addRenderableWidget(Button.builder(
            buildSweepingEdgeLabel(config),
            btn -> {
                config.sweepingEdgeRequired = !config.sweepingEdgeRequired;
                btn.setMessage(buildSweepingEdgeLabel(config));
                CombatTweaksFeature.saveConfig();
            }
        ).pos(centerX, startY + BUTTON_SPACING * 2).size(BUTTON_WIDTH, BUTTON_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Done"),
            btn -> this.onClose()
        ).pos((this.width - DONE_BUTTON_WIDTH) / 2, this.height - 30).size(DONE_BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * Builds the toggle button text, showing ON/OFF in color
     *
     * @param label option name
     * @param value current state (true = ON, false = OFF)
     * @return the component shown on the button
     */
    private Component buildToggleLabel(String label, boolean value) {
        Component status = value
            ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
            : Component.literal("OFF").withStyle(ChatFormatting.RED);
        return Component.literal(label + ": ").append(status);
    }

    /**
     * Builds the Sweeping Edge Required button label, hinting that it only applies while Disable Sweeping Attack is on
     *
     * <p>If {@code disableSweepingAttack} is false, shows "N/A" in dark gray
     * to indicate that this setting currently has no effect</p>
     *
     * @param config the current config
     * @return the component shown on the button
     */
    private Component buildSweepingEdgeLabel(CombatTweaksConfig config) {
        if (!config.disableSweepingAttack) {
            return Component.literal("Sweeping Edge Required: ")
                .append(Component.literal("N/A").withStyle(ChatFormatting.DARK_GRAY));
        }
        return buildToggleLabel("Sweeping Edge Required", config.sweepingEdgeRequired);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
