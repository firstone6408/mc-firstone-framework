package io.github.firstone.framework.client.mixin.animatium.sneak;

import io.github.firstone.framework.features.animatium.OldSneak;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Advances the 1.7.10 sneak eye offset once per tick
 *
 * <p>Runs at the end of {@link LocalPlayer#tick()}, after the movement input of this tick has been read,
 * like 1.7.10 updated {@code yOffset2} during the player's own tick.</p>
 */
@Mixin(LocalPlayer.class)
public class OldSneakTickMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void animatium$tickSneakOffset(CallbackInfo ci) {
        OldSneak.tick((LocalPlayer) (Object) this);
    }
}
