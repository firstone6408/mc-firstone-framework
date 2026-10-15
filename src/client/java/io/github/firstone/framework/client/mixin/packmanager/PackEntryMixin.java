package io.github.firstone.framework.client.mixin.packmanager;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.packmanager.PackLists;
import io.github.firstone.framework.features.packmanager.preset.PresetGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The pack screens' own pack actions (turning a pack on or off, moving it one step), as Pack Manager needs them
 *
 * <p>Every way the player changes the Selected list ends in these methods of the screen's model:
 * {@code toggleSelection} (arrow click, Enter, Space) and {@code move} (arrow click, Shift+arrow key).</p>
 * <ul>
 *   <li>Search ({@link PackLists}): no moving up or down while the open pack screen is searching. A step moves a
 *       pack past its neighbour in the whole list, which may be hidden by the search, so the step would look like
 *       nothing happened; the game asks {@code canMoveUp}/{@code canMoveDown} before it shows the arrows and before
 *       a click or key moves a pack, so the arrows are simply not shown.</li>
 *   <li>Presets ({@link PresetGuard}): before a change leaves the preset in use, it is stopped and asked about; after
 *       "Change Packs" it is made again.</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel$EntryBase")
public abstract class PackEntryMixin {

    @Shadow
    protected abstract void toggleSelection();

    @Shadow
    protected abstract void move(int step);

    @ModifyReturnValue(method = {"canMoveUp", "canMoveDown"}, at = @At("RETURN"))
    private boolean packManager$noMovingWhileSearching(boolean canMove) {
        return canMove && !PackLists.isSearching();
    }

    @Inject(method = "toggleSelection", at = @At("HEAD"), cancellable = true)
    private void packManager$askBeforeToggle(CallbackInfo ci) {
        if (PresetGuard.intercept(this::toggleSelection)) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void packManager$askBeforeMove(int step, CallbackInfo ci) {
        if (PresetGuard.intercept(() -> move(step))) {
            ci.cancel();
        }
    }
}
