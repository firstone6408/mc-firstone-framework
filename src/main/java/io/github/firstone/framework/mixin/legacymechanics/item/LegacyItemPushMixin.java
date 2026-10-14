package io.github.firstone.framework.mixin.legacymechanics.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.item.LegacyItemDrops;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Kicks items out of blocks the 1.7.10 way ({@link LegacyItemDrops})
 *
 * <p>Every tick on the server, {@code ItemEntity.tick} asks {@code noCollision} whether the item is free; if not, it
 * moves through blocks this tick and {@code moveTowardsClosestSpace} kicks it out. Both calls are replaced: the
 * check only looks at blocks, and the kick is the 1.7.10 one. Runs on the server (also the integrated one).</p>
 */
@Mixin(ItemEntity.class)
public class LegacyItemPushMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean legacymechanics$legacyIsFree(Level level, Entity entity, AABB box, Operation<Boolean> original) {
        return LegacyItemDrops.kicksLikeLegacy()
            ? LegacyItemDrops.isFree((ItemEntity) (Object) this) : original.call(level, entity, box);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/item/ItemEntity;moveTowardsClosestSpace(DDD)V"))
    private void legacymechanics$legacyKick(ItemEntity item, double x, double y, double z, Operation<Void> original) {
        if (LegacyItemDrops.kicksLikeLegacy()) {
            LegacyItemDrops.pushOutOfBlocks(item, x, y, z);
        } else {
            original.call(item, x, y, z);
        }
    }
}
