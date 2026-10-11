package io.github.firstone.framework.devtest.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches a mob's goals, so a test can clear them and walk the mob itself; part of the dev test mod only
 */
@Mixin(Mob.class)
public interface MobAccessor {

    /**
     * Returns the goals that move the mob (stroll, look around, attack…)
     *
     * @return {@code goalSelector}
     */
    @Accessor("goalSelector")
    GoalSelector devtest$getGoalSelector();

    /**
     * Returns the goals that choose the mob's target
     *
     * @return {@code targetSelector}
     */
    @Accessor("targetSelector")
    GoalSelector devtest$getTargetSelector();
}
