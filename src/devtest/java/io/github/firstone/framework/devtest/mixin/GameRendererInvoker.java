package io.github.firstone.framework.devtest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Runs the hurt camera tilt on its own, so a test can read the rotation it applies; part of the dev test mod only
 */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {

    /**
     * Applies the hurt (and death) camera rotation of the camera entity to a pose stack
     *
     * @param poseStack   the pose stack to rotate
     * @param partialTick progress between ticks
     */
    @Invoker("bobHurt")
    void devtest$bobHurt(PoseStack poseStack, float partialTick);
}
