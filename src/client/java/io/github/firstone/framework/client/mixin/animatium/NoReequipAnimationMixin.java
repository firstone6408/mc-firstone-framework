package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that removes the attack-cooldown hand animation, like versions before 1.9
 *
 * <p>The animation is split into 3 phases:</p>
 * <ul>
 *   <li><b>DOWN phase</b> ({@code switchInProgress}): fully vanilla-controlled — the old item lowers at vanilla speed</li>
 *   <li><b>RISE phase</b> ({@code riseActive}): we drive {@code mainHandHeight} with our own timer
 *       so the rise is not interrupted by the attack cooldown (the main reason the equip animation got cancelled)</li>
 *   <li><b>IDLE phase</b>: forces height = 1.0f to remove the attack-cooldown animation</li>
 * </ul>
 *
 * <p>Root cause of the equip bug: when the player attacks right after vanilla switches the item,
 * {@code attackStrengthScale} drops so vanilla does not raise the height → the old rise timer
 * ran out before the rise finished → we forced 1.0f → the animation looked cancelled.
 * Driving the rise with our own timer fixes this</p>
 *
 * <p>Target: {@link ItemInHandRenderer#tick()}</p>
 */
@Mixin(ItemInHandRenderer.class)
public class NoReequipAnimationMixin {

    /** Minecraft instance, used to access the current player */
    @Shadow private Minecraft minecraft;

    /** Item the renderer is rendering (may differ from the player's item during the animation) */
    @Shadow private ItemStack mainHandItem;

    /** Current main-hand height */
    @Shadow private float mainHandHeight;

    /** Main-hand height of the previous frame, used for interpolation */
    @Shadow private float oMainHandHeight;

    /** Off-hand height (shadow currently disabled) */
    // @Shadow private float offHandHeight;

    /** Off-hand height of the previous frame (shadow currently disabled) */
    // @Shadow private float oOffHandHeight;

    /** Item the renderer was rendering before tick runs, used to detect justSwitched */
    @Unique private ItemStack animatium$prevMainHandItem = ItemStack.EMPTY;

    /** true = we are driving the rise animation (not vanilla) */
    @Unique private boolean animatium$riseActive = false;

    /** Timer of the rise phase (0.0 → 1.0 at 0.4f/tick = 3 ticks, like vanilla) */
    @Unique private float animatium$riseTimer = 0.0f;

    /**
     * Records the item being rendered before tick runs
     *
     * @param ci CallbackInfo of the injection
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo ci) {
        if (!AnimatiumFeature.getConfig().noReequipAnimation) return;
        animatium$prevMainHandItem = this.mainHandItem;
    }

    /**
     * Handles the animation after the vanilla tick finishes
     *
     * <p>Main logic:</p>
     * <ol>
     *   <li>DOWN phase ({@code switchInProgress}): vanilla in control — reset the rise if a new switch starts</li>
     *   <li>RISE start ({@code justSwitched}): vanilla just swapped the item at height 0 → start our timer</li>
     *   <li>RISE ({@code riseActive}): drive {@code mainHandHeight} with our timer (independent of the cooldown)</li>
     *   <li>IDLE: force height = 1.0f to remove the attack-cooldown animation</li>
     * </ol>
     *
     * @param ci CallbackInfo of the injection
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(CallbackInfo ci) {
        if (!AnimatiumFeature.getConfig().noReequipAnimation) {
            animatium$riseActive = false;
            return;
        }

        LocalPlayer player = this.minecraft.player;
        if (player == null) return;

        ItemStack currentItem = player.getMainHandItem();

        // only compare the item type, ignore durability
        boolean itemsMatch = ItemStack.isSameItem(this.mainHandItem, currentItem);

        // vanilla is lowering the hand
        boolean switchInProgress = !itemsMatch;

        // the item really was just switched
        boolean justSwitched =
                !ItemStack.isSameItem(animatium$prevMainHandItem, currentItem)
                && itemsMatch;

        if (switchInProgress) {
            animatium$riseActive = false;

        } else if (justSwitched) {
            animatium$riseActive = true;
            animatium$riseTimer = 0.0f;
            mainHandHeight = 0.0f;

        } else if (animatium$riseActive) {
            animatium$riseTimer = Math.min(1.0f, animatium$riseTimer + 0.4f);
            mainHandHeight = animatium$riseTimer;

            if (animatium$riseTimer >= 1.0f) {
                animatium$riseActive = false;
            }

        } else {
            // no animation at all
            animatium$riseActive = false;
            animatium$riseTimer = 1.0f;

            mainHandHeight = 1.0f;
            oMainHandHeight = 1.0f;
        }

        // offHandHeight = 1.0f;
        // oOffHandHeight = 1.0f;
    }
}
