package io.github.firstone.framework.features.animatium.camera;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;

/**
 * The direction of the 1.7.10 hurt camera tilt
 *
 * <p>Both versions roll the camera by up to 14 degrees when the player is hurt, around an axis turned by the hurt
 * direction ({@code EntityRenderer.hurtCameraEffect} in 1.7.10, {@code GameRenderer.bobHurt} in 1.21.1). The 1.7.10
 * client always used 0 there: {@code attackedAtYaw} was only computed on the server, and the client set it to 0
 * whenever the player was hurt ({@code handleHealthUpdate}, {@code performHurtAnimation}). 1.21.1 receives the
 * direction of the hit ({@code ClientboundHurtAnimationPacket}) and rolls away from the attacker.</p>
 */
public final class LegacyHurtTilt {

    private LegacyHurtTilt() {}

    /**
     * Returns the direction the camera tilt is turned by
     *
     * @param hurtDirection the direction of the hit 1.21.1 would use, in degrees
     * @return 0 like 1.7.10 when "Legacy Hurt Tilt" is on, otherwise {@code hurtDirection}
     */
    public static float direction(float hurtDirection) {
        return AnimatiumFeature.getConfig().legacyHurtTilt ? 0.0F : hurtDirection;
    }
}
