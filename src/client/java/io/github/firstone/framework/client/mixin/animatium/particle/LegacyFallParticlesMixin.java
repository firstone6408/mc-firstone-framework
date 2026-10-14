package io.github.firstone.framework.client.mixin.animatium.particle;

import io.github.firstone.framework.features.animatium.particle.LegacyFallParticles;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the server's particles of a landing as the 1.7.10 dust ring ({@link LegacyFallParticles})
 *
 * <p>In {@code handleParticleEvent}, right after the packet is handed to the client thread
 * ({@code PacketUtils.ensureRunningOnSameThread}); the 1.21.1 particles are left out for a landing, every other
 * particle packet goes on as usual.</p>
 */
@Mixin(ClientPacketListener.class)
public class LegacyFallParticlesMixin {

    @Inject(method = "handleParticleEvent", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void animatium$legacyFallParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (LegacyFallParticles.handle(packet)) {
            ci.cancel();
        }
    }
}
