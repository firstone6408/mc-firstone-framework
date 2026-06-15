package io.github.firstone.framework.mixin.combattweaks;

import io.github.firstone.framework.features.combattweaks.CombatTweaksConfig;
import io.github.firstone.framework.features.combattweaks.CombatTweaksFeature;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin that controls the sweeping attack (AOE), like versions before 1.9
 *
 * <p>In 1.9+ a full-strength attack on the ground without sprinting triggers
 * a sweeping attack that also damages nearby entities.
 * This behavior did not exist in older versions</p>
 *
 * <p>This mixin uses 2 interception points:</p>
 * <ul>
 *   <li>{@code @Redirect} on {@code Level.getEntitiesOfClass()} in {@code attack()} —
 *   returns an empty list so nearby entities are not damaged</li>
 *   <li>{@code @Inject} in {@code sweepAttack()} — cancels the particle effect on the server</li>
 * </ul>
 *
 * <p>The Sweeping Edge check uses the {@code SWEEPING_DAMAGE_RATIO} attribute,
 * which is > 0 only when the weapon has the Sweeping Edge enchantment</p>
 *
 * <p>Targets: {@link Player#attack(Entity)} and {@link Player#sweepAttack()}</p>
 */
@Mixin(Player.class)
public class SweepingAttackMixin {

    /**
     * Decides whether the sweep attack should be blocked
     *
     * <p>Logic:</p>
     * <ul>
     *   <li>{@code disableSweepingAttack} is false → allow (vanilla)</li>
     *   <li>{@code sweepingEdgeRequired} is true → allow only with Sweeping Edge</li>
     *   <li>otherwise → block</li>
     * </ul>
     *
     * @return true if the sweep should be blocked, false if it should be allowed
     */
    @Unique
    private boolean combattweaks$shouldBlockSweep() {
        CombatTweaksConfig config = CombatTweaksFeature.getConfig();
        if (!config.disableSweepingAttack) return false;
        if (!config.sweepingEdgeRequired) return true;

        // check Sweeping Edge through the SWEEPING_DAMAGE_RATIO attribute
        // this attribute is > 0 only when the weapon has the Sweeping Edge enchantment
        Player self = (Player) (Object) this;
        double sweepRatio = self.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO);
        return sweepRatio <= 0;
    }

    /**
     * Redirects the lookup of nearby entities in {@code Player.attack()} to control sweep damage
     *
     * <p>When the sweep is blocked, returns an empty list so the loop has no entity to damage</p>
     *
     * <p>This intercepts the call at offset 654 in the bytecode of {@code attack()}:
     * {@code Level.getEntitiesOfClass(LivingEntity.class, aabb)}</p>
     *
     * @param level       level used to search for entities
     * @param entityClass class of the entities searched for
     * @param aabb        search area around the target
     * @return the entities that will be damaged (empty if the sweep is blocked)
     */
    @Redirect(
        method = "attack(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;")
    )
    private List<LivingEntity> redirectSweepEntities(Level level, Class<LivingEntity> entityClass, AABB aabb) {
        if (combattweaks$shouldBlockSweep()) {
            return List.of();
        }
        return level.getEntitiesOfClass(entityClass, aabb);
    }

    /**
     * Cancels the sweep particle effect on the server when the sweep is blocked
     *
     * <p>{@code sweepAttack()} runs on the server to send the {@code SWEEP_ATTACK} particle
     * to clients; cancelling it here stops the particle from being sent</p>
     *
     * @param ci cancellable CallbackInfo
     */
    @Inject(method = "sweepAttack()V", at = @At("HEAD"), cancellable = true)
    private void onSweepAttack(CallbackInfo ci) {
        if (combattweaks$shouldBlockSweep()) {
            ci.cancel();
        }
    }
}
