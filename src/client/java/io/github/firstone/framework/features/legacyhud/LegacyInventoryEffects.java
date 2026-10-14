package io.github.firstone.framework.features.legacyhud;

import com.google.common.collect.Ordering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Collection;
import java.util.List;

/**
 * 1.7.10 effect list of the inventory screens ({@code InventoryEffectRenderer})
 *
 * <p>When the inventory (or the creative inventory) opens while the player has effects, 1.7.10 moved the window
 * to the right ({@code guiLeft = 160 + (width - xSize - 200) / 2}) and listed the effects on its left, each with
 * its icon, name and time. The decision was made when the screen opened ({@code initGui}).</p>
 *
 * <p>The recipe book did not exist in 1.7.10: while it is open, the 1.21.1 layout is kept (window next to the
 * book, effects on the right).</p>
 */
public final class LegacyInventoryEffects {

    /** 1.21.1 sprite of a large effect box (120×32, same look as the 1.7.10 box) */
    private static final ResourceLocation BACKGROUND_SPRITE =
        ResourceLocation.withDefaultNamespace("container/inventory/effect_background_large");

    /** Distance from the left edge of the list to the window ({@code guiLeft - 124}) */
    private static final int LIST_OFFSET = 124;

    /** Box width and height */
    private static final int BOX_WIDTH = 120;
    private static final int BOX_HEIGHT = 32;

    /** Space between boxes, squeezed when there are more than 5 effects ({@code 132 / (count - 1)}) */
    private static final int BOX_SPACING = 33;
    private static final int SQUEEZED_HEIGHT = 132;

    /** Text colors: name white, time gray */
    private static final int NAME_COLOR = 0xFFFFFF;
    private static final int TIME_COLOR = 0x7F7F7F;

    private LegacyInventoryEffects() {}

    /**
     * Tells whether a screen that is opening should use the 1.7.10 layout
     *
     * @param minecraft Minecraft instance
     * @return true if the "Effects on the Left" option is on and the player has at least one effect
     */
    public static boolean shouldMoveLeft(Minecraft minecraft) {
        return LegacyHudFeature.getConfig().legacyInventoryEffects
            && minecraft.player != null && !minecraft.player.getActiveEffects().isEmpty();
    }

    /**
     * Returns the 1.7.10 left position of the window when the effects are listed on its left
     *
     * @param width      screen width (GUI pixels)
     * @param imageWidth width of the window (176 for the inventory, 195 for the creative inventory)
     * @return the new {@code leftPos}
     */
    public static int leftPos(int width, int imageWidth) {
        return 160 + (width - imageWidth - 200) / 2;
    }

    /**
     * Draws the effect list on the left of the window
     *
     * <p>Called instead of {@code EffectRenderingInventoryScreen.renderEffects} while the screen uses the 1.7.10
     * layout. Same order as 1.21.1; the time is written like 1.7.10 ({@code 1:53}).</p>
     *
     * @param guiGraphics graphics of the current frame
     * @param minecraft   Minecraft instance
     * @param font        font of the screen
     * @param leftPos     left position of the window
     * @param topPos      top position of the window (the list starts there)
     */
    public static void render(GuiGraphics guiGraphics, Minecraft minecraft, Font font, int leftPos, int topPos) {
        Collection<MobEffectInstance> effects = minecraft.player.getActiveEffects();
        if (effects.isEmpty()) {
            return;
        }

        int x = leftPos - LIST_OFFSET;
        int y = topPos;
        int spacing = effects.size() > 5 ? SQUEEZED_HEIGHT / (effects.size() - 1) : BOX_SPACING;
        float tickRate = minecraft.level.tickRateManager().tickrate();
        List<MobEffectInstance> sorted = Ordering.natural().sortedCopy(effects);

        for (MobEffectInstance effect : sorted) {
            guiGraphics.blitSprite(BACKGROUND_SPRITE, x, y, BOX_WIDTH, BOX_HEIGHT);
            guiGraphics.blit(x + 6, y + 7, 0, 18, 18, minecraft.getMobEffectTextures().get(effect.getEffect()));
            guiGraphics.drawString(font, name(effect), x + 28, y + 6, NAME_COLOR);
            guiGraphics.drawString(font, duration(effect, tickRate), x + 28, y + 16, TIME_COLOR);
            y += spacing;
        }
    }

    /**
     * Returns the effect name with its level, as 1.21.1 writes it (e.g. "Speed II")
     *
     * @param effect the effect
     * @return the name, followed by the level from level II to X
     */
    private static Component name(MobEffectInstance effect) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
            name.append(CommonComponents.SPACE)
                .append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
        }
        return name;
    }

    /**
     * Returns the remaining time like 1.7.10 ({@code StringUtils.ticksToElapsedTime}: minutes and seconds,
     * e.g. {@code 1:53}, {@code 90:00})
     *
     * @param effect   the effect
     * @param tickRate current tick rate (20 normally), as 1.21.1 uses it
     * @return the time, or "∞" for effects without an end (1.21.1 only)
     */
    private static Component duration(MobEffectInstance effect, float tickRate) {
        if (effect.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        }
        int seconds = Mth.floor(effect.getDuration() / tickRate);
        return Component.literal(seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60);
    }

    /**
     * Implemented by {@code EffectRenderingInventoryScreen} (through a mixin) to remember whether the screen uses
     * the 1.7.10 layout
     */
    public interface Layout {

        /**
         * Tells whether the screen lists the effects on the left of its window
         *
         * @return true while the 1.7.10 layout is used
         */
        boolean legacyHud$effectsOnLeft();

        /**
         * Sets whether the screen lists the effects on the left of its window
         *
         * @param onLeft true for the 1.7.10 layout, false for the 1.21.1 layout
         */
        void legacyHud$setEffectsOnLeft(boolean onLeft);
    }
}
