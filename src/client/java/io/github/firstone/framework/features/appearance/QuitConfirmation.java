package io.github.firstone.framework.features.appearance;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * "Quit the game?" dialog shown when the window's close button (X) is pressed
 *
 * <p>{@code ConfirmQuitMixin} passes every close request of the window to {@link #interceptClose}. With "Confirm
 * Quit" on, the request is cancelled and this dialog is drawn on top of the open screen; Esc, Enter and Space mean
 * Cancel. The open screen is never replaced, because replacing it runs its {@code removed()} logic (the creative
 * inventory, for example, stops syncing items), so the dialog is drawn through Fabric screen events, which also block
 * the screen's input while it is open. In game, an empty screen is opened to hold it, which pauses singleplayer.</p>
 *
 * <p>The in-game "Quit Game" button never asks. During the loading overlay the window closes without asking,
 * because no screen takes input then.</p>
 */
public final class QuitConfirmation {

    private static final int PANEL_WIDTH = 170;
    private static final int PANEL_HEIGHT = 52;
    private static final int BUTTON_WIDTH = 76;

    /** Depth of the dialog, above the items and tooltips of the screen below */
    private static final int Z_LEVEL = 500;

    /** Panel color: the dark tone vanilla uses behind its menus */
    private static final int PANEL_COLOR = 0xE0101010;

    private static final Component TITLE = Component.translatable("firstone-framework.appearance.quit_dialog");

    /** Quit and Cancel buttons while the dialog is open, otherwise null */
    @Nullable
    private static List<Button> buttons;

    private QuitConfirmation() {}

    /**
     * Registers the events that draw the dialog and route input to it; they do nothing while it is closed
     *
     * <p>Called once from {@link AppearanceFeature#initializeClient()}.</p>
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            ScreenEvents.afterRender(screen).register(QuitConfirmation::render);
            ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !isOpen() || click(x, y, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !isOpen());
            ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !isOpen());
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, mods) -> !isOpen() || keyPress(key));
        });
        // the game may close every screen while the dialog is open (e.g. when a world finishes loading)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isOpen() && client.screen == null) {
                client.setScreen(new HostScreen());
            }
        });
    }

    /**
     * Decides what happens to a close request of the game window
     *
     * <p>Called by {@code ConfirmQuitMixin} when the window reports that it should close.</p>
     *
     * @param minecraft the game instance
     * @return true if the request was taken over (the game keeps running and the dialog is shown), false to quit
     */
    public static boolean interceptClose(Minecraft minecraft) {
        if (!AppearanceFeature.getConfig().confirmQuit || minecraft.getOverlay() != null) {
            return false;
        }
        GLFW.glfwSetWindowShouldClose(minecraft.getWindow().getWindow(), false);
        if (!isOpen()) {
            Button quit = Button.builder(Component.translatable("menu.quit"), button -> minecraft.stop())
                .width(BUTTON_WIDTH).build();
            Button cancel = Button.builder(CommonComponents.GUI_CANCEL, button -> cancel(minecraft))
                .width(BUTTON_WIDTH).build();
            cancel.setFocused(true); // white border: the default answer
            buttons = List.of(quit, cancel);
            if (minecraft.screen == null) {
                minecraft.setScreen(new HostScreen());
            }
        }
        return true;
    }

    /**
     * Tells whether the dialog is open
     *
     * <p>Also used by {@code ConfirmQuitHoverMixin}, which hides the mouse from the screen below while it is.</p>
     *
     * @return true while the dialog is open
     */
    public static boolean isOpen() {
        return buttons != null;
    }

    /** Closes the dialog, and the empty screen that held it */
    private static void cancel(Minecraft minecraft) {
        buttons = null;
        if (minecraft.screen instanceof HostScreen) {
            minecraft.setScreen(null);
        }
    }

    /**
     * Draws the dialog like a vanilla menu: what is below is blurred (the "Menu Background Blurriness" option) and
     * darkened, then a dark panel between vanilla separator lines holds the question and the buttons
     *
     * <p>The screen below gets a mouse position outside the window ({@code ConfirmQuitHoverMixin}), so the real
     * position is read from the mouse here.</p>
     */
    private static void render(Screen screen, GuiGraphics graphics, int ignoredX, int ignoredY, float partialTick) {
        List<Button> dialog = buttons;
        if (dialog == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        int mouseX = (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
        int x = (screen.width - PANEL_WIDTH) / 2;
        int y = (screen.height - PANEL_HEIGHT) / 2;
        boolean inWorld = minecraft.level != null;

        graphics.flush(); // blur what is drawn so far, as Screen.renderBlurredBackground does
        minecraft.gameRenderer.processBlurEffect(partialTick);
        minecraft.getMainRenderTarget().bindWrite(false);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, Z_LEVEL);
        screen.renderTransparentBackground(graphics);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL_COLOR);
        RenderSystem.enableBlend();
        graphics.blit(inWorld ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR,
            x, y - 2, 0.0F, 0.0F, PANEL_WIDTH, 2, 32, 2);
        graphics.blit(inWorld ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR,
            x, y + PANEL_HEIGHT, 0.0F, 0.0F, PANEL_WIDTH, 2, 32, 2);
        RenderSystem.disableBlend();
        graphics.drawCenteredString(minecraft.font, TITLE, screen.width / 2, y + 8, 0xFFFFFFFF);
        for (int i = 0; i < dialog.size(); i++) {
            Button button = dialog.get(i);
            button.setPosition(screen.width / 2 - BUTTON_WIDTH - 3 + i * (BUTTON_WIDTH + 6), y + PANEL_HEIGHT - 26);
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.pose().popPose();
    }

    /** Presses the dialog button under the mouse; the click never reaches the screen below */
    private static boolean click(double mouseX, double mouseY, int mouseButton) {
        List<Button> dialog = buttons;
        if (dialog != null) {
            for (Button button : dialog) {
                if (button.mouseClicked(mouseX, mouseY, mouseButton)) {
                    break;
                }
            }
        }
        return false;
    }

    /** Esc, Enter or Space cancels; no key reaches the screen below */
    private static boolean keyPress(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
            || key == GLFW.GLFW_KEY_SPACE) {
            cancel(Minecraft.getInstance());
        }
        return false;
    }

    /** Empty screen that holds the dialog in game; its background is drawn by the dialog */
    private static final class HostScreen extends Screen {
        private HostScreen() {
            super(TITLE);
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }
    }
}
