package io.github.firstone.framework.client.mixin.cosmetics.itembreak;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.cosmetics.itembreak.ItemBreakEffect;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Replaces the item break effect with the player's files ({@link ItemBreakEffect})
 *
 * <p>{@code breakItem} runs on the client when an item of the entity breaks (entity events 47–52 and 65); its sound
 * and its item crack particles are replaced separately.</p>
 */
@Mixin(LivingEntity.class)
public class CosmeticItemBreakMixin {

    @WrapOperation(method = "breakItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void cosmetics$itemBreakSound(Level level, double x, double y, double z, SoundEvent sound,
                                          SoundSource source, float volume, float pitch, boolean distanceDelay,
                                          Operation<Void> original) {
        if (!ItemBreakEffect.playItemBreakSound((LivingEntity) (Object) this)) {
            original.call(level, x, y, z, sound, source, volume, pitch, distanceDelay);
        }
    }

    @WrapOperation(method = "breakItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;spawnItemParticles(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void cosmetics$itemBreakParticles(LivingEntity entity, ItemStack stack, int count,
                                              Operation<Void> original) {
        if (!ItemBreakEffect.playItemBreakParticles(entity)) {
            original.call(entity, stack, count);
        }
    }
}
