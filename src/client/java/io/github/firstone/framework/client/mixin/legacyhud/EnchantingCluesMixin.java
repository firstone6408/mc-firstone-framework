package io.github.firstone.framework.client.mixin.legacyhud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacyhud.EnchantingClues;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy Mechanics' Legacy Enchanting on the enchanting table screen: the Show Clues button and the real levels a
 * line takes
 * ({@link EnchantingClues})
 *
 * <p>Both follow what the server said; on a server without the mod the screen stays as in 1.21.1.</p>
 */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantingCluesMixin extends AbstractContainerScreen<EnchantmentMenu> {

    /** The Show Clues button, or null when the server has no paid clues */
    @Unique
    @Nullable
    private Button legacyHud$clueButton;

    /** What the button's tooltip says, to make it again only when it changes */
    @Unique
    private int legacyHud$clueState = -1;

    private EnchantingCluesMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void legacyHud$addClueButton(CallbackInfo ci) {
        this.legacyHud$clueButton = EnchantingClues.button(this.leftPos, this.topPos, this.menu);
        this.legacyHud$clueState = -1;
        if (this.legacyHud$clueButton != null) {
            addRenderableWidget(this.legacyHud$clueButton);
        }
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void legacyHud$updateClueButton(CallbackInfo ci) {
        if (this.legacyHud$clueButton != null) {
            this.legacyHud$clueState = EnchantingClues.update(this.legacyHud$clueButton, this.menu,
                this.legacyHud$clueState);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent legacyHud$levelOne(String key, Operation<MutableComponent> original) {
        return EnchantingClues.levelsText(key, original.call(key), this.menu);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent legacyHud$levelsMany(String key, Object[] args,
                                                        Operation<MutableComponent> original) {
        return EnchantingClues.levelsText(key, args, original.call(key, args), this.menu);
    }
}
