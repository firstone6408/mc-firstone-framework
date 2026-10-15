package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reads the model and the two list widgets of a pack screen (Pack Manager tests)
 */
@Mixin(PackSelectionScreen.class)
public interface PackScreenAccessor {

    /**
     * Returns the screen's model, which holds the two pack lists
     *
     * @return {@code model}
     */
    @Accessor("model")
    PackSelectionModel devtest$getModel();

    /**
     * Returns the Available list widget (left)
     *
     * @return {@code availablePackList}
     */
    @Accessor("availablePackList")
    TransferableSelectionList devtest$getAvailablePackList();

    /**
     * Returns the Selected list widget (right)
     *
     * @return {@code selectedPackList}
     */
    @Accessor("selectedPackList")
    TransferableSelectionList devtest$getSelectedPackList();

    /**
     * Stops watching the pack folder (the game does it when the screen closes with Done or Escape; a test that
     * leaves a screen otherwise must do it, or the system runs out of folder watchers)
     */
    @Invoker("closeWatcher")
    void devtest$closeWatcher();
}
