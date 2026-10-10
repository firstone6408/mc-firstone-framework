package io.github.firstone.framework.features.cosmetics;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plays Cosmetics sounds as mono 3D sounds and skips the start of a sound
 *
 * <p>The game only places <b>mono</b> sounds in the world (direction and distance); a stereo file plays straight
 * into both ears. So, while a Cosmetics sound loads, its decoded samples are mixed down to mono and, if asked, the
 * first part is dropped. Other sounds of the game are never changed.</p>
 *
 * <p>The processing is chosen by the sound's name: {@link #instance} plays a variant of the file named
 * {@code cosmetics/<sound>/v_m<0|1>_s<milliseconds>}, and {@code CosmeticAudioMixin} opens the real file for such a
 * name ({@link #file}) and processes it. Each setting gets its own name, so a changed setting never reuses a sound
 * the game has cached with the old one.</p>
 */
public final class CosmeticAudio {

    /** Last part of a variant path, e.g. {@code v_m1_s380.ogg} (mono, skip 380 ms) */
    private static final Pattern VARIANT = Pattern.compile("v_m([01])_s(\\d{1,6})\\.ogg");

    /** Bytes per sample: the game decodes OGG to 16-bit samples */
    private static final int SAMPLE_BYTES = 2;

    private CosmeticAudio() {}

    /**
     * Creates a sound to play at a position, as {@code ClientLevel.playLocalSound} does, with Cosmetics processing
     *
     * @param event  sound event id of the pack (e.g. {@code firstone-framework:cosmetics.death})
     * @param source sound category (sets which volume slider applies)
     * @param volume volume; above 1 makes the sound heard farther (the jukebox uses 4)
     * @param x      position x
     * @param y      position y
     * @param z      position z
     * @param mono   mix the sound down to mono, so it is placed in the world
     * @param skipMs milliseconds to skip at the start
     * @return the sound, to play with {@code SoundManager.play}
     */
    public static SimpleSoundInstance instance(ResourceLocation event, SoundSource source, float volume, double x,
                                               double y, double z, boolean mono, int skipMs) {
        return new SimpleSoundInstance(event, source, volume, 1.0F, SoundInstance.createUnseededRandom(), false, 0,
            SoundInstance.Attenuation.LINEAR, x, y, z, false) {
            @Override
            public WeighedSoundEvents resolve(SoundManager soundManager) {
                WeighedSoundEvents events = super.resolve(soundManager);
                this.sound = variant(this.sound, mono, skipMs);
                return events;
            }
        };
    }

    /** Returns the sound renamed to the variant that carries the processing, or the sound itself if none is needed */
    private static Sound variant(Sound sound, boolean mono, int skipMs) {
        ResourceLocation location = sound.getLocation();
        if ((!mono && skipMs <= 0) || !CosmeticsPack.NAMESPACE.equals(location.getNamespace())
            || !location.getPath().startsWith("cosmetics/")) {
            return sound;
        }
        ResourceLocation renamed = location.withSuffix("/v_m" + (mono ? 1 : 0) + "_s" + Math.max(0, skipMs));
        return new Sound(renamed, sound.getVolume(), sound.getPitch(), sound.getWeight(), sound.getType(),
            sound.shouldStream(), sound.shouldPreload(), sound.getAttenuationDistance());
    }

    /**
     * Returns the file to open for a sound path: the real file for a variant, else the path itself
     *
     * <p>The game opens sound files only from the list of files it found at the last resource reload, which has
     * no variant names, so a variant must be opened under its file's name.</p>
     *
     * @param location a sound path, e.g. {@code firstone-framework:sounds/cosmetics/death/v_m1_s0.ogg}
     * @return the file's path ({@code firstone-framework:sounds/cosmetics/death.ogg}), or {@code location}
     */
    public static ResourceLocation file(ResourceLocation location) {
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        if (!CosmeticsPack.NAMESPACE.equals(location.getNamespace()) || slash <= 0
            || !path.startsWith("sounds/cosmetics/") || !VARIANT.matcher(path.substring(slash + 1)).matches()) {
            return location;
        }
        return location.withPath(path.substring(0, slash) + ".ogg");
    }

    /**
     * Processes a sound loaded at once (short sounds); called for every sound buffer the game builds
     *
     * @param location the loaded file, e.g. {@code firstone-framework:sounds/cosmetics/death/v_m1_s0.ogg}
     * @param samples  decoded 16-bit samples
     * @param format   their format
     * @param create   the game's {@code SoundBuffer} constructor
     * @return the buffer, processed if {@code location} is a Cosmetics variant
     */
    public static SoundBuffer buffer(ResourceLocation location, ByteBuffer samples, AudioFormat format,
                                     BiFunction<ByteBuffer, AudioFormat, SoundBuffer> create) {
        Processor processor = Processor.of(location, format);
        if (processor == null) {
            return create.apply(samples, format);
        }
        ByteBuffer processed = processor.process(samples);
        if (!processed.hasRemaining()) {
            // everything was skipped: one silent sample, as an empty sound cannot be played
            processed = BufferUtils.createByteBuffer(processor.format.getFrameSize());
        }
        return create.apply(processed, processor.format);
    }

    /**
     * Processes a sound read while playing (songs); called for every stream the game opens
     *
     * @param location the opened file
     * @param stream   the game's stream
     * @return a processing stream if {@code location} is a Cosmetics variant, else {@code stream}
     */
    public static AudioStream stream(ResourceLocation location, AudioStream stream) {
        Processor processor = Processor.of(location, stream.getFormat());
        return processor == null ? stream : new ProcessedStream(stream, processor);
    }

    /** Mixes down to mono and skips the start of 16-bit samples, keeping its place across stream reads */
    private static final class Processor {

        private final boolean mono;
        private final int channels;
        private final AudioFormat format;
        private long bytesToSkip;

        private Processor(boolean mono, int channels, AudioFormat format, long bytesToSkip) {
            this.mono = mono;
            this.channels = channels;
            this.format = format;
            this.bytesToSkip = bytesToSkip;
        }

        /** Returns the processor of a variant file, or null if the file needs none (or is not 16-bit) */
        @Nullable
        private static Processor of(ResourceLocation location, AudioFormat format) {
            if (!CosmeticsPack.NAMESPACE.equals(location.getNamespace())
                || format.getSampleSizeInBits() != SAMPLE_BYTES * 8 || format.getChannels() < 1) {
                return null;
            }
            String path = location.getPath();
            Matcher matcher = VARIANT.matcher(path.substring(path.lastIndexOf('/') + 1));
            if (!path.startsWith("sounds/cosmetics/") || !matcher.matches()) {
                return null;
            }
            int channels = format.getChannels();
            boolean mono = matcher.group(1).equals("1") && channels > 1;
            long frames = (long) (Long.parseLong(matcher.group(2)) / 1000.0 * format.getSampleRate());
            AudioFormat out = mono
                ? new AudioFormat(format.getSampleRate(), SAMPLE_BYTES * 8, 1, true, format.isBigEndian())
                : format;
            return new Processor(mono, channels, out, frames * channels * SAMPLE_BYTES);
        }

        /**
         * Processes the next samples
         *
         * @param samples whole frames (every channel of each sample), as the decoder returns them
         * @return the processed samples, from position 0 (may be empty while skipping)
         */
        private ByteBuffer process(ByteBuffer samples) {
            if (this.bytesToSkip > 0) {
                int skipped = (int) Math.min(this.bytesToSkip, samples.remaining());
                samples.position(samples.position() + skipped);
                this.bytesToSkip -= skipped;
            }
            if (!this.mono) {
                return samples.slice().order(samples.order());
            }
            int frames = samples.remaining() / (this.channels * SAMPLE_BYTES);
            ByteBuffer out = BufferUtils.createByteBuffer(frames * SAMPLE_BYTES).order(samples.order());
            for (int frame = 0; frame < frames; frame++) {
                int sum = 0;
                for (int channel = 0; channel < this.channels; channel++) {
                    sum += samples.getShort();
                }
                out.putShort((short) (sum / this.channels));
            }
            return out.flip();
        }
    }

    /** Stream that processes the samples of the game's stream as they are read */
    private static final class ProcessedStream implements AudioStream {

        private final AudioStream source;
        private final Processor processor;

        private ProcessedStream(AudioStream source, Processor processor) {
            this.source = source;
            this.processor = processor;
        }

        @Override
        public AudioFormat getFormat() {
            return this.processor.format;
        }

        @Override
        public ByteBuffer read(int size) throws IOException {
            int sourceSize = this.processor.mono ? size * this.processor.channels : size;
            while (true) {
                ByteBuffer samples = this.source.read(sourceSize);
                if (!samples.hasRemaining()) {
                    return samples;
                }
                ByteBuffer processed = this.processor.process(samples);
                if (processed.hasRemaining()) {
                    return processed;
                }
                // the whole read was skipped: read on
            }
        }

        @Override
        public void close() throws IOException {
            this.source.close();
        }
    }
}
