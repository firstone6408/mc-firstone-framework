package io.github.firstone.framework.features.cosmetics.itembreak;

import io.github.firstone.framework.features.cosmetics.CosmeticParticle;
import io.github.firstone.framework.features.cosmetics.CosmeticsConfig;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsFiles;
import io.github.firstone.framework.features.cosmetics.CosmeticsPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.MAX_SCALE;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.MIN_SCALE;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.appliesTo;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.frames;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.playSound;

/**
 * The Cosmetics item break effect: the player's particles and sound when a tool, weapon or armor piece breaks
 *
 * <p>Called by the mixin in {@code client/mixin/cosmetics/itembreak/}. When the player has no file for the effect
 * (or turned it off), the game's own effect is kept.</p>
 */
public final class ItemBreakEffect {

    /** Most item break particles (slider maximum) */
    public static final int MAX_ITEM_BREAK_PARTICLES = 50;

    /** Longest skip at the start of the item break sound, in seconds */
    public static final float MAX_SOUND_SKIP = 10.0F;

    private static final ResourceLocation ITEM_BREAK_SOUND = CosmeticsPack.soundEvent(CosmeticsFiles.ITEM_BREAK);

    private ItemBreakEffect() {}

    /**
     * Plays the item break sound instead of the game's one
     *
     * @param entity the entity whose item breaks (only called when it is not silent)
     * @return true if the custom sound replaced the game's sound
     */
    public static boolean playItemBreakSound(LivingEntity entity) {
        CosmeticsConfig.ItemBreak config = CosmeticsFeature.getConfig().itemBreak;
        int skipMs = Math.round(Mth.clamp(config.soundSkip, 0.0F, MAX_SOUND_SKIP) * 1000);
        return config.enabled && appliesTo(entity, config.players, config.mobs)
            && playSound(entity, ITEM_BREAK_SOUND, config.soundVolume, config.sound3d, skipMs);
    }

    /**
     * Shows the item break particles instead of the game's item crack
     *
     * @param entity the entity whose item breaks
     * @return true if the custom particles replaced the game's particles
     */
    public static boolean playItemBreakParticles(LivingEntity entity) {
        CosmeticsConfig.ItemBreak config = CosmeticsFeature.getConfig().itemBreak;
        if (!config.enabled || !appliesTo(entity, config.players, config.mobs)) {
            return false;
        }
        List<TextureAtlasSprite> frames = frames(CosmeticsFiles.ITEM_BREAK);
        if (frames.isEmpty() || !(entity.level() instanceof ClientLevel level)) {
            return false;
        }
        int count = Mth.clamp(config.particleCount, 1, MAX_ITEM_BREAK_PARTICLES);
        float size = Mth.clamp(config.particleSize, MIN_SCALE, MAX_SCALE);
        float duration = Mth.clamp(config.particleDuration, MIN_SCALE, MAX_SCALE);
        RandomSource random = entity.getRandom();
        float pitch = -entity.getXRot() * Mth.DEG_TO_RAD;
        float yaw = -entity.getYRot() * Mth.DEG_TO_RAD;
        // same spawn place and motion as LivingEntity.spawnItemParticles: in front of the eyes, thrown forward
        for (int i = 0; i < count; i++) {
            Vec3 motion = new Vec3((random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0)
                .xRot(pitch).yRot(yaw);
            Vec3 pos = new Vec3((random.nextFloat() - 0.5) * 0.3, -random.nextFloat() * 0.6 - 0.3, 0.6)
                .xRot(pitch).yRot(yaw).add(entity.getX(), entity.getEyeY(), entity.getZ());
            Minecraft.getInstance().particleEngine.add(CosmeticParticle.itemBreak(level, pos.x, pos.y, pos.z,
                motion.x, motion.y + 0.05, motion.z, frames, size, duration));
        }
        return true;
    }
}
