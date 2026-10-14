package io.github.firstone.framework.devtest.core;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Compares screenshots pixel by pixel
 */
public final class Images {

    /** A pixel differs when one of its color channels differs by more than this (water and particles move) */
    private static final int CHANNEL_TOLERANCE = 40;

    private Images() {}

    /**
     * Returns the share of pixels that differ between two images of the same size
     *
     * @param a one image
     * @param b the other image
     * @return the share in percent, or 100 when the sizes differ
     */
    public static double differingPercent(NativeImage a, NativeImage b) {
        return differingPercent(a, b, 0, 0, a.getWidth(), a.getHeight());
    }

    /**
     * Returns the average of one color channel inside a rectangle
     *
     * @param image   the image
     * @param x       left of the rectangle, in image pixels
     * @param y       top of the rectangle
     * @param width   width of the rectangle
     * @param height  height of the rectangle
     * @param channel 0 red, 1 green, 2 blue
     * @return the average, from 0 to 255
     */
    public static double meanChannel(NativeImage image, int x, int y, int width, int height, int channel) {
        int right = Math.min(image.getWidth(), x + width);
        int bottom = Math.min(image.getHeight(), y + height);
        long sum = 0;
        long total = 0;
        for (int py = Math.max(0, y); py < bottom; py++) {
            for (int px = Math.max(0, x); px < right; px++) {
                // NativeImage stores pixels as ABGR: red is the lowest byte
                sum += image.getPixelRGBA(px, py) >> (channel * 8) & 0xFF;
                total++;
            }
        }
        return total == 0 ? 0.0 : (double) sum / total;
    }

    /**
     * Returns the share of pixels that differ inside a rectangle of two images of the same size
     *
     * @param a      one image
     * @param b      the other image
     * @param x      left of the rectangle, in image pixels
     * @param y      top of the rectangle
     * @param width  width of the rectangle
     * @param height height of the rectangle
     * @return the share in percent, or 100 when the sizes differ
     */
    public static double differingPercent(NativeImage a, NativeImage b, int x, int y, int width, int height) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return 100.0;
        }
        int right = Math.min(a.getWidth(), x + width);
        int bottom = Math.min(a.getHeight(), y + height);
        long differing = 0;
        long total = 0;
        for (int py = Math.max(0, y); py < bottom; py++) {
            for (int px = Math.max(0, x); px < right; px++) {
                total++;
                int pixelA = a.getPixelRGBA(px, py);
                int pixelB = b.getPixelRGBA(px, py);
                for (int shift = 0; shift < 24; shift += 8) {
                    if (Math.abs((pixelA >> shift & 0xFF) - (pixelB >> shift & 0xFF)) > CHANNEL_TOLERANCE) {
                        differing++;
                        break;
                    }
                }
            }
        }
        return total == 0 ? 0.0 : differing * 100.0 / total;
    }
}
