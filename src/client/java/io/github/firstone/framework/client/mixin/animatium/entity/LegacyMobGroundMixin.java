package io.github.firstone.framework.client.mixin.animatium.entity;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.firstone.framework.features.animatium.LegacyMobPhysics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the client decide whether a simulated mob is on the ground, like 1.7.10
 *
 * <p>1.7.10 entity move packets carried no on-ground flag (the boolean of {@code S14PacketEntity} only says whether
 * a rotation is included); the client found out from its own physics ({@code moveEntity}). 1.21.1 copies the server's
 * flag from every move and teleport packet, which changes the friction the client physics uses between ticks.
 * For mobs that get 1.7.10 client physics ({@link LegacyMobPhysics}), the packet's flag is ignored; other entities
 * keep it.</p>
 */
@Mixin(ClientPacketListener.class)
public class LegacyMobGroundMixin {

    @WrapWithCondition(
        method = {"handleMoveEntity", "handleTeleportEntity"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setOnGround(Z)V")
    )
    private boolean animatium$clientDecidesGround(Entity entity, boolean onGround) {
        return !(entity instanceof LivingEntity living && LegacyMobPhysics.appliesTo(living));
    }
}
