package io.github.firstone.framework.client.mixin.cosmetics;

import io.github.firstone.framework.features.cosmetics.CosmeticEffects;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the player's own body the custom death effect ({@link CosmeticEffects#playDeath})
 *
 * <p>The client removes its own player when the death timer reaches 20 ({@code LocalPlayer.tickDeath}), usually
 * before the server's "body disappears" message arrives, so the game shows no {@code poof} for yourself. The custom
 * effect is played at that moment instead; nothing is shown when the effect has no files. Each tick of the death
 * timer may also start the death sound early ({@link CosmeticEffects#tickDeath}).</p>
 */
@Mixin(LocalPlayer.class)
public class CosmeticSelfDeathMixin {

    @Inject(method = "tickDeath", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/player/LocalPlayer;remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))
    private void cosmetics$selfDeath(CallbackInfo ci) {
        CosmeticEffects.playDeath((LocalPlayer) (Object) this);
    }

    @Inject(method = "tickDeath", at = @At("TAIL"))
    private void cosmetics$selfDeathTick(CallbackInfo ci) {
        CosmeticEffects.tickDeath((LocalPlayer) (Object) this);
    }
}
