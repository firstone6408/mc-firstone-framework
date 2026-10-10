package io.github.firstone.framework.devtest.features.cosmetics;

import com.mojang.blaze3d.audio.SoundBuffer;
import io.github.firstone.framework.devtest.core.FeatureTests;
import io.github.firstone.framework.devtest.core.SoundRecorder;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.features.cosmetics.CosmeticsFixture.FileSet;
import io.github.firstone.framework.devtest.mixin.SoundBufferAccessor;
import io.github.firstone.framework.devtest.mixin.SoundEngineAccessor;
import io.github.firstone.framework.devtest.mixin.SoundManagerAccessor;
import io.github.firstone.framework.features.cosmetics.CosmeticsConfig;
import io.github.firstone.framework.features.cosmetics.CosmeticsFeature;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tests of Cosmetics: custom death effect, item break effect and music disc songs from files
 *
 * <p>The tests make their own files ({@link CosmeticsFixture}) and check what the player sees and hears: the custom
 * particles (count, and size, duration and speed on average over many particles) and the sounds the game plays. A
 * Cosmetics sound's settings show in the name of the sound file the game plays: {@code …/v_m1_s1500} is mixed to
 * mono and skips 1500 ms. The sound processing itself is tested with made-up samples.</p>
 */
public final class CosmeticsTests implements FeatureTests {

    /** Sound events of the custom effects */
    private static final String DEATH_EVENT = "firstone-framework:cosmetics.death";
    private static final String ITEM_BREAK_EVENT = "firstone-framework:cosmetics.item_break";

    /** Sound files the custom effects play (before the processing variant) */
    private static final String DEATH_SOUND = "firstone-framework:cosmetics/death";
    private static final String ITEM_BREAK_SOUND = "firstone-framework:cosmetics/item_break";

    /** Where the test mobs stand, and where the player watches from */
    private static final double MOB_X = 4.5;
    private static final double MOB_Z = 8.5;

    @Override
    public String id() {
        return "cosmetics";
    }

    @Override
    public List<Object> configs() {
        return List.of(CosmeticsFeature.getConfig());
    }

    @Override
    public List<TestCase> tests() {
        return List.of(
            // sound processing (made-up samples)
            TestCase.of("Sound processing: a stereo sound is mixed to mono", this::mixToMono),
            TestCase.of("Sound processing: the start is skipped; skipping everything leaves one silent sample",
                this::skipStart),
            TestCase.of("Sound processing: other sounds and other sample formats are not changed", this::untouched),
            TestCase.of("Sound processing: streams (songs) skip across reads and mix to mono", this::streams),
            TestCase.of("Sound files: a processed sound opens its real file", this::fileNames),
            TestCase.of("3D Sound: the real stereo file is loaded as mono; off: stereo; Sound Skip cuts the start",
                this::loadedSound),
            // death
            TestCase.of("Death: a mob's body disappears with the custom particles and the 3D sound", this::death),
            TestCase.slow("Death: particle count, size, duration and speed follow the settings", this::deathParticles),
            TestCase.of("Death: Sound Lead starts the sound early; volume and 3D follow the settings",
                this::deathSound),
            TestCase.of("Death: Mobs off or the effect off gives the game's poof", this::deathMobsOff),
            TestCase.of("Death: the player's own death plays the effect once; Players off: the game's",
                this::playerDeath),
            // item break
            TestCase.of("Item break: a breaking tool shows the custom particles and sound", this::itemBreak),
            TestCase.of("Item break: count, size, duration, Sound Skip, volume and 3D follow the settings",
                this::itemBreakSettings),
            TestCase.of("Item break: Mobs, Players and the effect off give the game's effect", this::itemBreakOff),
            TestCase.of("Item break: volume 0 mutes the game's sound", this::itemBreakMuted),
            // music discs
            TestCase.of("Music discs: the chosen song plays from the jukebox instead of the disc's", this::song),
            TestCase.of("Music discs: no song chosen or the option off plays the disc's own song", this::songOff),
            // missing files
            TestCase.of("Files: without frames the game's particles show; without a sound the game's sound plays",
                this::missingFiles),
            // settings
            TestCase.of("Settings: the main screen and the three category screens open", this::screens)
        );
    }

