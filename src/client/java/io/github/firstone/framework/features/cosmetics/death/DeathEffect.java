package io.github.firstone.framework.features.cosmetics.death;

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

import java.lang.ref.WeakReference;
import java.util.List;

import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.MAX_SCALE;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.MIN_SCALE;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.appliesTo;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.frames;
import static io.github.firstone.framework.features.cosmetics.CosmeticEffects.playSound;

/**
 * The Cosmetics death effect: the player's particles and sound when a dead body disappears
 *
 * <p>Called by the mixins in {@code client/mixin/cosmetics/death/}. When the player has no file for the effect (or
 * turned it off), the game's own effect is kept.</p>
 */
public final class DeathEffect {

    /** Most death particles (slider maximum) */
    public static final int MAX_DEATH_PARTICLES = 100;

    /** Latest death sound start, in seconds before the body disappears (19 of the 20 ticks of the death) */
    public static final float MAX_SOUND_LEAD = 0.95F;

    /** Ticks between the death and the body disappearing (the game's {@code tickDeath}) */
    private static final int DEATH_TICKS = 20;

    private static final ResourceLocation DEATH_SOUND = CosmeticsPack.soundEvent(CosmeticsFiles.DEATH);

    /**
     * Last entity that got the death effect, so the local player does not get it twice (the death is seen both by
     * the player's own death timer and by the server's message); weak, so a removed entity is not kept in memory
     */
    private static WeakReference<LivingEntity> lastDeath = new WeakReference<>(null);

    private DeathEffect() {}

    /**
     * Plays the death effect when a dead body disappears (the game's {@code poof})
     *
     * <p>Plays the death sound if there is one (unless it already started early, see {@link #tickDeath}), then the
     * particles if there are frames.</p>
     *
     * @param entity the entity whose body disappears
     * @return true if the game's {@code poof} must not be shown (custom particles shown, or already handled)
     */
    public static boolean playDeath(LivingEntity entity) {
        CosmeticsConfig.Death config = CosmeticsFeature.getConfig().death;
        if (!config.enabled || !appliesTo(entity, config.players, config.mobs)) {
            return false;
        }
        if (lastDeath.get() == entity) {
            return true;
        }
        lastDeath = new WeakReference<>(entity);

        if (leadTicks(config) == 0) {
            playDeathSound(entity, config);
        }
        List<TextureAtlasSprite> frames = frames(CosmeticsFiles.DEATH);
        if (frames.isEmpty() || !(entity.level() instanceof ClientLevel level)) {
            return false;
        }
        int count = Mth.clamp(config.particleCount, 1, MAX_DEATH_PARTICLES);
        float size = Mth.clamp(config.particleSize, MIN_SCALE, MAX_SCALE);
        float duration = Mth.clamp(config.particleDuration, MIN_SCALE, MAX_SCALE);
        float speed = Mth.clamp(config.particleSpeed, 0.0F, MAX_SCALE);
        RandomSource random = entity.getRandom();
        // same spawn area and motion as LivingEntity.makePoofParticles
        for (int i = 0; i < count; i++) {
            Minecraft.getInstance().particleEngine.add(CosmeticParticle.death(level,
                entity.getRandomX(1.0), entity.getRandomY(), entity.getRandomZ(1.0),
                random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02,
                frames, size, duration, speed));
        }
        return true;
    }

    /**
     * Starts the death sound early: called every tick of a dying entity, after its death timer moved
     *
     * <p>The body disappears when the timer reaches 20 ticks (1 second after the death), so a sound that should
     * start {@code soundLead} seconds before is started at tick {@code 20 - soundLead × 20}.</p>
     *
     * @param entity the dying entity
     */
    public static void tickDeath(LivingEntity entity) {
        CosmeticsConfig.Death config = CosmeticsFeature.getConfig().death;
        int lead = leadTicks(config);
        if (lead > 0 && entity.deathTime == DEATH_TICKS - lead && config.enabled
            && appliesTo(entity, config.players, config.mobs)) {
            playDeathSound(entity, config);
        }
    }

    /** Returns how many ticks before the body disappears the death sound starts (0 = when it disappears) */
    private static int leadTicks(CosmeticsConfig.Death config) {
        return Math.round(Mth.clamp(config.soundLead, 0.0F, MAX_SOUND_LEAD) * DEATH_TICKS);
    }

    /** Plays the death sound of a not silent entity */
    private static void playDeathSound(LivingEntity entity, CosmeticsConfig.Death config) {
        if (!entity.isSilent()) {
            playSound(entity, DEATH_SOUND, config.soundVolume, config.sound3d, 0);
        }
    }
}
