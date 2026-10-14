package io.github.firstone.framework.features.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Cosmetics effects share: the size and duration limits, who an effect applies to, playing a Cosmetics
 * sound and the loaded particle frames
 *
 * <p>The effects themselves are in their own folders: {@code death/} ({@code DeathEffect}), {@code itembreak/}
 * ({@code ItemBreakEffect}) and {@code musicdiscs/} ({@code MusicDiscs}).</p>
 */
public final class CosmeticEffects {

    /** Smallest size / duration multiplier (slider minimum) */
    public static final float MIN_SCALE = 0.25F;

    /** Largest size / duration / speed multiplier (slider maximum) */
    public static final float MAX_SCALE = 4.0F;

    private CosmeticEffects() {}

    /**
     * Tells whether an effect is on for this entity: players and mobs have their own option, and only the client
     * plays effects (in singleplayer the server shares the game's classes)
     *
     * @param entity  the entity of the effect
     * @param players the effect's option for players
     * @param mobs    the effect's option for mobs
     * @return true if the effect plays for this entity
     */
    public static boolean appliesTo(LivingEntity entity, boolean players, boolean mobs) {
        return entity.level().isClientSide() && (entity instanceof Player ? players : mobs);
    }

    /**
     * Plays a Cosmetics sound at the entity, in the entity's sound category, if its file is loaded
     *
     * @param entity the entity the sound plays at
     * @param event  the sound event
     * @param volume the volume, from 0 to 1
     * @param mono   mix it to mono (3D sound)
     * @param skipMs milliseconds to skip at the start
     * @return true if the sound exists (it is played even at volume 0, which mutes the game's sound)
     */
    public static boolean playSound(LivingEntity entity, ResourceLocation event, float volume, boolean mono,
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
     *
     * @param effect the effect's folder name ({@link CosmeticsFiles#DEATH}, {@link CosmeticsFiles#ITEM_BREAK})
     * @return the frames, empty when the effect has none
     */
    @SuppressWarnings("deprecation") // LOCATION_PARTICLES is the particle atlas id the game itself uses in 1.21.1
    public static List<TextureAtlasSprite> frames(String effect) {
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
