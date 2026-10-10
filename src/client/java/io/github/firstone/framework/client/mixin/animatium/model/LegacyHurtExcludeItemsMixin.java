package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.model.LegacyHurtTint;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the 1.7.10 red hurt pass off held items: 1.7.10 drew them in {@code renderEquippedItems}, outside the red
 * pass ({@link LegacyHurtTint#suspend()} while {@code ItemRenderer.render} draws an item)
 */
@Mixin(ItemRenderer.class)
public class LegacyHurtExcludeItemsMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void animatium$suspendHurtCapture(CallbackInfo ci) {
        LegacyHurtTint.suspend();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void animatium$resumeHurtCapture(CallbackInfo ci) {
        LegacyHurtTint.resume();
    }
}
