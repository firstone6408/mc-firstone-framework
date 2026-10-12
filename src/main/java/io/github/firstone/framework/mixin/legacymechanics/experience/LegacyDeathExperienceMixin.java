package io.github.firstone.framework.mixin.legacymechanics.experience;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.experience.LegacyExperience;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a killed entity's experience when its body disappears, like 1.7.10 ({@link LegacyExperience})
 *
 * <p>1.21.1 drops the experience at the moment of death ({@code dropExperience}, called by {@code die}). With the
 * option on, the amount worked out there (1.21.1's) is kept, and the orbs appear where the entity is when it is
 * removed: normally 20 ticks later with the death smoke ({@code tickDeath}), or earlier if it is removed another way
 * (a player who respawns at once or leaves, a command). Nothing is lost: when the chunk of a dying entity is saved
 * or unloads, the kept experience is saved with the entity (1.21.1 saves the dying entity and its orbs), and a
 * dying entity loaded again finishes dying and drops it. Runs on the server (also the integrated one).</p>
 */
@Mixin(LivingEntity.class)
public class LegacyDeathExperienceMixin {

    /** Name of the saved kept experience */
    @Unique
    private static final String KEPT_EXPERIENCE_TAG = "firstone-framework:kept_experience";

    /** Experience kept from the moment of death until the body disappears */
    @Unique
    private int legacymechanics$keptExperience;

    @WrapOperation(method = "dropExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/"
        + "ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void legacymechanics$keepUntilRemoved(ServerLevel level, Vec3 position, int amount,
                                                  Operation<Void> original) {
        if (LegacyExperience.delaysDeathExperience((LivingEntity) (Object) this)) {
            this.legacymechanics$keptExperience += amount;
        } else {
            original.call(level, position, amount);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void legacymechanics$saveKept(CompoundTag tag, CallbackInfo ci) {
        if (this.legacymechanics$keptExperience > 0) {
            tag.putInt(KEPT_EXPERIENCE_TAG, this.legacymechanics$keptExperience);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void legacymechanics$loadKept(CompoundTag tag, CallbackInfo ci) {
        this.legacymechanics$keptExperience = Math.max(tag.getInt(KEPT_EXPERIENCE_TAG), 0);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void legacymechanics$dropKept(Entity.RemovalReason reason, CallbackInfo ci) {
        // an entity unloaded with its chunk was saved with its kept experience (addAdditionalSaveData)
        int amount = this.legacymechanics$keptExperience;
        LivingEntity self = (LivingEntity) (Object) this;
        if (amount > 0 && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
            && self.level() instanceof ServerLevel level) {
            this.legacymechanics$keptExperience = 0;
            ExperienceOrb.award(level, self.position(), amount);
        }
    }
}
