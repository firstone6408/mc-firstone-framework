package io.github.firstone.framework.features.animatium.client.screen;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Settings screen of the Animatium feature
 *
 * <p>Options are grouped into 2 categories:</p>
 * <ul>
 *   <li><b>Animation</b> — controls the various animations (toggles + FPS sliders)</li>
 *   <li><b>Combat</b> — hides effects and sounds added after 1.9</li>
 * </ul>
 *
 * <p>Supports scrolling when the content is taller than the screen.
 * All widgets are managed manually (not through {@code addRenderableWidget})
 * so the scroll offset can be controlled directly</p>
 */
public class AnimatiumConfigScreen extends Screen {

    private static final int TITLE_H    = 20;
    private static final int FOOTER_H   = 28;
    private static final int ROW_H      = 24;
    private static final int CATEGORY_H = 30;
    private static final int BUTTON_WIDTH  = 260;
    private static final int BUTTON_HEIGHT = 20;
    /** Maximum FPS supported by the sliders */
    private static final int MAX_FPS = 60;

    private final Screen parent;

    /** Entries in the scroll area (HeaderEntry or WidgetEntry) */
    private final List<Object> entries = new ArrayList<>();

    private int totalContentHeight;
    private double scrollOffset;

    /** Done button, outside the scroll area */
    private Button doneButton;

    /** Widget currently being dragged (for sliders) */
    private AbstractWidget activeWidget;

    /**
     * Creates the Animatium settings screen
     *
     * @param parent previous screen to return to when closed
     */
    public AnimatiumConfigScreen(Screen parent) {
        super(Component.literal("Animatium - Legacy Animation"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries.clear();
        scrollOffset = 0;
        activeWidget = null;

        AnimatiumConfig config = AnimatiumFeature.getConfig();

        // ===== Category: Animation =====
        addHeader("Animation");

        addToggle("No Re-equip Animation", config.noReequipAnimation, v -> {
            config.noReequipAnimation = v;
            AnimatiumFeature.saveConfig();
        });
        addToggle("Old Sneak Animation", config.oldSneakAnimation, v -> {
            config.oldSneakAnimation = v;
            AnimatiumFeature.saveConfig();
        });
        addToggle("Old Item Drop Animation", config.oldItemDropAnimation, v -> {
            config.oldItemDropAnimation = v;
            AnimatiumFeature.saveConfig();
        });
        addSlider("Walk Animation", config.legacyWalkAnimationFps, fps -> config.legacyWalkAnimationFps = fps);
        addSlider("Head Rotation",  config.legacyHeadRotationFps,  fps -> config.legacyHeadRotationFps  = fps);
        addSlider("Body Rotation",  config.legacyBodyRotationFps,  fps -> config.legacyBodyRotationFps  = fps);

        // ===== Category: Combat =====
        addHeader("Combat");

        addToggle("No Sweep Effect", config.noSweepEffect, v -> {
            config.noSweepEffect = v;
            AnimatiumFeature.saveConfig();
        });
        addToggle("No Damage Indicator", config.noDamageIndicatorParticle, v -> {
            config.noDamageIndicatorParticle = v;
            AnimatiumFeature.saveConfig();
        });
        addToggle("No Attack Sounds", config.noAttackSounds, v -> {
            config.noAttackSounds = v;
            AnimatiumFeature.saveConfig();
        });

        // compute the total content height
        int total = 4;
        for (Object e : entries) total += entryH(e);
        total += 4;
        totalContentHeight = total;

        // Done button pinned to the bottom
        doneButton = Button.builder(Component.literal("Done"), btn -> onClose())
                .pos((width - 100) / 2, height - FOOTER_H + 4)
                .size(100, BUTTON_HEIGHT)
                .build();
    }

    // ─── Helper methods for init ───────────────────────────────────────

    private void addHeader(String title) {
        entries.add(new HeaderEntry(Component.literal(title).withStyle(ChatFormatting.GOLD)));
    }

    private void addToggle(String label, boolean initialValue, java.util.function.Consumer<Boolean> onChange) {
        boolean[] state = { initialValue };
        Button btn = Button.builder(buildToggleLabel(label, state[0]), b -> {
            state[0] = !state[0];
            onChange.accept(state[0]);
            b.setMessage(buildToggleLabel(label, state[0]));
        }).pos(0, 0).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
        entries.add(new WidgetEntry(btn));
    }

    private void addSlider(String label, int currentFps, IntConsumer onChange) {
        entries.add(new WidgetEntry(new FpsSlider(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, label, currentFps, onChange)));
    }

    // ─── Entry types ────────────────────────────────────────────────────

    private record HeaderEntry(Component label) {}
    private record WidgetEntry(AbstractWidget widget) {}

    private int entryH(Object entry) {
        return entry instanceof HeaderEntry ? CATEGORY_H : ROW_H;
    }

    // ─── Scroll helpers ──────────────────────────────────────────────────

    private int contentTop()    { return TITLE_H; }
    private int contentBottom() { return height - FOOTER_H; }
    private int maxScroll()     { return Math.max(0, totalContentHeight - (contentBottom() - contentTop())); }

    // ─── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);

        // title
        g.drawCenteredString(font, this.title, width / 2, 6, 0xFFFFFF);

        // content area borders
        int cTop    = contentTop();
        int cBottom = contentBottom();
        g.fill(0, cTop - 1, width, cTop,    0x55FFFFFF);
        g.fill(0, cBottom,  width, cBottom + 1, 0x55FFFFFF);

        int scroll   = (int) scrollOffset;
        int contentX = (width - BUTTON_WIDTH) / 2;
        boolean mouseInContent = my >= cTop && my < cBottom;

        g.enableScissor(0, cTop, width, cBottom);

        int y = cTop + 4 - scroll;
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            int h = entryH(entry);

            if (y + h > cTop && y < cBottom) {
                if (entry instanceof HeaderEntry header) {
                    // separator line before a header (except the first header)
                    if (i > 0) {
                        g.fill(10, y + 4, width - 10, y + 5, 0x44FFFFFF);
                    }
                    g.drawString(font, header.label(), 14, y + (h - 9) / 2 + (i > 0 ? 3 : 0), 0xFFFFAA00);
                } else if (entry instanceof WidgetEntry wr) {
                    AbstractWidget w = wr.widget();
                    w.setX(contentX);
                    w.setY(y + (h - BUTTON_HEIGHT) / 2);
                    w.render(g, mouseInContent ? mx : -1, mouseInContent ? my : -1, delta);
                }
            }
            y += h;
        }

        g.disableScissor();

        // scrollbar (shown when the content is taller than the screen)
        if (maxScroll() > 0) {
            renderScrollbar(g, cTop, cBottom, scroll);
        }

        // Done button
        doneButton.render(g, mx, my, delta);
    }

