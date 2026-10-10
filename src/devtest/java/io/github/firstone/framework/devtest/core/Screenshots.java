package io.github.firstone.framework.devtest.core;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Saves test screenshots and compares them with baseline screenshots
 *
 * <p>The first run of a screenshot saves it as the baseline ({@code baselines/<feature>/<name>.png}); later runs
 * count the pixels that differ from it. Baselines stay on this computer (rendering differs between graphics cards);
 * {@code -PupdateBaselines=true} replaces them, after a change that is meant to look different.</p>
 */
final class Screenshots {

    /** A pixel differs when one of its color channels differs by more than this (water and particles move) */
    private static final int CHANNEL_TOLERANCE = 40;

    private final Path results;
    private final Path baselines;
    private final boolean update;

    Screenshots(Path results, Path baselines, boolean update) {
        this.results = results;
        this.baselines = baselines;
        this.update = update;
    }

    /**
     * Saves a screenshot and compares it with its baseline
     *
     * @param name  file name without extension
     * @param image the screenshot; closed here
     * @return the share of differing pixels in percent, or -1 when a new baseline was saved
     * @throws IOException if a file cannot be read or written
     */
    double saveAndCompare(String name, NativeImage image) throws IOException {
        try (image) {
            Files.createDirectories(this.results);
            Files.createDirectories(this.baselines);
            Path result = this.results.resolve(name + ".png");
            Path baseline = this.baselines.resolve(name + ".png");
            image.writeToFile(result);
            if (this.update || !Files.exists(baseline)) {
                Files.copy(result, baseline, StandardCopyOption.REPLACE_EXISTING);
                return -1.0;
            }
            try (InputStream input = Files.newInputStream(baseline); NativeImage expected = NativeImage.read(input)) {
                return differingPercent(image, expected);
            }
        }
    }

    /**
     * Saves a screenshot without comparing it
     *
     * @param name  file name without extension
     * @param image the screenshot; closed here
     * @return always 0
     * @throws IOException if the file cannot be written
     */
    double save(String name, NativeImage image) throws IOException {
        try (image) {
            Files.createDirectories(this.results);
            image.writeToFile(this.results.resolve(name + ".png"));
            return 0.0;
        }
    }

    private static double differingPercent(NativeImage actual, NativeImage expected) {
        if (actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight()) {
            return 100.0;
        }
        long differing = 0;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                int a = actual.getPixelRGBA(x, y);
                int b = expected.getPixelRGBA(x, y);
                for (int shift = 0; shift < 24; shift += 8) {
                    if (Math.abs((a >> shift & 0xFF) - (b >> shift & 0xFF)) > CHANNEL_TOLERANCE) {
                        differing++;
                        break;
                    }
                }
            }
        }
        return differing * 100.0 / ((long) actual.getWidth() * actual.getHeight());
    }
}
