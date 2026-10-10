package io.github.firstone.framework.mixin.legacymechanics.boat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.boat.LegacyBoatRules;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Server side of {@link LegacyBoatRules} for moves sent by clients
 *
 * <ul>
 *   <li>{@code handleMoveVehicle} — the server moves a vehicle to the position its driver's client sent; each move of
 *       a boat is checked for a crash</li>
 *   <li>{@code isPlayerCollidingWithAnythingNew} — boats are left out of the collisions that make the server put a
 *       player back ({@link LegacyBoatRules#withoutBoats})</li>
 * </ul>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class LegacyBoatDriverMixin {

    @WrapOperation(method = "handleMoveVehicle", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void legacyMechanics$checkDriverMove(Entity vehicle, MoverType type, Vec3 move, Operation<Void> original) {
        original.call(vehicle, type, move);
        if (vehicle instanceof Boat boat) {
            LegacyBoatRules.afterDriverMove(boat, move);
        }
    }

    @WrapOperation(method = "isPlayerCollidingWithAnythingNew", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/LevelReader;getCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"))
    private Iterable<VoxelShape> legacyMechanics$ignoreBoats(LevelReader level, Entity player, AABB box,
                                                            Operation<Iterable<VoxelShape>> original) {
        return LegacyBoatRules.withoutBoats(level, box, original.call(level, player, box));
    }
}
