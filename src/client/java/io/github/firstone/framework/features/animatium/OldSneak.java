package io.github.firstone.framework.features.animatium;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

/**
 * State and rules of the 1.7.10 sneak eye height for the local player
 *
 * <p>1.7.10 logic ({@code EntityPlayerSP.onLivingUpdate} and {@code Entity.moveEntity}):
 * every tick, {@code if (sneakKeyDown && yOffset2 < 0.2) yOffset2 = 0.2;} then {@code yOffset2 *= 0.4},
 * and the eye is {@code yOffset2} lower than standing. While sneaking the eye is therefore 0.08 lower
 * (1.62 → 1.54); after releasing it rises back 0.08 → 0.032 → 0.0128 … per tick. Between ticks the
 * value is interpolated with the partial tick, like 1.7.10 interpolated {@code posY}.</p>
 *
 * <p>Used by the {@code animatium.sneak} mixins: the camera and the pick ray both read
 * {@link #offset(float)}, so the crosshair always matches what the player hits.</p>
 */
public final class OldSneak {

    /** Value set while the sneak key is held, before the per-tick decay (1.7.10 {@code yOffset2 = 0.2F}) */
    private static final float SNEAK_OFFSET = 0.2F;

    /** Per-tick decay factor (1.7.10 {@code yOffset2 *= 0.4F}) */
    private static final float DECAY = 0.4F;

    /** Eye offset of the previous tick */
    private static float offsetO;

    /** Eye offset of the current tick */
    private static float offset;

    private OldSneak() {}

    /**
     * Advances the eye offset by one tick; called at the end of {@code LocalPlayer.tick()}
     *
     * @param player the local player
     */
    public static void tick(LocalPlayer player) {
        offsetO = offset;
        if (!AnimatiumFeature.getConfig().oldSneak || player.isPassenger() || player.isSpectator() || player.isSleeping()) {
            offset = 0.0F;
            offsetO = 0.0F;
            return;
        }

        if (player.input != null && player.input.shiftKeyDown && offset < SNEAK_OFFSET) {
            offset = SNEAK_OFFSET;
        }
        offset *= DECAY;
    }

    /**
     * Returns the eye offset of the current tick (no interpolation)
     *
     * @return how far below the standing eye height the eye is, in blocks
     */
    public static float currentOffset() {
        return offset;
    }

    /**
     * Returns the eye offset interpolated between the last two ticks
     *
     * @param partialTick progress between the last tick and the next one (0–1)
     * @return how far below the standing eye height the eye is, in blocks
     */
    public static float offset(float partialTick) {
        return Mth.lerp(partialTick, offsetO, offset);
    }

    /**
     * Tells whether the 1.7.10 eye height applies to this entity right now
     *
     * <p>Only the local player in the standing or crouching pose; elytra, swimming, sleeping and
     * other poses keep the vanilla eye height.</p>
     *
     * @param entity any entity
     * @return true if the legacy eye offset should be applied
     */
    public static boolean appliesTo(Entity entity) {
        if (!(entity instanceof LocalPlayer) || !AnimatiumFeature.getConfig().oldSneak) {
            return false;
        }
        Pose pose = entity.getPose();
        return pose == Pose.STANDING || pose == Pose.CROUCHING;
    }
}
