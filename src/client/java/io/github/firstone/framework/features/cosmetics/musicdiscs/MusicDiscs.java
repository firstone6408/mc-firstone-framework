package io.github.firstone.framework.features.cosmetics.musicdiscs;

import io.github.firstone.framework.features.cosmetics.CosmeticAudio;
import io.github.firstone.framework.features.cosmetics.CosmeticsConfig;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import io.github.firstone.framework.features.cosmetics.CosmeticsPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Cosmetics music discs: the song file the player chose for a disc plays instead of the disc's song
 *
 * <p>Called by the mixin in {@code client/mixin/cosmetics/musicdiscs/}. Without a chosen song (or with the option
 * off, or a missing file), the disc's own song plays.</p>
 */
public final class MusicDiscs {

    /** Volume and attenuation of a jukebox song ({@code SimpleSoundInstance.forJukeboxSong}) */
    private static final float JUKEBOX_VOLUME = 4.0F;

    private MusicDiscs() {}

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
}