    /**
     * Draws the scrollbar on the right side of the content area
     *
     * @param g      GuiGraphics
     * @param cTop   top edge of the content area
     * @param cBottom bottom edge of the content area
     * @param scroll  current scroll value (pixels)
     */
    private void renderScrollbar(GuiGraphics g, int cTop, int cBottom, int scroll) {
        int trackH = cBottom - cTop;
        float ratio = Math.min(1f, (float) trackH / totalContentHeight);
        int barH = Math.max(20, (int) (trackH * ratio));
        int barY = cTop + (int) ((trackH - barH) * (scroll / (float) maxScroll()));
        int x = width - 6;
        g.fill(x, cTop,  x + 4, cBottom,    0x33FFFFFF); // track
        g.fill(x, barY,  x + 4, barY + barH, 0xAAFFFFFF); // thumb
    }

    // ─── Event handling ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        activeWidget = null;

        if (doneButton.isMouseOver(mx, my) && doneButton.mouseClicked(mx, my, button)) return true;

        if (my >= contentTop() && my < contentBottom()) {
            for (Object entry : entries) {
                if (entry instanceof WidgetEntry wr) {
                    AbstractWidget w = wr.widget();
                    if (w.isMouseOver(mx, my) && w.mouseClicked(mx, my, button)) {
                        activeWidget = w;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (activeWidget != null) return activeWidget.mouseDragged(mx, my, button, dx, dy);
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (activeWidget != null) {
            activeWidget.mouseReleased(mx, my, button);
            activeWidget = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (my >= contentTop() && my < contentBottom()) {
            scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - scrollY * 10));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; } // Escape
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /**
     * Builds the toggle button text with colored ON/OFF
     *
     * @param label option name
     * @param value current state
     * @return the component shown on the button
     */
    private Component buildToggleLabel(String label, boolean value) {
        Component status = value
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.RED);
        return Component.literal(label + ": ").append(status);
    }

    // ─── FpsSlider ───────────────────────────────────────────────────────

    /**
     * Slider for choosing the FPS of a legacy animation
     *
     * <p>Far left → OFF (feature disabled),
     * moving right → 1–{@value #MAX_FPS} FPS</p>
     */
    private class FpsSlider extends AbstractSliderButton {

        private final String prefix;
        private final IntConsumer onFpsChange;

        /**
         * Creates an FPS slider
         *
         * @param x           X position
         * @param y           Y position
         * @param width       width
         * @param height      height
         * @param prefix      label shown before the value
         * @param currentFps  current FPS value (0 = OFF)
         * @param onFpsChange callback receiving the new fps when the slider changes
         */
        FpsSlider(int x, int y, int width, int height, String prefix, int currentFps, IntConsumer onFpsChange) {
            super(x, y, width, height, Component.literal(""), currentFps / (double) MAX_FPS);
            this.prefix = prefix;
            this.onFpsChange = onFpsChange;
            updateMessage();
        }

        private int getFps() {
            return (int) Math.round(this.value * MAX_FPS);
        }

        @Override
        protected void updateMessage() {
            int fps = getFps();
            Component val = fps == 0
                    ? Component.literal("OFF").withStyle(ChatFormatting.RED)
                    : Component.literal(fps + " FPS").withStyle(ChatFormatting.YELLOW);
            setMessage(Component.literal(prefix + ": ").append(val));
        }

        @Override
        protected void applyValue() {
            onFpsChange.accept(getFps());
            AnimatiumFeature.saveConfig();
        }
    }
}
