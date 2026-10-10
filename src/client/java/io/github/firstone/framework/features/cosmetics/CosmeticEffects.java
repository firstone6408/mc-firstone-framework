package io.github.firstone.framework.features.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

    /** Latest death sound start, in seconds before the body disappears (19 of the 20 ticks of the death) */
    public static final float MAX_SOUND_LEAD = 0.95F;

    /** Longest skip at the start of the item break sound, in seconds */
    public static final float MAX_SOUND_SKIP = 10.0F;

    /** Ticks between the death and the body disappearing (the game's {@code tickDeath}) */
    private static final int DEATH_TICKS = 20;

    private static final ResourceLocation DEATH_SOUND = CosmeticsPack.soundEvent(CosmeticsFiles.DEATH);
    private static final ResourceLocation ITEM_BREAK_SOUND = CosmeticsPack.soundEvent(CosmeticsFiles.ITEM_BREAK);

    /** Volume and attenuation of a jukebox song ({@code SimpleSoundInstance.forJukeboxSong}) */
    private static final float JUKEBOX_VOLUME = 4.0F;

    /**
     * Last entity that got the death effect, so the local player does not get it twice (the death is seen both by
     * the player's own death timer and by the server's message); weak, so a removed entity is not kept in memory
     */
    private static WeakReference<LivingEntity> lastDeath = new WeakReference<>(null);

    private CosmeticEffects() {}

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

    /**
     * Returns the song a jukebox plays for a disc: the song file chosen for it, if the game has loaded that file
     *
     * <p>Played like the game's jukebox song ({@code SimpleSoundInstance.forJukeboxSong}): records volume slider,
     * heard up to 64 blocks away.</p>
     *
     * @param song the disc's song (its id, e.g. {@code minecraft:cat}, is the key of the chosen file)
     * @param pos  center of the jukebox
     * @return the chosen song, or null to play the game's song
     */
    @Nullable
    public static SimpleSoundInstance musicDisc(Holder<JukeboxSong> song, Vec3 pos) {
        CosmeticsConfig.MusicDiscs config = CosmeticsFeature.getConfig().musicDiscs;
        if (!config.enabled) {
            return null;
        }
        String file = song.unwrapKey().map(key -> config.songs.get(key.location().toString())).orElse(null);
        ResourceLocation event = file != null ? CosmeticsPack.songEvent(file) : null;
        if (event == null || Minecraft.getInstance().getSoundManager().getSoundEvent(event) == null) {
            // no song chosen, or the file was not there at the last resource reload
            return null;
        }
        return CosmeticAudio.instance(event, SoundSource.RECORDS, JUKEBOX_VOLUME, pos.x, pos.y, pos.z,
            config.sound3d, 0);
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
    private static boolean playSound(LivingEntity entity, ResourceLocation event, float volume, boolean mono,
                                     int skipMs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager().getSoundEvent(event) == null) {
            return false;
        }
        minecraft.getSoundManager().play(CosmeticAudio.instance(event, entity.getSoundSource(),
            Mth.clamp(volume, 0.0F, 1.0F), entity.getX(), entity.getY(), entity.getZ(), mono, skipMs));
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
