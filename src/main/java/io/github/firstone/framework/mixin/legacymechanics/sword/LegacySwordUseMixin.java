package io.github.firstone.framework.mixin.legacymechanics.sword;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.legacymechanics.sword.LegacySwordBlocking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets swords be used to block, like 1.7.10 {@code ItemSword} ({@link LegacySwordBlocking})
 *
 * <p>Swords do not change these methods of {@code Item}, so the base methods answer for them: a right click starts
 * using the sword, with the block animation and the 1.7.10 duration. Runs on both sides; every other item is left
 * alone.</p>
 */
@Mixin(Item.class)
public class LegacySwordUseMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void legacymechanics$blockWithSword(Level level, Player player, InteractionHand hand,
                                                CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if ((Object) this instanceof SwordItem && LegacySwordBlocking.blocks(level.isClientSide(), player, hand)) {
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResultHolder.consume(player.getItemInHand(hand)));
        }
    }

    @ModifyReturnValue(method = "getUseAnimation", at = @At("RETURN"))
    private UseAnim legacymechanics$swordBlockAnimation(UseAnim useAnim) {
        return (Object) this instanceof SwordItem && LegacySwordBlocking.enabledAnywhere() ? UseAnim.BLOCK : useAnim;
    }

    @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
    private int legacymechanics$swordUseDuration(int duration, ItemStack stack, LivingEntity entity) {
        return (Object) this instanceof SwordItem && LegacySwordBlocking.enabledAnywhere()
            ? LegacySwordBlocking.USE_DURATION : duration;
    }
}
