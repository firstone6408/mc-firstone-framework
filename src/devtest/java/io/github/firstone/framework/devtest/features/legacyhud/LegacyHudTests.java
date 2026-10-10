package io.github.firstone.framework.devtest.features.legacyhud;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.mixin.ContainerScreenAccessor;
import io.github.firstone.framework.features.legacyhud.LegacyHudFeature;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.function.Supplier;

/**
 * Tests of Legacy HUD: hidden effect icons and the 1.7.10 inventory position with effects
 *
 * <p>The inventory position is compared with the 1.7.10 formula ({@code InventoryEffectRenderer.initGui}:
 * {@code guiLeft = 160 + (width - xSize - 200) / 2}) and with 1.21.1
 * ({@code RecipeBookComponent.updateScreenPosition}); the screen's size comes from the running game, so any window
 * size and GUI scale works. Effect icons are checked by comparing the top-right corner of the screen with and without
 * an effect. Inventory screenshots are only saved: the player model looks toward the mouse and items under it show a
 * tooltip.</p>
 */
public final class LegacyHudTests implements FeatureTests {

    /** Width of the inventory panel */
    private static final int INVENTORY_WIDTH = 176;

    @Override
    public String id() {
        return "legacy_hud";
    }

    @Override
    public List<Object> configs() {
        return List.of(LegacyHudFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        return List.of(
            TestCase.of("Inventory: no effects keeps the 1.21.1 position", this::inventoryNoEffects),
            TestCase.of("Inventory: with effects it moves to the 1.7.10 position; off: 1.21.1", this::inventoryEffects),
            TestCase.of("Inventory: the open recipe book keeps the 1.21.1 position", this::recipeBookOpen),
            TestCase.of("Inventory: the recipe book button moves it back and forth", this::recipeBookButton),
            TestCase.of("Creative inventory: with effects it moves to the 1.7.10 position; off: 1.21.1",
                this::creativeInventory),
            TestCase.of("HUD: effect icons are hidden in the top-right corner; off: shown", this::effectIcons)
        );
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private static void giveEffect(TestContext t) {
        // Haste: an effect that changes nothing on screen (Speed changes the field of view); its particles must be
        // on, as hiding them also hides the effect's icon
        t.command("effect give @a minecraft:haste 1000 0 false");
        t.waitUntil("the client has the effect", () -> !t.player().getActiveEffects().isEmpty(), 40);
    }

    private static void setRecipeBookOpen(TestContext t, boolean open) {
        t.player().getRecipeBook().setOpen(RecipeBookType.CRAFTING, open);
    }

    /** Opens a container screen and returns its panel's x position */
    private static int openAndGetLeft(TestContext t, Supplier<Screen> screen) {
        t.openScreen(screen);
        t.require(t.mc().screen instanceof AbstractContainerScreen<?>, "The inventory did not open");
        return ((ContainerScreenAccessor) t.mc().screen).devtest$getLeftPos();
    }

    /** Survival: in creative the inventory key (and {@code InventoryScreen}) opens the creative inventory */
    private static void survival(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
    }

    private static int screenWidth(TestContext t) {
        return t.mc().getWindow().getGuiScaledWidth();
    }

    /** 1.7.10 {@code guiLeft} with effects */
    private static int legacyLeft(int width) {
        return 160 + (width - INVENTORY_WIDTH - 200) / 2;
    }

    /** 1.21.1 position: centered, or beside the open recipe book when the window is wide enough */
    private static int vanillaLeft(int width, boolean recipeBookOpen) {
        return recipeBookOpen && width >= 379
            ? 177 + (width - INVENTORY_WIDTH - 200) / 2
            : (width - INVENTORY_WIDTH) / 2;
    }

    private static InventoryScreen inventory(TestContext t) {
        return new InventoryScreen(t.player());
    }

    // ---- inventory ------------------------------------------------------------------------------------------------

    private void inventoryNoEffects(TestContext t) {
        survival(t);
        setRecipeBookOpen(t, false);
        int width = screenWidth(t);
        t.check("no effects: inventory x", openAndGetLeft(t, () -> inventory(t))).isEqualTo(vanillaLeft(width, false));
    }

    private void inventoryEffects(TestContext t) {
        survival(t);
        setRecipeBookOpen(t, false);
        giveEffect(t);
        int width = screenWidth(t);
        t.check("on: inventory x", openAndGetLeft(t, () -> inventory(t))).isEqualTo(legacyLeft(width));
        t.saveScreenshot("inventory_effects_on_the_left");
        t.closeScreen();
        LegacyHudFeature.getConfig().legacyInventoryEffects = false;
        t.check("off: inventory x", openAndGetLeft(t, () -> inventory(t))).isEqualTo(vanillaLeft(width, false));
        t.saveScreenshot("inventory_effects_off");
    }

    private void recipeBookOpen(TestContext t) {
        survival(t);
        giveEffect(t);
        setRecipeBookOpen(t, true);
        int width = screenWidth(t);
        t.check("recipe book open: inventory x", openAndGetLeft(t, () -> inventory(t)))
            .isEqualTo(vanillaLeft(width, true));
        t.closeScreen();
        setRecipeBookOpen(t, false);
    }

    /** Presses the recipe book button of the open inventory and returns the new panel x position */
    private static int pressRecipeButton(TestContext t) {
        Screen screen = t.mc().screen;
        t.require(screen != null, "The inventory is not open");
        ImageButton button = screen.children().stream().filter(child -> child instanceof ImageButton)
            .map(child -> (ImageButton) child).findFirst().orElse(null);
        t.require(button != null, "The inventory has no recipe book button");
        t.onClientThread(() -> {
            button.onPress();
            return null;
        });
        t.ticks(2);
        return ((ContainerScreenAccessor) screen).devtest$getLeftPos();
    }

    private void recipeBookButton(TestContext t) {
        survival(t);
        setRecipeBookOpen(t, false);
        giveEffect(t);
        int width = screenWidth(t);
        t.check("start: inventory x", openAndGetLeft(t, () -> inventory(t))).isEqualTo(legacyLeft(width));
        t.check("recipe book opened: inventory x", pressRecipeButton(t)).isEqualTo(vanillaLeft(width, true));
        t.check("recipe book closed again: inventory x", pressRecipeButton(t)).isEqualTo(legacyLeft(width));
        t.closeScreen();
        setRecipeBookOpen(t, false);
    }

    private void creativeInventory(TestContext t) {
        t.gameMode(GameType.CREATIVE);
        giveEffect(t);
        int width = screenWidth(t);
        Supplier<Screen> creative = () -> new CreativeModeInventoryScreen(t.player(),
            t.player().connection.enabledFeatures(), true);
        int left = openAndGetLeft(t, creative);
        int imageWidth = ((ContainerScreenAccessor) t.mc().screen).devtest$getImageWidth();
        t.check("on: creative inventory x", left).isEqualTo(160 + (width - imageWidth - 200) / 2);
        t.saveScreenshot("creative_inventory_effects_on_the_left");
        t.closeScreen();
        LegacyHudFeature.getConfig().legacyInventoryEffects = false;
        t.check("off: creative inventory x", openAndGetLeft(t, creative)).isEqualTo((width - imageWidth) / 2);
    }

    // ---- HUD ------------------------------------------------------------------------------------------------------

    /** Captures the frame looking straight down at the floor (nothing moves there) */
    private static NativeImage frameLookingDown(TestContext t) {
        t.teleport(4.5, 1.0, 4.5, 0.0F, 90.0F);
        t.ticks(5);
        return t.captureFrame();
    }

    /** Share of differing pixels in the top-right corner, where 1.21.1 draws effect icons */
    private static double topRightDiffers(NativeImage a, NativeImage b) {
        int width = a.getWidth() / 5;
        int height = a.getHeight() / 8;
        return Images.differingPercent(a, b, a.getWidth() - width, 0, width, height);
    }

    private void effectIcons(TestContext t) {
        // effects are cleared before every test; make sure the client knows
        t.waitUntil("the client has no effects", () -> t.player().getActiveEffects().isEmpty(), 40);
        try (NativeImage none = frameLookingDown(t)) {
            giveEffect(t);
            try (NativeImage hidden = frameLookingDown(t)) {
                t.saveFrame("hud_effect_icons_hidden", hidden);
                t.check("on: top-right corner differs from no effects (%)", topRightDiffers(none, hidden))
                    .atMost(0.5);
            }
            LegacyHudFeature.getConfig().hideEffectIcons = false;
            try (NativeImage shown = frameLookingDown(t)) {
                t.saveFrame("hud_effect_icons_shown", shown);
                t.check("off: top-right corner differs from no effects (%)", topRightDiffers(none, shown))
                    .atLeast(3.0);
            }
        }
    }
}
