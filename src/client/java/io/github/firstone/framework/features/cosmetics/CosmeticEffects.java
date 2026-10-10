package io.github.firstone.framework.features.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays the Cosmetics effects: death, item break and music discs
 *
 * <p>Called by the mixins in {@code client/mixin/cosmetics/}. Each method checks the config and the files; when the
 * player has no file for an effect (or turned it off), the game's own effect is kept.</p>
 */
public final class CosmeticEffects {

    /** Most death particles (slider maximum) */
    public static final int MAX_DEATH_PARTICLES = 100;

    /** Most item break particles (slider maximum) */
    public static final int MAX_ITEM_BREAK_PARTICLES = 50;

    /** Smallest size / duration multiplier (slider minimum) */
    public static final float MIN_SCALE = 0.25F;

    /** Largest size / duration / speed multiplier (slider maximum) */
    public static final float MAX_SCALE = 4.0F;

    private static final SoundEvent DEATH_SOUND =
        SoundEvent.createVariableRangeEvent(CosmeticsPack.soundEvent(CosmeticsFiles.DEATH));
    private static final SoundEvent ITEM_BREAK_SOUND =
        SoundEvent.createVariableRangeEvent(CosmeticsPack.soundEvent(CosmeticsFiles.ITEM_BREAK));

    /**
     * Last entity that got the death effect, so the local player does not get it twice (the death is seen both by
     * the player's own death timer and by the server's message); weak, so a removed entity is not kept in memory
     */
    private static WeakReference<LivingEntity> lastDeath = new WeakReference<>(null);

    private CosmeticEffects() {}

    /**
     * Plays the death effect when a dead body disappears (the game's {@code poof})
     *
     * <p>Plays the death sound if there is one, then the particles if there are frames.</p>
     *
     * @param entity the entity whose body disappears
     * @return true if the game's {@code poof} must not be shown (custom particles shown, or already handled)
     */
    public static boolean playDeath(LivingEntity entity) {
        CosmeticsConfig config = CosmeticsFeature.getConfig();
        if (!config.deathEffect || !appliesTo(entity, config.deathEffectPlayers, config.deathEffectMobs)) {
            return false;
        }
        if (lastDeath.get() == entity) {
            return true;
        }
        lastDeath = new WeakReference<>(entity);

        if (!entity.isSilent()) {
            playSound(entity, DEATH_SOUND, config.deathSoundVolume);
        }
        List<TextureAtlasSprite> frames = frames(CosmeticsFiles.DEATH);
        if (frames.isEmpty() || !(entity.level() instanceof ClientLevel level)) {
            return false;
        }
        int count = Mth.clamp(config.deathParticleCount, 1, MAX_DEATH_PARTICLES);
        float size = Mth.clamp(config.deathParticleSize, MIN_SCALE, MAX_SCALE);
        float duration = Mth.clamp(config.deathParticleDuration, MIN_SCALE, MAX_SCALE);
        float speed = Mth.clamp(config.deathParticleSpeed, 0.0F, MAX_SCALE);
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
     * Plays the item break sound instead of the game's one
     *
     * @param entity the entity whose item breaks (only called when it is not silent)
     * @return true if the custom sound replaced the game's sound
     */
    public static boolean playItemBreakSound(LivingEntity entity) {
        CosmeticsConfig config = CosmeticsFeature.getConfig();
        return config.itemBreak && appliesTo(entity, config.itemBreakPlayers, config.itemBreakMobs)
            && playSound(entity, ITEM_BREAK_SOUND, config.itemBreakSoundVolume);
    }

    /**
     * Shows the item break particles instead of the game's item crack
     *
     * @param entity the entity whose item breaks
     * @return true if the custom particles replaced the game's particles
     */
    public static boolean playItemBreakParticles(LivingEntity entity) {
        CosmeticsConfig config = CosmeticsFeature.getConfig();
        if (!config.itemBreak || !appliesTo(entity, config.itemBreakPlayers, config.itemBreakMobs)) {
            return false;
        }
        List<TextureAtlasSprite> frames = frames(CosmeticsFiles.ITEM_BREAK);
        if (frames.isEmpty() || !(entity.level() instanceof ClientLevel level)) {
            return false;
        }
        int count = Mth.clamp(config.itemBreakParticleCount, 1, MAX_ITEM_BREAK_PARTICLES);
        float size = Mth.clamp(config.itemBreakParticleSize, MIN_SCALE, MAX_SCALE);
        float duration = Mth.clamp(config.itemBreakParticleDuration, MIN_SCALE, MAX_SCALE);
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

    /**
     * Returns the sound a jukebox plays for a disc: the custom song if there is a file for it
     *
     * @param song     the disc's song (its id, e.g. {@code minecraft:cat}, names the file {@code cat.ogg})
     * @param original the game's sound for the disc
     * @return the custom song, or {@code original}
     */
    public static SoundEvent musicDisc(Holder<JukeboxSong> song, SoundEvent original) {
        if (!CosmeticsFeature.getConfig().musicDiscs) {
            return original;
        }
        return song.unwrapKey()
            .map(key -> CosmeticsPack.soundEvent("music_disc." + key.location().getPath()))
            .filter(id -> Minecraft.getInstance().getSoundManager().getSoundEvent(id) != null)
            .map(SoundEvent::createVariableRangeEvent)
            .orElse(original);
    }

    /**
     * Tells whether an effect is on for this entity: players and mobs have their own option, and only the client
     * plays effects (in singleplayer the server shares the game's classes)
     */
    private static boolean appliesTo(LivingEntity entity, boolean players, boolean mobs) {
        return entity.level().isClientSide() && (entity instanceof Player ? players : mobs);
    }

    /**
     * Plays a Cosmetics sound at the entity, in the entity's sound category, if its file is loaded
     *
     * @return true if the sound exists (it is played even at volume 0, which mutes the game's sound)
     */
    private static boolean playSound(LivingEntity entity, SoundEvent sound, float volume) {
        if (Minecraft.getInstance().getSoundManager().getSoundEvent(sound.getLocation()) == null) {
            return false;
        }
        entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(),
            Mth.clamp(volume, 0.0F, 1.0F), 1.0F, false);
        return true;
    }

    /**
     * Returns the loaded frames of an effect from the particle atlas, in play order
     *
     * <p>Read on each effect (a few map lookups), so it always matches the last resource reload.</p>
     */
    @SuppressWarnings("deprecation") // LOCATION_PARTICLES is the particle atlas id the game itself uses in 1.21.1
    private static List<TextureAtlasSprite> frames(String effect) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager()
            .getTexture(TextureAtlas.LOCATION_PARTICLES);
        List<TextureAtlasSprite> frames = new ArrayList<>();
        if (texture instanceof TextureAtlas atlas) {
            for (int i = 0; i < CosmeticsFiles.MAX_FRAMES; i++) {
                TextureAtlasSprite sprite = atlas.getSprite(CosmeticsPack.frame(effect, i));
                if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                    break;
                }
                frames.add(sprite);
            }
        }
        return frames;
    }
}
