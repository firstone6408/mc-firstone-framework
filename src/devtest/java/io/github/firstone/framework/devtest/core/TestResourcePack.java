package io.github.firstone.framework.devtest.core;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * A resource pack made by a test, e.g. to check how a feature reacts to a pack that changes a texture
 *
 * <p>Made with {@link TestContext#resourcePack}, filled with {@link #image}, then turned on with {@link #enable}. It
 * is turned off and deleted at the end of the test.</p>
 */
public final class TestResourcePack {

    /** Resource pack format of Minecraft 1.21.1 */
    private static final int PACK_FORMAT = 34;

    private final TestContext t;
    private final Path directory;
    private final String id;
    private boolean enabled;

    TestResourcePack(TestContext t, Path directory) throws IOException {
        this.t = t;
        this.directory = directory;
        this.id = "file/" + directory.getFileName();
        deleteFiles();
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + PACK_FORMAT
            + ",\"description\":\"FirstOne dev test pack\"}}");
    }

    /**
     * Adds a texture of one color to the pack
     *
     * @param path   path inside the pack, e.g. {@code assets/minecraft/textures/entity/boat/oak.png}
     * @param width  width in pixels
     * @param height height in pixels
     * @param abgr   the color, as {@code NativeImage} stores it (alpha, blue, green, red)
     * @return this pack
     * @throws IOException if the file cannot be written
     */
    public TestResourcePack image(String path, int width, int height, int abgr) throws IOException {
        Path file = this.directory.resolve(path);
        Files.createDirectories(file.getParent());
        try (NativeImage image = new NativeImage(width, height, false)) {
            image.fillRect(0, 0, width, height, abgr);
            image.writeToFile(file);
        }
        return this;
    }

    /** Turns the pack on and waits until the resources have reloaded */
    public void enable() {
        setSelected(true);
    }

    /** Turns the pack off and waits until the resources have reloaded */
    public void disable() {
        setSelected(false);
    }

    /** Turns the pack off if it is on and deletes it (end of the test) */
    void remove() throws IOException {
        if (this.enabled) {
            disable();
        }
        deleteFiles();
    }

    private void setSelected(boolean on) {
        Minecraft minecraft = this.t.mc();
        CompletableFuture<Void> reload = this.t.onClientThread(() -> {
            PackRepository repository = minecraft.getResourcePackRepository();
            repository.reload();
            List<String> selected = new ArrayList<>(repository.getSelectedIds());
            selected.remove(this.id);
            if (on) {
                selected.add(this.id);
            }
            repository.setSelected(selected);
            return minecraft.reloadResourcePacks();
        });
        this.t.waitUntil("the resources reload", () -> reload.isDone() && minecraft.getOverlay() == null, 20 * 60);
        this.enabled = on;
    }

    private void deleteFiles() throws IOException {
        if (!Files.exists(this.directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(this.directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
