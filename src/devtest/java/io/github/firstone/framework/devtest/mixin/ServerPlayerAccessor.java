package io.github.firstone.framework.devtest.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ends the player's spawn protection, so a test can hurt the player right after the world loads; part of the dev
 * test mod only
 */
@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {

    /**
     * Sets how many more ticks the player cannot be hurt after joining (60 when joining)
     *
     * @param ticks the ticks left, 0 to end the protection
     */
    @Accessor("spawnInvulnerableTime")
    void devtest$setSpawnInvulnerableTime(int ticks);
}
