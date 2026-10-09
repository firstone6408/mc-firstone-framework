package io.github.firstone.framework.client.mixin.animatium.model;

import io.github.firstone.framework.features.animatium.LegacyHurtTint;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the 1.7.10 red hurt pass off things 1.7.10 did not tint
 *
 * <p>1.7.10 drew held items, carried blocks, blocks and skulls on heads, the cape and stuck arrows in
 * {@code renderEquippedItems}, outside its red pass. While any of these is drawn, capturing is suspended
 * ({@link LegacyHurtTint#suspend()}): items ({@code ItemRenderer.render}), blocks such as an enderman's block or a
 * snow golem's pumpkin ({@code BlockRenderDispatcher.renderSingleBlock}), skulls ({@code CustomHeadLayer}), capes,
 * elytra, stuck arrows/stingers and shoulder parrots. The calls are balanced, so nesting is safe.</p>
 */
@Mixin({ItemRenderer.class, BlockRenderDispatcher.class, CustomHeadLayer.class, CapeLayer.class, ElytraLayer.class,
    StuckInBodyLayer.class, ParrotOnShoulderLayer.class})
public class LegacyHurtExcludeMixin {

    @Inject(method = {"render", "renderSingleBlock"}, at = @At("HEAD"))
    private void animatium$suspendHurtCapture(CallbackInfo ci) {
        LegacyHurtTint.suspend();
    }

    @Inject(method = {"render", "renderSingleBlock"}, at = @At("RETURN"))
    private void animatium$resumeHurtCapture(CallbackInfo ci) {
        LegacyHurtTint.resume();
    }
}
