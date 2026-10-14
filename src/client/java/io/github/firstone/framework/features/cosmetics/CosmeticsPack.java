package io.github.firstone.framework.features.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Built-in resource pack that serves the player's Cosmetics files ({@link CosmeticsFiles}) to the game
 *
 * <p>The files stay in the config folder; this pack only maps them to resource paths of the
 * {@code firstone-framework} namespace, scanned again on every resource reload (F3+T or the screen's Reload):</p>
 * <ul>
 *   <li>frames → {@code textures/particle/cosmetics/<effect>/<n>.png}, stitched into the particle atlas
 *       (sprite {@code firstone-framework:cosmetics/<effect>/<n>})</li>
 *   <li>sounds → {@code sounds/cosmetics/…ogg}, with a generated {@code sounds.json} that defines the sound events
 *       {@code cosmetics.death}, {@code cosmetics.item_break} and {@code cosmetics.song.<id>} (one per song file)</li>
 * </ul>
 *
 * <p>Only this namespace is used, so no game texture or sound is replaced and other resource packs are not
 * affected. The pack is required (always on) and added to the client's pack list by {@code CosmeticsPackMixin}.</p>
 */
public final class CosmeticsPack implements PackResources {

    /** Namespace of every resource of this pack */
    public static final String NAMESPACE = "firstone-framework";

    /** Adds this pack to a pack list; given to the client's {@code PackRepository} */
    public static final RepositorySource SOURCE = CosmeticsPack::loadPacks;

    /** Id and name of the pack in the resource pack list */
    private static final PackLocationInfo LOCATION = new PackLocationInfo("firstone-framework/cosmetics",
        Component.translatable("firstone-framework.cosmetics.pack"), PackSource.BUILT_IN, Optional.empty());

    /** Where this pack opens */
    private final PackLocationInfo location;

    /** Every resource of this pack, built when the pack opens */
    private final Map<ResourceLocation, IoSupplier<InputStream>> resources = new HashMap<>();

    private CosmeticsPack(PackLocationInfo location) {
        this.location = location;
        JsonObject sounds = new JsonObject();
        addEffect(CosmeticsFiles.DEATH, "firstone-framework.cosmetics.subtitle.death", sounds);
        addEffect(CosmeticsFiles.ITEM_BREAK, "subtitles.entity.item.break", sounds);
        for (Path song : CosmeticsFiles.songs()) {
            String id = songId(song.getFileName().toString());
            addSound("song." + id, "song/" + id, song, true, null, sounds);
        }
        if (!sounds.isEmpty()) {
            byte[] json = sounds.toString().getBytes(StandardCharsets.UTF_8);
            this.resources.put(id("sounds.json"), () -> new ByteArrayInputStream(json));
        }
    }

    /**
     * Returns the id of the sound event of a Cosmetics sound
     *
     * @param name {@code death}, {@code item_break} or {@code song.<id>}
     * @return {@code firstone-framework:cosmetics.<name>}
     */
    public static ResourceLocation soundEvent(String name) {
        return id("cosmetics." + name);
    }

    /**
     * Returns the sound event of a song file (it exists only if the file was there at the last resource reload)
     *
     * @param fileName file name in {@code music_discs/}, e.g. {@code "My Song.ogg"}
     * @return {@code firstone-framework:cosmetics.song.<id>}, see {@link #songId}
     */
    public static ResourceLocation songEvent(String fileName) {
        return soundEvent("song." + songId(fileName));
    }

    /**
     * Returns the id of a song file, made only from its name, so it never changes while the file keeps its name
     *
     * <p>The name in lower case with every character a resource id cannot hold replaced by {@code _}, followed by
     * a hash of the exact name, so two names that look alike after the replacement still get different ids.</p>
     *
     * @param fileName file name, e.g. {@code "My Song.ogg"}
     * @return e.g. {@code "my_song_1f2e3d4c"}
     */
    private static String songId(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".ogg")) {
            name = name.substring(0, name.length() - ".ogg".length());
        }
        return name.replaceAll("[^a-z0-9_.-]", "_") + "_" + Integer.toHexString(fileName.hashCode());
    }

    /**
     * Returns the particle sprite id of a frame
     *
     * @param effect {@link CosmeticsFiles#DEATH} or {@link CosmeticsFiles#ITEM_BREAK}
     * @param frame  frame index, from 0
     * @return {@code firstone-framework:cosmetics/<effect>/<frame>}
     */
    public static ResourceLocation frame(String effect, int frame) {
        return id("cosmetics/" + effect + "/" + frame);
    }

    /** Creates the pack and gives it to the pack list (called by the pack list on every reload) */
    private static void loadPacks(Consumer<Pack> packs) {
        Pack pack = Pack.readMetaAndCreate(LOCATION, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new CosmeticsPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new CosmeticsPack(location);
            }
        }, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            packs.accept(pack);
        }
    }

    /** Maps the frames and the sound of an effect */
    private void addEffect(String effect, String subtitle, JsonObject sounds) {
        List<Path> frames = CosmeticsFiles.frames(effect);
        for (int i = 0; i < frames.size(); i++) {
            Path file = frames.get(i);
            this.resources.put(id("textures/particle/cosmetics/" + effect + "/" + i + ".png"), IoSupplier.create(file));
        }
        Path sound = CosmeticsFiles.sound(effect);
        if (Files.isRegularFile(sound)) {
            addSound(effect, effect, sound, false, subtitle, sounds);
        }
    }

    /**
     * Maps a sound file and adds its sound event to {@code sounds.json}
     *
     * @param event    event name after {@code cosmetics.}
     * @param path     file path after {@code sounds/cosmetics/}, without {@code .ogg}
     * @param file     the file
     * @param stream   true for long sounds (songs), read while playing instead of loaded at once
     * @param subtitle subtitle key, or null for none
     * @param sounds   the {@code sounds.json} being built
     */
    private void addSound(String event, String path, Path file, boolean stream, @Nullable String subtitle,
                          JsonObject sounds) {
        this.resources.put(id("sounds/cosmetics/" + path + ".ogg"), IoSupplier.create(file));
        JsonObject sound = new JsonObject();
        sound.addProperty("name", NAMESPACE + ":cosmetics/" + path);
        sound.addProperty("stream", stream);
        JsonArray list = new JsonArray();
        list.add(sound);
        JsonObject entry = new JsonObject();
        entry.add("sounds", list);
        if (subtitle != null) {
            entry.addProperty("subtitle", subtitle);
        }
        sounds.add("cosmetics." + event, entry);
    }

    /** Returns a resource location of this pack's namespace */
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        return type == PackType.CLIENT_RESOURCES ? this.resources.get(location) : null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !NAMESPACE.equals(namespace)) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        this.resources.forEach((location, resource) -> {
            if (location.getPath().startsWith(prefix)) {
                output.accept(location, resource);
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES && !this.resources.isEmpty() ? Set.of(NAMESPACE) : Set.of();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer != PackMetadataSection.TYPE) {
            return null;
        }
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        return (T) new PackMetadataSection(Component.translatable("firstone-framework.cosmetics.pack.description"),
            format, Optional.empty());
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
        // nothing is kept open: every resource opens its own file stream when read
    }
}
