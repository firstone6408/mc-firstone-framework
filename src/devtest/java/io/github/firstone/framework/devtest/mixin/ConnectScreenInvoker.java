package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Makes a connecting screen without connecting anywhere, so a test can look at it; part of the dev test mod only
 */
@Mixin(ConnectScreen.class)
public interface ConnectScreenInvoker {

    /**
     * Creates the screen ({@code ConnectScreen}'s private constructor)
     *
     * @param parent screen to go back to
     * @param title  the screen's title
     * @return the screen, not connecting
     */
    @Invoker("<init>")
    static ConnectScreen devtest$create(Screen parent, Component title) {
        throw new AssertionError("replaced by the mixin");
    }
}
