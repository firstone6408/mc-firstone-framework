package io.github.firstone.framework.devtest.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the sounds the client plays, from {@link TestContext#recordSounds} until the end of the test
 *
 * <p>Sounds played at volume 0 are not recorded: the game drops them before telling its listeners.</p>
 */
public final class SoundRecorder implements SoundEventListener {

    /**
     * One played sound
     *
     * @param event  the sound event id, e.g. {@code minecraft:entity.pig.death}
     * @param sound  the sound file the event chose, e.g. {@code minecraft:mob/pig/death1}
     * @param volume the volume it was played with
     * @param source the sound category (which volume slider applies)
     * @param tick   the local player's tick count when it was played ({@code LocalPlayer.tickCount})
     * @param pos    where it was played
     * @param placed whether it is placed in the world (linear attenuation, not relative to the listener)
     */
    public record Played(String event, String sound, float volume, SoundSource source, int tick, Vec3 pos,
                         boolean placed) {}

    private final List<Played> sounds = new ArrayList<>();

    SoundRecorder() {}

    @Override
    public void onPlaySound(SoundInstance sound, WeighedSoundEvents events, float range) {
        Minecraft minecraft = Minecraft.getInstance();
        int tick = minecraft.player == null ? -1 : minecraft.player.tickCount;
        this.sounds.add(new Played(sound.getLocation().toString(), sound.getSound().getLocation().toString(),
            sound.getVolume(), sound.getSource(), tick, new Vec3(sound.getX(), sound.getY(), sound.getZ()),
            sound.getAttenuation() == SoundInstance.Attenuation.LINEAR && !sound.isRelative()));
    }

    /**
     * Counts the recorded sounds whose event id contains a text
     *
     * @param part part of the sound event id, e.g. {@code splash}
     * @return how many were played
     */
    public int count(String part) {
        return (int) this.sounds.stream().filter(sound -> sound.event().contains(part)).count();
    }

    /**
     * Returns the recorded sounds whose event id contains a text, in the order they were played
     *
     * @param part part of the sound event id
     * @return the sounds
     */
    public List<Played> played(String part) {
        return this.sounds.stream().filter(sound -> sound.event().contains(part)).toList();
    }

    /** Forgets the sounds recorded so far */
    public void clear() {
        this.sounds.clear();
    }
}
