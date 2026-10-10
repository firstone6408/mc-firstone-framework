package io.github.firstone.framework.client.mixin.cosmetics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.cosmetics.CosmeticEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the death effect and the item break effect with the player's files ({@link CosmeticEffects})
 *
 * <ul>
 *   <li>{@code makePoofParticles} — runs on the client when a dead body disappears (entity event 60)</li>
 *   <li>{@code tickDeath} — each tick of a dying entity; starts the death sound early when asked (the local player
 *       and the ender dragon have their own {@code tickDeath}: see {@code CosmeticSelfDeathMixin})</li>
 *   <li>{@code breakItem} — runs on the client when an item of the entity breaks (entity events 47–52 and 65);
 *       its sound and its item crack particles are replaced separately</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public class CosmeticEffectsMixin {

    @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
    private void cosmetics$death(CallbackInfo ci) {
        if (CosmeticEffects.playDeath((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickDeath", at = @At("TAIL"))
    private void cosmetics$deathTick(CallbackInfo ci) {
        CosmeticEffects.tickDeath((LivingEntity) (Object) this);
    }

    @WrapOperation(method = "breakItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void cosmetics$itemBreakSound(Level level, double x, double y, double z, SoundEvent sound,
                                          SoundSource source, float volume, float pitch, boolean distanceDelay,
                                          Operation<Void> original) {
        if (!CosmeticEffects.playItemBreakSound((LivingEntity) (Object) this)) {
            original.call(level, x, y, z, sound, source, volume, pitch, distanceDelay);
        }
    }

    @WrapOperation(method = "breakItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;spawnItemParticles(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void cosmetics$itemBreakParticles(LivingEntity entity, ItemStack stack, int count,
                                              Operation<Void> original) {
        if (!CosmeticEffects.playItemBreakParticles(entity)) {
            original.call(entity, stack, count);
        }
    }
}
