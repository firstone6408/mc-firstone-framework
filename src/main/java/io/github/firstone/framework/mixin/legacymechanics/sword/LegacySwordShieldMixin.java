package io.github.firstone.framework.mixin.legacymechanics.sword;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.legacymechanics.sword.LegacySwordBlocking;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps a blocking sword from counting as a raised shield ({@link LegacySwordBlocking})
 *
 * <p>1.21.1 treats every item used with the block animation as a shield: it stops all damage from the front, can be
 * disabled by axes and limits how far the player's head turns. A 1.7.10 sword block only lowers the damage, so
 * {@code isBlocking} answers false while a sword is used.</p>
 */
@Mixin(LivingEntity.class)
public class LegacySwordShieldMixin {

    @ModifyReturnValue(method = "isBlocking", at = @At("RETURN"))
    private boolean legacymechanics$swordIsNoShield(boolean blocking) {
        return blocking && !LegacySwordBlocking.isSword(((LivingEntity) (Object) this).getUseItem());
    }
}
