package io.github.firstone.framework.client.mixin.animatium.entity;

import io.github.firstone.framework.features.animatium.LegacyEntitySync;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Stores the 1.7.10 sync state ({@link LegacyEntitySync.State}) on every entity
 *
 * <p>The state is created on first use, so entities that never receive a movement packet carry no extra object.</p>
 */
@Mixin(Entity.class)
public abstract class LegacyEntitySyncStateMixin implements LegacyEntitySync.Holder {

    @Unique
    private LegacyEntitySync.State animatium$syncState;

    @Override
    public LegacyEntitySync.State animatium$getSyncState() {
        if (this.animatium$syncState == null) {
            this.animatium$syncState = new LegacyEntitySync.State();
        }
        return this.animatium$syncState;
    }
}
