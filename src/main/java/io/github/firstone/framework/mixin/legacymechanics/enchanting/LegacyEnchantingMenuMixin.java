package io.github.firstone.framework.mixin.legacymechanics.enchanting;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.firstone.framework.features.legacymechanics.enchanting.LegacyEnchanting;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy Enchanting: the enchanting table's roll, clues and cost ({@link LegacyEnchanting})
 *
 * <p>The game's own code still works out the roll; this only gives it the seed of the table's roll (both where the
 * roll is shown, {@code slotsChanged}, and where it is applied, {@code getEnchantmentList}), keeps the clues back
 * until they are paid for, changes the levels taken, and answers the Show Clues button. Server side only: the roll
 * is kept on the server's screen, the client's screen is left as it is.</p>
 *
 * <p>Lambdas by their intermediary names: {@code method_17411} works out the roll in {@code slotsChanged},
 * {@code method_17410} enchants in {@code clickMenuButton}.</p>
 */
@Mixin(EnchantmentMenu.class)
public abstract class LegacyEnchantingMenuMixin implements LegacyEnchanting.Holder {

    @Shadow @Final private Container enchantSlots;
    @Shadow @Final private DataSlot enchantmentSeed;
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;

    /** The player of the server's screen (only it rolls), or null on the client */
    @Unique
    @Nullable
    private Player legacymechanics$player;

    /** The table's roll while Legacy Enchant Reroll is on, made when the first item is put in */
    @Unique
    @Nullable
    private LegacyEnchanting.Roll legacymechanics$roll;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
        at = @At("TAIL"))
    private void legacymechanics$rememberPlayer(int id, Inventory inventory, ContainerLevelAccess access,
                                                CallbackInfo ci) {
        this.legacymechanics$player = inventory.player.level().isClientSide() ? null : inventory.player;
    }

    @Inject(method = "slotsChanged", at = @At("HEAD"))
    private void legacymechanics$newRoll(Container container, CallbackInfo ci) {
        Player player = this.legacymechanics$player;
        if (player != null && container == this.enchantSlots) {
            this.legacymechanics$roll = LegacyEnchanting.itemChanged(this.legacymechanics$roll, container.getItem(0),
                this.enchantmentSeed, player);
        }
    }

    @ModifyExpressionValue(method = {"method_17411", "getEnchantmentList"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/DataSlot;get()I"))
    private int legacymechanics$rollSeed(int seed) {
        return LegacyEnchanting.seed(this.legacymechanics$roll, seed);
    }

    @WrapOperation(method = "method_17411", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/inventory/EnchantmentMenu;broadcastChanges()V"))
    private void legacymechanics$hideClues(EnchantmentMenu menu, Operation<Void> original) {
        LegacyEnchanting.hideClues(this.legacymechanics$roll, this.enchantClue, this.levelClue);
        original.call(menu);
    }

    @WrapOperation(method = "method_17410", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V"))
    private void legacymechanics$levelsTaken(Player player, ItemStack item, int levels, Operation<Void> original,
                                             @Local(argsOnly = true, ordinal = 0) int line) {
        original.call(player, item, LegacyEnchanting.levelsTaken(levels, this.costs[line]));
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void legacymechanics$showClues(Player player, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button == LegacyEnchanting.CLUE_BUTTON && this.legacymechanics$player != null
            && LegacyEnchanting.paidClues()) {
            cir.setReturnValue(LegacyEnchanting.showClues(this.legacymechanics$roll, player, this.enchantSlots,
                this.costs, this.enchantmentSeed));
        }
    }

    @Override
    @Nullable
    public LegacyEnchanting.Roll legacymechanics$roll() {
        return this.legacymechanics$roll;
    }
}
