package io.github.firstone.framework.mixin.legacymechanics.experience;

import io.github.firstone.framework.features.legacymechanics.experience.LegacyExperience;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Places the experience of smelted items where 1.7.10 did ({@link LegacyExperience})
 *
 * <p>{@code awardUsedRecipesAndPopExperience} runs when a player takes items out of a furnace, smoker or blast
 * furnace; 1.21.1 drops the orbs at the player's feet, 1.7.10 ({@code SlotFurnace}) half a block higher and to the
 * south. Runs on the server (also the integrated one).</p>
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class LegacyFurnaceExperienceMixin {

    @ModifyArg(method = "awardUsedRecipesAndPopExperience",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;"
            + "getRecipesToAwardAndPopExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;)"
            + "Ljava/util/List;"), index = 1)
    private Vec3 legacymechanics$legacyPosition(Vec3 playerPosition) {
        return LegacyExperience.furnaceExperiencePosition(playerPosition);
    }
}
