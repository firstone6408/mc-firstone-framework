package io.github.firstone.framework.mixin.legacymechanics.sword;

import io.github.firstone.framework.features.legacymechanics.sword.LegacySwordBlocking;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lowers the damage a player takes while blocking with a sword, like 1.7.10 ({@link LegacySwordBlocking})
 *
 * <p>Changes the damage at the start of {@code Player.actuallyHurt}, before armor, enchantments and absorption, where
 * 1.7.10 {@code EntityPlayer.damageEntity} applied it. Runs on the server.</p>
 */
@Mixin(Player.class)
public class LegacySwordDamageMixin {

    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true)
    private float legacymechanics$swordBlock(float damage, DamageSource source) {
        return LegacySwordBlocking.blockedDamage((Player) (Object) this, source, damage);
    }
}
