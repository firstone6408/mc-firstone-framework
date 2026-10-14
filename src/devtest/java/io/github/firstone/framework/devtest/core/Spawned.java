package io.github.firstone.framework.devtest.core;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * An entity a test spawned, seen on both sides: the client's copy and the server's
 *
 * @param <T> the entity's class
 */
public final class Spawned<T extends Entity> {

    private final TestContext t;
    private final int id;

    Spawned(TestContext t, int id) {
        this.t = t;
        this.id = id;
    }

    /**
     * Returns the entity's network id, the same on both sides
     *
     * @return the id
     */
    public int id() {
        return this.id;
    }

    /**
     * Returns the client's copy of the entity
     *
     * @return the entity, or null when it is gone (removed, or not sent to the client yet)
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public T client() {
        return this.t.level() == null ? null : (T) this.t.level().getEntity(this.id);
    }

    /**
     * Reads something from the server's copy of the entity, on the server thread
     *
     * @param reader reads the value from the entity
     * @param <R>    type of the value
     * @return the value, or null when the entity is gone on the server
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <R> R server(Function<T, R> reader) {
        return this.t.onServer(level -> {
            Entity entity = level.getEntity(this.id);
            return entity == null || entity.isRemoved() ? null : reader.apply((T) entity);
        });
    }

    /**
     * Tells whether the entity still exists on the server
     *
     * @return false once it was removed (killed, broken)
     */
    public boolean isAlive() {
        return Boolean.TRUE.equals(server(entity -> true));
    }

    /**
     * Returns how far the client's copy moved sideways in its last tick
     *
     * @return the horizontal speed in blocks per tick, or 0 when it is gone
     */
    public double speed() {
        Entity entity = client();
        return entity == null ? 0.0 : Math.hypot(entity.getX() - entity.xo, entity.getZ() - entity.zo);
    }
}
