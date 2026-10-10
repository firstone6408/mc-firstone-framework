package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.model.LegacyHurtTint;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the 1.7.10 red hurt pass off blocks entities carry, such as an enderman's block or a snow golem's pumpkin
 * ({@link LegacyHurtTint#suspend()} while {@code BlockRenderDispatcher.renderSingleBlock} draws a block)
 */
@Mixin(BlockRenderDispatcher.class)
public class LegacyHurtExcludeBlocksMixin {

    @Inject(method = "renderSingleBlock", at = @At("HEAD"))
    private void animatium$suspendHurtCapture(CallbackInfo ci) {
        LegacyHurtTint.suspend();
    }

    @Inject(method = "renderSingleBlock", at = @At("RETURN"))
    private void animatium$resumeHurtCapture(CallbackInfo ci) {
        LegacyHurtTint.resume();
    }
}
