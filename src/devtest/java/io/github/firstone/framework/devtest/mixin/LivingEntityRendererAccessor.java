package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Reads the layers of an entity renderer (held items, armor…), so a test can draw one of them on its own; part of
 * the dev test mod only
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    /**
     * Returns the renderer's layers, in drawing order
     *
     * @return {@code layers}
     */
    @Accessor("layers")
    List<RenderLayer<?, ?>> devtest$getLayers();
}