    @Override
    public void cleanUp(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.NONE);
    }

    private static CosmeticsConfig config() {
        return CosmeticsFeature.getConfig();
    }

    // ---- sound processing -----------------------------------------------------------------------------------------

    private static ResourceLocation variant(String effect, boolean mono, int skipMs) {
        return ResourceLocation.fromNamespaceAndPath("firstone-framework",
            "sounds/cosmetics/" + effect + "/v_m" + (mono ? 1 : 0) + "_s" + skipMs + ".ogg");
    }

    /** 16-bit little-endian frames: every frame holds the given value for each channel */
    private static ByteBuffer frames(int count, short... channels) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(count * channels.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            for (short value : channels) {
                buffer.putShort(value);
            }
        }
        return buffer.flip();
    }

    private static AudioFormat format(int channels) {
        return new AudioFormat(1000.0F, 16, channels, true, false);
    }

    private void mixToMono(TestContext t) {
        CosmeticsInternals.Processed mono = CosmeticsInternals.processBuffer(variant("death", true, 0),
            frames(200, (short) 1000, (short) 3000), format(2));
        t.check("channels after mixing", mono.format().getChannels()).isEqualTo(1);
        t.check("frames after mixing", mono.samples().remaining() / 2).isEqualTo(200);
        t.check("first sample (average of 1000 and 3000)", (int) mono.samples().getShort(0)).isEqualTo(2000);
        CosmeticsInternals.Processed negative = CosmeticsInternals.processBuffer(variant("death", true, 0),
            frames(10, (short) -32768, (short) 32767), format(2));
        t.check("average of the extremes", (int) negative.samples().getShort(0)).isEqualTo(0);
        CosmeticsInternals.Processed already = CosmeticsInternals.processBuffer(variant("death", true, 0),
            frames(10, (short) 500), format(1));
        t.check("mono stays mono", already.format().getChannels()).isEqualTo(1);
        t.check("mono samples kept", (int) already.samples().getShort(0)).isEqualTo(500);
    }

    private void skipStart(TestContext t) {
        // 1000 frames per second: 100 ms are 100 frames
        CosmeticsInternals.Processed skipped = CosmeticsInternals.processBuffer(variant("item_break", false, 100),
            frames(300, (short) 7, (short) 9), format(2));
        t.check("frames left after skipping 100 ms of 300", skipped.samples().remaining() / 4).isEqualTo(200);
        t.check("still stereo", skipped.format().getChannels()).isEqualTo(2);
        CosmeticsInternals.Processed both = CosmeticsInternals.processBuffer(variant("item_break", true, 50),
            frames(300, (short) 100, (short) 300), format(2));
        t.check("skip and mix: frames left", both.samples().remaining() / 2).isEqualTo(250);
        t.check("skip and mix: sample", (int) both.samples().getShort(both.samples().position())).isEqualTo(200);
        CosmeticsInternals.Processed all = CosmeticsInternals.processBuffer(variant("item_break", true, 5000),
            frames(300, (short) 100, (short) 300), format(2));
        t.check("everything skipped: one sample left", all.samples().remaining()).isEqualTo(2);
        t.check("everything skipped: it is silent", (int) all.samples().getShort(0)).isEqualTo(0);
    }

    private void untouched(TestContext t) {
        ByteBuffer samples = frames(50, (short) 1, (short) 2);
        CosmeticsInternals.Processed other = CosmeticsInternals.processBuffer(
            ResourceLocation.withDefaultNamespace("sounds/random/pop.ogg"), samples, format(2));
        t.check("a game sound: same samples", other.samples() == samples).isTrue();
        t.check("a game sound: still stereo", other.format().getChannels()).isEqualTo(2);
        ByteBuffer plain = frames(50, (short) 1, (short) 2);
        CosmeticsInternals.Processed unprocessed = CosmeticsInternals.processBuffer(
            ResourceLocation.fromNamespaceAndPath("firstone-framework", "sounds/cosmetics/death.ogg"), plain,
            format(2));
        t.check("the Cosmetics file itself (no variant): same samples", unprocessed.samples() == plain).isTrue();
        ByteBuffer eightBit = ByteBuffer.allocateDirect(100);
        CosmeticsInternals.Processed bytes = CosmeticsInternals.processBuffer(variant("death", true, 10), eightBit,
            new AudioFormat(1000.0F, 8, 2, true, false));
        t.check("8-bit samples: not changed", bytes.samples() == eightBit).isTrue();
    }

    /** A stream that returns the given frames in reads of at most {@code chunk} bytes */
    private static AudioStream fakeStream(ByteBuffer all, AudioFormat format, int chunk) {
        return new AudioStream() {
            @Override
            public AudioFormat getFormat() {
                return format;
            }

            @Override
            public ByteBuffer read(int size) {
                int length = Math.min(Math.min(size, chunk), all.remaining());
                ByteBuffer part = all.slice(all.position(), length).order(all.order());
                all.position(all.position() + length);
                return part;
            }

            @Override
            public void close() {
            }
        };
    }

    private void streams(TestContext t) throws IOException {
        // 1000 stereo frames of (100, 300), read 40 frames at a time; skip 250 ms = 250 frames
        AudioStream stream = CosmeticsInternals.processStream(variant("song/devtest", true, 250),
            fakeStream(frames(1000, (short) 100, (short) 300), format(2), 160));
        t.check("stream format: mono", stream.getFormat().getChannels()).isEqualTo(1);
        int samples = 0;
        boolean allMixed = true;
        while (true) {
            ByteBuffer read = stream.read(80);
            if (!read.hasRemaining()) {
                break;
            }
            while (read.hasRemaining()) {
                allMixed &= read.getShort() == 200;
                samples++;
            }
        }
        t.check("samples read after skipping 250 of 1000 frames", samples).isEqualTo(750);
        t.check("every sample mixed to the average", allMixed).isTrue();
        AudioStream plain = fakeStream(frames(10, (short) 1), format(1), 100);
        t.check("a game stream is not wrapped", CosmeticsInternals.processStream(
            ResourceLocation.withDefaultNamespace("sounds/records/cat.ogg"), plain) == plain).isTrue();
    }

    private void fileNames(TestContext t) {
        t.check("variant of the death sound", CosmeticsInternals.fileOf(variant("death", true, 380)).toString())
            .isEqualTo("firstone-framework:sounds/cosmetics/death.ogg");
        t.check("variant of a song", CosmeticsInternals.fileOf(variant("song/abc_12", false, 0)).toString())
            .isEqualTo("firstone-framework:sounds/cosmetics/song/abc_12.ogg");
        ResourceLocation game = ResourceLocation.withDefaultNamespace("sounds/cosmetics/death/v_m1_s0.ogg");
        t.check("another namespace is not mapped", CosmeticsInternals.fileOf(game) == game).isTrue();
        ResourceLocation plain = ResourceLocation.fromNamespaceAndPath("firstone-framework",
            "sounds/cosmetics/death.ogg");
        t.check("a plain file is not mapped", CosmeticsInternals.fileOf(plain) == plain).isTrue();
    }

    /** Loads a Cosmetics sound file like the game does when it plays it; returns {channels, bytes of samples} */
    private static int[] load(TestContext t, String path) {
        SoundBufferLibrary library = ((SoundEngineAccessor) ((SoundManagerAccessor) t.mc().getSoundManager())
            .devtest$getSoundEngine()).devtest$getSoundBuffers();
        CompletableFuture<SoundBuffer> loading = t.onClientThread(() -> library.getCompleteBuffer(
            ResourceLocation.fromNamespaceAndPath("firstone-framework", path)));
        t.waitUntil("the sound loads", loading::isDone, 200);
        SoundBufferAccessor buffer = (SoundBufferAccessor) loading.join();
        ByteBuffer data = buffer.devtest$getData();
        t.require(data != null, "The sound " + path + " was already played");
        return new int[] {buffer.devtest$getFormat().getChannels(), data.remaining()};
    }

    private void loadedSound(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        // names no other test plays: a played sound gives its samples to the sound card
        int[] stereo = load(t, "sounds/cosmetics/death/v_m0_s3.ogg");
        int[] mono = load(t, "sounds/cosmetics/death/v_m1_s3.ogg");
        int[] skipped = load(t, "sounds/cosmetics/death/v_m0_s253.ogg");
        t.check("the test file is stereo (3D off keeps it)", stereo[0]).isEqualTo(2);
        t.check("3D Sound: loaded as mono", mono[0]).isEqualTo(1);
        t.check("3D Sound: same length (frames)", mono[1] / 2).isEqualTo(stereo[1] / 4);
        // 250 ms more of 48000 frames per second, 4 bytes per stereo frame
        t.check("Sound Skip 0.25 s more: bytes cut", stereo[1] - skipped[1])
            .between(250 * 48 * 4 - 8, 250 * 48 * 4 + 8);
    }

    // ---- death ----------------------------------------------------------------------------------------------------

    /**
     * What happened around a death: particles, sounds and when ({@code deathTimeAtSound}: the dying entity's death
     * timer on the client at the tick the custom sound played, -1 if none)
     */
    private record Death(int custom, int vanilla, List<Particle> particles, Map<Particle, Vec3> start,
                         Map<Particle, Vec3> later, List<SoundRecorder.Played> sounds, int poofTick,
                         int deathTimeAtSound) {}

    private static Vec3 center(Particle particle) {
        return particle.getBoundingBox().getCenter();
    }

    /** Watches the custom and the game's poof particles and the dying entity for 40 ticks after a death started */
    private static Death watchDeath(TestContext t, SoundRecorder sounds, Supplier<LivingEntity> dying) {
        Class<? extends Particle> custom = CosmeticsInternals.particleClass();
        int most = 0;
        int mostVanilla = 0;
        int poofTick = -1;
        List<Particle> first = List.of();
        Map<Particle, Vec3> start = new IdentityHashMap<>();
        Map<Particle, Vec3> later = new IdentityHashMap<>();
        int deathTimeAtSound = -1;
        for (int i = 0; i < 40; i++) {
            t.tick();
            LivingEntity entity = dying.get();
            if (deathTimeAtSound < 0 && entity != null && !sounds.played(DEATH_EVENT).isEmpty()) {
                // the early sound starts at the end of the entity's tickDeath, after its timer moved
                deathTimeAtSound = entity.deathTime;
            }
            List<? extends Particle> now = t.particles(custom);
            int vanilla = t.particleCount(ExplodeParticle.class);
            if (poofTick < 0 && (!now.isEmpty() || vanilla > 0)) {
                poofTick = t.player().tickCount;
                first = new ArrayList<>(now);
                now.forEach(particle -> start.put(particle, center(particle)));
            } else if (poofTick >= 0 && later.isEmpty() && t.player().tickCount - poofTick == 4) {
                first.stream().filter(particle -> particle.isAlive())
                    .forEach(particle -> later.put(particle, center(particle)));
            }
            most = Math.max(most, now.size());
            mostVanilla = Math.max(mostVanilla, vanilla);
        }
        return new Death(most, mostVanilla, first, start, later, sounds.played(DEATH_EVENT), poofTick,
            deathTimeAtSound);
    }

    /** Spawns a pig in front of the player and kills it; returns what was seen and heard */
    private static Death killPig(TestContext t) {
        t.teleport(MOB_X, 1.0, MOB_Z - 4.0, 0.0F, 15.0F);
        Spawned<Pig> pig = t.spawn(EntityType.PIG, MOB_X, 0.0, MOB_Z, 180.0F, entity -> entity.setNoAi(true));
        t.ticks(5);
        t.clearParticles();
        SoundRecorder sounds = t.recordSounds();
        pig.server(entity -> {
            entity.kill();
            return null;
        });
        Death death = watchDeath(t, sounds, pig::client);
        t.removeEntities();
        return death;
    }

    private void death(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        Death death = killPig(t);
        t.check("custom particles (Particle Count 20)", death.custom()).isEqualTo(20);
        t.check("the game's poof particles", death.vanilla()).isEqualTo(0);
        t.check("death sounds played", death.sounds().size()).isEqualTo(1);
        if (!death.sounds().isEmpty()) {
            SoundRecorder.Played sound = death.sounds().get(0);
            t.check("sound event", sound.event()).isEqualTo(DEATH_EVENT);
            t.check("sound file: 3D (mono variant)", sound.sound()).isEqualTo(DEATH_SOUND + "/v_m1_s0");
            t.check("volume", (double) sound.volume()).near(1.0, 1.0E-6);
            t.check("category: the pig's (neutral)", sound.source()).isEqualTo(SoundSource.NEUTRAL);
            t.check("placed in the world (3D)", sound.placed()).isTrue();
            t.check("played where the pig was (distance)", sound.pos().distanceTo(new Vec3(
                TestSite.ORIGIN.getX() + MOB_X, TestSite.ORIGIN.getY(), TestSite.ORIGIN.getZ() + MOB_Z))).atMost(0.01);
            t.check("played when the body disappears (ticks before the poof)", death.poofTick() - sound.tick())
                .between(-1, 1);
        }
    }

    /** Every particle's size must be from {@code min} to {@code max} (the game's range times the setting) */
    private static void checkSizes(TestContext t, String name, List<Particle> particles, double min, double max) {
        t.require(!particles.isEmpty(), name + ": no particles");
        double smallest = particles.stream().mapToDouble(p -> ((SingleQuadParticle) p).getQuadSize(0.0F)).min()
            .orElse(0);
        double largest = particles.stream().mapToDouble(p -> ((SingleQuadParticle) p).getQuadSize(0.0F)).max()
            .orElse(0);
        t.check(name + ": smallest size", smallest).atLeast(min - 1.0E-4);
        t.check(name + ": largest size", largest).atMost(max + 1.0E-4);
    }

    /**
     * Every particle's lifetime must be the game's lifetime times {@code factor}: a multiple of it, from {@code min}
     * to {@code max} ticks
     */
    private static void checkLifetimes(TestContext t, String name, List<Particle> particles, int factor, int min,
                                       int max) {
        t.require(!particles.isEmpty(), name + ": no particles");
        t.check(name + ": every lifetime a multiple of " + factor,
            particles.stream().allMatch(p -> p.getLifetime() % factor == 0)).isTrue();
        t.check(name + ": shortest lifetime", particles.stream().mapToInt(p -> p.getLifetime()).min().orElse(0))
            .atLeast(min);
        t.check(name + ": longest lifetime", particles.stream().mapToInt(p -> p.getLifetime()).max().orElse(0))
            .atMost(max);
    }

    /** Average sideways distance the particles moved in their first 4 ticks */
    private static double averageSpread(Death death) {
        return death.later().entrySet().stream().mapToDouble(entry -> {
            Vec3 from = death.start().get(entry.getKey());
            return from == null ? 0.0 : Math.hypot(entry.getValue().x - from.x, entry.getValue().z - from.z);
        }).average().orElse(0);
    }

    private void deathParticles(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        config().death.particleCount = 37;
        t.check("Particle Count 37", killPig(t).custom()).isEqualTo(37);
        config().death.particleCount = 500;
        t.check("Particle Count 500 is limited to 100", killPig(t).custom()).isEqualTo(100);
        config().death.particleCount = 0;
        t.check("Particle Count 0 shows at least 1", killPig(t).custom()).isEqualTo(1);

        // the game's poof: size 0.1 to 0.7, lifetime 18 to 82 ticks (LivingEntity.makePoofParticles)
        config().death.particleCount = 100;
        Death normal = killPig(t);
        checkSizes(t, "Particle Size 1", normal.particles(), 0.1, 0.7);
        checkLifetimes(t, "Particle Duration 1", normal.particles(), 1, 18, 82);
        config().death.particleSize = 3.0F;
        config().death.particleDuration = 3.0F;
        config().death.particleSpeed = 2.0F;
        Death tripled = killPig(t);
        checkSizes(t, "Particle Size 3", tripled.particles(), 0.3, 2.1);
        checkLifetimes(t, "Particle Duration 3", tripled.particles(), 3, 54, 246);
        t.check("Particle Speed 2: average spread, compared to 1", averageSpread(tripled) / averageSpread(normal))
            .between(1.5, 2.5);
        config().death.particleSpeed = 0.0F;
        t.check("Particle Speed 0: average sideways spread", averageSpread(killPig(t))).atMost(0.001);
    }

    private void deathSound(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        config().death.soundLead = 0.5F;
        config().death.soundVolume = 0.4F;
        config().death.sound3d = false;
        Death death = killPig(t);
        t.require(death.sounds().size() == 1, "Expected one death sound, got " + death.sounds().size());
        SoundRecorder.Played sound = death.sounds().get(0);
        t.check("Sound Lead 0.5 s: starts at death tick 10 of 20", death.deathTimeAtSound()).isEqualTo(10);
        t.note("ticks between the sound and the poof (server timing)", death.poofTick() - sound.tick());
        t.check("Sound Volume 0.4", (double) sound.volume()).near(0.4, 1.0E-6);
        t.check("3D Sound off: the file as it is (stereo kept)", sound.sound()).isEqualTo(DEATH_SOUND);
        config().death.soundLead = 5.0F;
        Death latest = killPig(t);
        t.require(latest.sounds().size() == 1, "Expected one death sound, got " + latest.sounds().size());
        t.check("Sound Lead 5 s is limited to 0.95 s: starts at death tick 1 of 20", latest.deathTimeAtSound())
            .isEqualTo(1);
    }

    private void deathMobsOff(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        config().death.mobs = false;
        Death mobsOff = killPig(t);
        t.check("Mobs off: custom particles", mobsOff.custom()).isEqualTo(0);
        t.check("Mobs off: the game's poof particles", mobsOff.vanilla()).atLeast(15);
        t.check("Mobs off: death sounds", mobsOff.sounds().size()).isEqualTo(0);
        config().death.mobs = true;
        config().death.enabled = false;
        Death off = killPig(t);
        t.check("off: custom particles", off.custom()).isEqualTo(0);
        t.check("off: the game's poof particles", off.vanilla()).atLeast(15);
        t.check("off: death sounds", off.sounds().size()).isEqualTo(0);
    }

    /** Kills the local player, watches the death and respawns */
    private static Death killPlayer(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(MOB_X, 0.0, MOB_Z, 0.0F, 0.0F);
        t.setFlying(false);
        t.ticks(5);
        t.clearParticles();
        SoundRecorder sounds = t.recordSounds();
        t.withServerPlayer(player -> player.kill());
        Death death = watchDeath(t, sounds, () -> t.mc().player);
        t.onClientThread(() -> {
            t.player().respawn();
            return null;
        });
        t.waitUntil("the player respawns", () -> t.mc().player != null && t.mc().player.isAlive()
            && t.mc().screen == null, 100);
        t.ticks(10);
        return death;
    }

    private void playerDeath(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        Death own = killPlayer(t);
        t.check("own death: custom particles (once, not twice)", own.custom()).isEqualTo(20);
        t.check("own death: the game's poof particles", own.vanilla()).isEqualTo(0);
        t.check("own death: death sounds (once)", own.sounds().size()).isEqualTo(1);
        config().death.players = false;
        Death playersOff = killPlayer(t);
        t.check("Players off: custom particles", playersOff.custom()).isEqualTo(0);
        // whether the game shows a poof for one's own death is a race in 1.21.1: the client removes its body at its
        // death tick 20, the server sends the poof at its own tick 20 (ignored once the body is gone)
        t.note("Players off: the game's poof particles (0 or 20, timing of the game)", playersOff.vanilla());
        t.check("Players off: death sounds", playersOff.sounds().size()).isEqualTo(0);
    }

    // ---- item break -----------------------------------------------------------------------------------------------

    /** What happened when an item broke */
    private record Break(int custom, int vanilla, List<Particle> particles, List<SoundRecorder.Played> customSounds,
                         List<SoundRecorder.Played> vanillaSounds) {}

    /** An iron pickaxe one use from breaking */
    private static ItemStack almostBroken() {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 1);
        return pickaxe;
    }

    /** Breaks the item in the main hand of an entity (on the server) and watches for 10 ticks */
    private static Break breakItem(TestContext t, int entityId) {
        t.clearParticles();
        SoundRecorder sounds = t.recordSounds();
        t.onServer(level -> {
            LivingEntity entity = (LivingEntity) level.getEntity(entityId);
            t.require(entity != null, "The entity is gone");
            entity.getMainHandItem().hurtAndBreak(1, entity, EquipmentSlot.MAINHAND);
            return null;
        });
        Class<? extends Particle> custom = CosmeticsInternals.particleClass();
        int most = 0;
        int mostVanilla = 0;
        List<Particle> first = List.of();
        for (int i = 0; i < 10; i++) {
            t.tick();
            List<? extends Particle> now = t.particles(custom);
            if (first.isEmpty() && !now.isEmpty()) {
                first = new ArrayList<>(now);
            }
            most = Math.max(most, now.size());
            mostVanilla = Math.max(mostVanilla, t.particleCount(BreakingItemParticle.class));
        }
        return new Break(most, mostVanilla, first, sounds.played(ITEM_BREAK_EVENT),
            sounds.played("minecraft:entity.item.break"));
    }

    /** Breaks a tool in the player's hand (survival: creative tools do not wear) */
    private static Break breakPlayerTool(TestContext t) {
        t.gameMode(GameType.SURVIVAL);
        t.teleport(MOB_X, 0.0, MOB_Z, 0.0F, 0.0F);
        t.setFlying(false);
        t.holdItem(almostBroken());
        t.ticks(5);
        return breakItem(t, t.player().getId());
    }

    private void itemBreak(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        Break result = breakPlayerTool(t);
        t.check("custom particles (Particle Count 5)", result.custom()).isEqualTo(5);
        t.check("the game's item crack particles", result.vanilla()).isEqualTo(0);
        t.check("custom sounds", result.customSounds().size()).isEqualTo(1);
        t.check("the game's break sounds", result.vanillaSounds().size()).isEqualTo(0);
        if (!result.customSounds().isEmpty()) {
            SoundRecorder.Played sound = result.customSounds().get(0);
            t.check("sound event", sound.event()).isEqualTo(ITEM_BREAK_EVENT);
            t.check("sound file: 3D, no skip", sound.sound()).isEqualTo(ITEM_BREAK_SOUND + "/v_m1_s0");
            t.check("volume", (double) sound.volume()).near(1.0, 1.0E-6);
            t.check("category: the player's", sound.source()).isEqualTo(SoundSource.PLAYERS);
            t.check("placed in the world (3D)", sound.placed()).isTrue();
            t.check("played where the player is (distance)", sound.pos().distanceTo(t.player().position()))
                .atMost(0.5);
        }
    }

    private void itemBreakSettings(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        // the game's item crack: size 0.05 to 0.1, lifetime 4 to 40 ticks (Particle, BreakingItemParticle)
        config().itemBreak.particleCount = 50;
        Break normal = breakPlayerTool(t);
        checkSizes(t, "Particle Size 1", normal.particles(), 0.05, 0.1);
        checkLifetimes(t, "Particle Duration 1", normal.particles(), 1, 4, 40);
        config().itemBreak.particleCount = 12;
        config().itemBreak.particleSize = 3.0F;
        config().itemBreak.particleDuration = 3.0F;
        config().itemBreak.soundSkip = 1.5F;
        config().itemBreak.soundVolume = 0.3F;
        Break changed = breakPlayerTool(t);
        t.check("Particle Count 12", changed.custom()).isEqualTo(12);
        checkSizes(t, "Particle Size 3", changed.particles(), 0.15, 0.3);
        checkLifetimes(t, "Particle Duration 3", changed.particles(), 3, 12, 120);
        t.require(changed.customSounds().size() == 1, "Expected one item break sound");
        t.check("Sound Skip 1.5 s", changed.customSounds().get(0).sound()).isEqualTo(ITEM_BREAK_SOUND + "/v_m1_s1500");
        t.check("Sound Volume 0.3", (double) changed.customSounds().get(0).volume()).near(0.3, 1.0E-6);
        config().itemBreak.particleCount = 500;
        config().itemBreak.soundSkip = 25.0F;
        config().itemBreak.sound3d = false;
        Break limits = breakPlayerTool(t);
        t.check("Particle Count 500 is limited to 50", limits.custom()).isEqualTo(50);
        t.require(limits.customSounds().size() == 1, "Expected one item break sound");
        t.check("Sound Skip 25 s is limited to 10 s; 3D off keeps stereo", limits.customSounds().get(0).sound())
            .isEqualTo(ITEM_BREAK_SOUND + "/v_m0_s10000");
        config().itemBreak.soundSkip = 0.0F;
        Break plain = breakPlayerTool(t);
        t.require(plain.customSounds().size() == 1, "Expected one item break sound");
        t.check("3D off and no skip: the file as it is", plain.customSounds().get(0).sound())
            .isEqualTo(ITEM_BREAK_SOUND);
    }

    /** Breaks the sword of an armor stand (a living entity that is not a player) */
    private static Break breakArmorStandTool(TestContext t) {
        t.teleport(MOB_X, 1.0, MOB_Z - 3.0, 0.0F, 15.0F);
        Spawned<ArmorStand> stand = t.spawn(EntityType.ARMOR_STAND, MOB_X, 0.0, MOB_Z, 180.0F,
            entity -> entity.setItemSlot(EquipmentSlot.MAINHAND, almostBroken()));
        t.ticks(5);
        Break result = breakItem(t, stand.id());
        t.removeEntities();
        return result;
    }

    private void itemBreakOff(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        Break mob = breakArmorStandTool(t);
        t.check("an armor stand's tool: custom particles", mob.custom()).isEqualTo(5);
        t.check("an armor stand's tool: custom sounds", mob.customSounds().size()).isEqualTo(1);
        config().itemBreak.mobs = false;
        Break mobsOff = breakArmorStandTool(t);
        t.check("Mobs off: custom particles", mobsOff.custom()).isEqualTo(0);
        t.check("Mobs off: the game's item crack particles", mobsOff.vanilla()).atLeast(5);
        t.check("Mobs off: the game's break sound", mobsOff.vanillaSounds().size()).isEqualTo(1);
        config().itemBreak.mobs = true;
        config().itemBreak.players = false;
        Break playersOff = breakPlayerTool(t);
        t.check("Players off: custom particles", playersOff.custom()).isEqualTo(0);
        t.check("Players off: the game's break sound", playersOff.vanillaSounds().size()).isEqualTo(1);
        config().itemBreak.players = true;
        config().itemBreak.enabled = false;
        Break off = breakPlayerTool(t);
        t.check("off: custom particles", off.custom()).isEqualTo(0);
        t.check("off: custom sounds", off.customSounds().size()).isEqualTo(0);
        t.check("off: the game's item crack particles", off.vanilla()).atLeast(5);
        t.check("off: the game's break sound", off.vanillaSounds().size()).isEqualTo(1);
    }

    private void itemBreakMuted(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        config().itemBreak.soundVolume = 0.0F;
        Break muted = breakPlayerTool(t);
        t.check("volume 0: the game's break sound", muted.vanillaSounds().size()).isEqualTo(0);
        t.check("volume 0: custom particles still shown", muted.custom()).isEqualTo(5);
    }

    // ---- music discs ----------------------------------------------------------------------------------------------

    /** Puts the Cat disc into a jukebox in front of the player; returns the songs played */
    private static List<SoundRecorder.Played> playCat(TestContext t) {
        t.teleport(MOB_X, 1.0, MOB_Z - 3.0, 0.0F, 15.0F);
        t.site().set((int) MOB_X, 0, (int) MOB_Z, "jukebox");
        t.ticks(3);
        SoundRecorder sounds = t.recordSounds();
        BlockPos pos = TestSite.ORIGIN.offset((int) MOB_X, 0, (int) MOB_Z);
        t.onServer(level -> {
            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                jukebox.setTheItem(new ItemStack(Items.MUSIC_DISC_CAT));
            }
            return null;
        });
        t.ticks(10);
        List<SoundRecorder.Played> songs = new ArrayList<>(sounds.played("music_disc"));
        songs.addAll(sounds.played("cosmetics.song"));
        t.onServer(level -> {
            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox) {
                jukebox.popOutTheItem();
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return null;
        });
        t.removeEntities();
        t.ticks(5);
        return songs;
    }

    private void song(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        config().musicDiscs.songs.put("minecraft:cat", CosmeticsFixture.SONG_FILE);
        List<SoundRecorder.Played> songs = playCat(t);
        t.require(songs.size() == 1, "Expected one song, got " + songs);
        SoundRecorder.Played song = songs.get(0);
        String event = CosmeticsInternals.songEvent(CosmeticsFixture.SONG_FILE);
        t.check("the chosen song plays", song.event()).isEqualTo(event);
        t.check("as a 3D sound (mono variant)", song.sound().endsWith("/v_m1_s0")).isTrue();
        t.check("volume 4, heard from 64 blocks like the game's discs", (double) song.volume()).near(4.0, 1.0E-6);
        t.check("category: records", song.source()).isEqualTo(SoundSource.RECORDS);
        t.check("placed in the world at the jukebox", song.placed()).isTrue();
        t.check("played at the jukebox (distance from its center)", song.pos().distanceTo(Vec3.atCenterOf(
            TestSite.ORIGIN.offset((int) MOB_X, 0, (int) MOB_Z)))).atMost(0.01);
        config().musicDiscs.sound3d = false;
        List<SoundRecorder.Played> stereo = playCat(t);
        t.require(stereo.size() == 1, "Expected one song, got " + stereo);
        t.check("3D off: the file as it is", stereo.get(0).sound().contains("/v_m")).isFalse();
    }

    private void songOff(TestContext t) throws IOException {
        CosmeticsFixture.use(t, FileSet.ALL);
        List<SoundRecorder.Played> none = playCat(t);
        t.require(none.size() == 1, "Expected one song, got " + none);
        t.check("no song chosen: the disc's song", none.get(0).event()).isEqualTo("minecraft:music_disc.cat");
        config().musicDiscs.songs.put("minecraft:cat", CosmeticsFixture.SONG_FILE);
        config().musicDiscs.enabled = false;
        List<SoundRecorder.Played> off = playCat(t);
        t.require(off.size() == 1, "Expected one song, got " + off);
        t.check("off: the disc's song", off.get(0).event()).isEqualTo("minecraft:music_disc.cat");
        config().musicDiscs.enabled = true;
        config().musicDiscs.songs.put("minecraft:cat", "devtest_missing.ogg");
        List<SoundRecorder.Played> missing = playCat(t);
        t.require(missing.size() == 1, "Expected one song, got " + missing);
        t.check("chosen file missing: the disc's song", missing.get(0).event()).isEqualTo("minecraft:music_disc.cat");
    }

    // ---- missing files --------------------------------------------------------------------------------------------

    private void missingFiles(TestContext t) throws IOException {
        CosmeticsFixture.use(t, new FileSet(false, true, false, true, false));
        Death noFrames = killPig(t);
        t.check("death without frames: custom particles", noFrames.custom()).isEqualTo(0);
        t.check("death without frames: the game's poof particles", noFrames.vanilla()).atLeast(15);
        t.check("death without frames: the custom sound still plays", noFrames.sounds().size()).isEqualTo(1);
        Break breakNoFrames = breakPlayerTool(t);
        t.check("item break without frames: the game's particles", breakNoFrames.vanilla()).atLeast(5);
        t.check("item break without frames: the custom sound", breakNoFrames.customSounds().size()).isEqualTo(1);

        CosmeticsFixture.use(t, new FileSet(true, false, true, false, false));
        Death noSound = killPig(t);
        t.check("death without a sound: custom particles", noSound.custom()).isEqualTo(20);
        t.check("death without a sound: death sounds", noSound.sounds().size()).isEqualTo(0);
        Break breakNoSound = breakPlayerTool(t);
        t.check("item break without a sound: custom particles", breakNoSound.custom()).isEqualTo(5);
        t.check("item break without a sound: the game's break sound", breakNoSound.vanillaSounds().size())
            .isEqualTo(1);
    }

    // ---- settings -------------------------------------------------------------------------------------------------

    private void screens(TestContext t) {
        t.check("Cosmetics screen open", t.openSettings("cosmetics")).isTrue();
        t.saveScreenshot("settings_cosmetics");
        Screen main = t.mc().screen;
        int i = 0;
        for (Function<Screen, Screen> category : CosmeticsInternals.categoryScreens()) {
            Screen[] opened = new Screen[1];
            t.openScreen(() -> {
                opened[0] = category.apply(main);
                return opened[0];
            });
            t.check("category screen " + i + " open", t.mc().screen == opened[0]).isTrue();
            t.saveScreenshot("settings_cosmetics_category_" + i);
            i++;
        }
        t.closeScreen();
    }
}
