package io.github.firstone.framework.client.mixin.cosmetics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.cosmetics.CosmeticsPack;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

/**
 * Adds the Cosmetics pack ({@link CosmeticsPack}) to the client's resource pack list when the game starts
 *
 * <p>Only the client's resource packs are changed (the list created in the {@code Minecraft} constructor); data
 * packs and servers are not affected.</p>
 */
@Mixin(Minecraft.class)
public class CosmeticsPackMixin {

    @WrapOperation(method = "<init>", at = @At(value = "NEW",
        target = "([Lnet/minecraft/server/packs/repository/RepositorySource;)Lnet/minecraft/server/packs/repository/PackRepository;"))
    private PackRepository cosmetics$addPack(RepositorySource[] sources, Operation<PackRepository> original) {
        RepositorySource[] withCosmetics = Arrays.copyOf(sources, sources.length + 1);
        withCosmetics[sources.length] = CosmeticsPack.SOURCE;
        return original.call((Object) withCosmetics);
    }
}
