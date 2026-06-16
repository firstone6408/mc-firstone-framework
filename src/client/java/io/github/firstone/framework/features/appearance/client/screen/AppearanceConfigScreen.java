package io.github.firstone.framework.features.appearance.client.screen;

import io.github.firstone.framework.features.appearance.AppearanceConfig;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Settings screen of the Appearance feature
 *
 * <p>Split into 2 sections:</p>
 * <ul>
 *   <li><b>Game Icon</b> — cycle through the PNGs in the icons folder, or use the default</li>
 *   <li><b>Window Title</b> — type a title, or leave it empty to use the default</li>
 * </ul>
 *
 * <p>Icon and title changes both apply immediately</p>
 */
public class AppearanceConfigScreen extends Screen {

    private static final int TITLE_Y       = 8;
    private static final int BUTTON_WIDTH  = 260;
    private static final int BUTTON_HEIGHT = 20;
    private static final float SMALL_SCALE = 0.75f;

    private final Screen parent;

    private List<String> icons;
    private int iconIndex;
    private Button iconButton;
    private EditBox titleInput;

    /**
     * Creates the Appearance settings screen
     *
     * @param parent previous screen to return to when closed
     */
    public AppearanceConfigScreen(Screen parent) {
        super(Component.literal("Appearance"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AppearanceConfig config = AppearanceFeature.getConfig();
        icons = AppearanceFeature.getAvailableIcons();

        iconIndex = -1;
        for (int i = 0; i < icons.size(); i++) {
            if (icons.get(i).equals(config.selectedIcon)) {
                iconIndex = i;
                break;
            }
        }

        int cx   = (width - BUTTON_WIDTH) / 2;
        int midY = height / 2;

        // ===== Game Icon =====
        iconButton = Button.builder(buildIconLabel(), btn -> {
            cycleIcon();
            btn.setMessage(buildIconLabel());
        }).pos(cx, midY - 38).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
        addRenderableWidget(iconButton);

        // ===== Window Title =====
        titleInput = new EditBox(font, cx, midY + 18, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal("Window Title"));
        titleInput.setMaxLength(256);
        titleInput.setValue(config.windowTitle);
        titleInput.setHint(Component.literal("Leave empty for Minecraft default")
            .withStyle(ChatFormatting.DARK_GRAY));
        titleInput.setResponder(value -> {
            config.windowTitle = value;
            AppearanceFeature.saveConfig();
            minecraft.getWindow().setTitle(value);
        });
        addRenderableWidget(titleInput);

        // ===== Done =====
        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
            .pos((width - 100) / 2, height - 28)
            .size(100, BUTTON_HEIGHT)
            .build());
    }

    // ─── Icon helpers ─────────────────────────────────────────────────────

    /**
     * Cycles forward to the next icon
     *
     * <p>Does nothing if the icons folder is empty.
     * Cycles Default → first icon → ... → last icon → Default</p>
     */
    private void cycleIcon() {
        if (icons.isEmpty()) return;
        iconIndex++;
        if (iconIndex >= icons.size()) iconIndex = -1;

        AppearanceConfig config = AppearanceFeature.getConfig();
        config.selectedIcon = iconIndex < 0 ? "" : icons.get(iconIndex);
        AppearanceFeature.saveConfig();
        AppearanceFeature.applyIcon(minecraft);
    }

    /**
     * Builds the icon button text showing the selected file name
     *
     * @return colored component
     */
    private Component buildIconLabel() {
        Component value = iconIndex < 0
            ? Component.literal("Default").withStyle(ChatFormatting.GRAY)
            : Component.literal(icons.get(iconIndex)).withStyle(ChatFormatting.YELLOW);
        return Component.literal("Game Icon: ").append(value);
    }

    // ─── Rendering ────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawCenteredString(font, title, width / 2, TITLE_Y, 0xFFFFFF);

        int midY = height / 2;

        drawSectionHeader(g, "Game Icon", midY - 55);
        drawSmall(g, Component.literal("Place PNG files in: config/firstone-framework/appearance/icons/")
            .withStyle(ChatFormatting.DARK_GRAY), midY - 44);

        drawSectionHeader(g, "Window Title", midY + 2);
    }

    /**
     * Draws a gold section header
     *
     * @param g     GuiGraphics
     * @param label section name
     * @param y     Y position
     */
    private void drawSectionHeader(GuiGraphics g, String label, int y) {
        g.drawCenteredString(font,
            Component.literal(label).withStyle(ChatFormatting.GOLD),
            width / 2, y, 0xFFFFFF);
    }

    /**
     * Draws small text at {@value #SMALL_SCALE}x, centered on the screen
     *
     * @param g    GuiGraphics
     * @param text component to show
     * @param y    Y position in normal screen space
     */
    private void drawSmall(GuiGraphics g, Component text, int y) {
        g.pose().pushPose();
        g.pose().translate(width / 2.0, y, 0);
        g.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        g.drawCenteredString(font, text, 0, 0, 0xFFFFFF);
        g.pose().popPose();
    }

    // ─── Events ──────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
