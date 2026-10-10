package io.github.firstone.framework.devtest.core;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the sounds the client plays, from {@link TestContext#recordSounds} until the end of the test
 */
public final class SoundRecorder implements SoundEventListener {

    private final List<String> sounds = new ArrayList<>();

    SoundRecorder() {}

    @Override
    public void onPlaySound(SoundInstance sound, WeighedSoundEvents events, float range) {
        this.sounds.add(sound.getLocation().toString());
    }

    /**
     * Counts the recorded sounds whose id contains a text
     *
     * @param part part of the sound event id, e.g. {@code splash}
     * @return how many were played
     */
    public int count(String part) {
        return (int) this.sounds.stream().filter(sound -> sound.contains(part)).count();
    }

    /** Forgets the sounds recorded so far */
    public void clear() {
        this.sounds.clear();
    }
}
