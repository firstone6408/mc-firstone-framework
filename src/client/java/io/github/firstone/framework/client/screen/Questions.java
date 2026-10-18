package io.github.firstone.framework.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Asks the player a yes/no question with the game's confirmation screen, then goes back to the screen that asked
 *
 * <p>Both answers go back to that screen first; on yes the action runs after that, so it may open another screen.
 * Used by every screen of the mod that asks before it deletes, replaces or throws away something.</p>
 */
public final class Questions {

    private Questions() {}

    /**
     * Asks with the game's Yes and No buttons
     *
     * @param back    screen to go back to
     * @param title   the question
     * @param message what happens on yes
     * @param onYes   runs after going back, on yes
     */
    public static void ask(Screen back, Component title, Component message, Runnable onYes) {
        ask(back, title, message, CommonComponents.GUI_YES, CommonComponents.GUI_NO, onYes);
    }

    /**
     * Asks with a yes button that names what it does and a Cancel button
     *
     * @param back    screen to go back to
     * @param title   the question
     * @param message what happens on yes
     * @param yes     text of the yes button (e.g. "Discard")
     * @param onYes   runs after going back, on yes
     */
    public static void ask(Screen back, Component title, Component message, Component yes, Runnable onYes) {
        ask(back, title, message, yes, CommonComponents.GUI_CANCEL, onYes);
    }

    /**
     * Asks with two named buttons
     *
     * @param back    screen to go back to
     * @param title   the question
     * @param message what happens on yes
     * @param yes     text of the yes button
     * @param no      text of the no button
     * @param onYes   runs after going back, on yes
     */
    public static void ask(Screen back, Component title, Component message, Component yes, Component no,
                           Runnable onYes) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(back);
            if (confirmed) {
                onYes.run();
            }
        }, title, message, yes, no));
    }
}
