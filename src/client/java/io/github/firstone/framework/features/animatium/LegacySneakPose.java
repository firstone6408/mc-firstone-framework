package io.github.firstone.framework.features.animatium;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.10 rules for when a player is drawn sneaking, and how far the model is lowered
 *
 * <p>1.7.10 drew the sneak pose whenever the sneak flag was set (shift held), also while flying in creative.
 * 1.21.1 draws it only in the {@code CROUCHING} pose, which is never used while flying. 1.7.10 lowered other
 * players' models by 0.125 block ({@code RenderPlayer}); the local player was lowered by its eye offset instead,
 * because its position itself moved down ({@link OldSneak}).</p>
 */
public final class LegacySneakPose {

    /** How far 1.7.10 lowered other sneaking players ({@code RenderPlayer: y -= 0.125}) */
    private static final double OTHER_PLAYER_OFFSET = 0.125;

    private LegacySneakPose() {}

    /**
     * Tells whether the legacy sneak pose rules are active
     *
     * @return true if the "Legacy Sneak Pose" option is on
     */
    public static boolean enabled() {
        return AnimatiumFeature.getConfig().legacySneakPose;
    }

    /**
     * Tells whether 1.7.10 would draw this player sneaking
     *
     * <p>True when shift is held (synced from the server for other players) or the player is crouching, as long as
     * the pose is standing or crouching (elytra, swimming and sleeping keep their own poses).</p>
     *
     * @param player any player
     * @return true to draw the sneak pose
     */
    public static boolean isSneaking(Player player) {
        Pose pose = player.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) {
            return false;
        }
        return player.isShiftKeyDown() || pose == Pose.CROUCHING;
    }

    /**
     * Returns the 1.7.10 render offset of a player model
     *
     * @param player      the player being drawn
     * @param partialTick progress between ticks
     * @return the offset to apply to the model position
     */
    public static Vec3 renderOffset(Player player, float partialTick) {
        if (player instanceof LocalPlayer && OldSneak.appliesTo(player)) {
            float offset = OldSneak.offset(partialTick);
            return offset == 0.0F ? Vec3.ZERO : new Vec3(0.0, -offset, 0.0);
        }
        return isSneaking(player) ? new Vec3(0.0, -OTHER_PLAYER_OFFSET * player.getScale(), 0.0) : Vec3.ZERO;
    }
}
