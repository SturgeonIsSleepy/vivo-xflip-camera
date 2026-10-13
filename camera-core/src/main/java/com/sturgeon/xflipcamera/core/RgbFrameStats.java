package com.sturgeon.xflipcamera.core;

/** Pixel evidence only: a nonzero pixel does not prove camera or orientation correctness. */
public final class RgbFrameStats {
    public final int pixels;
    public final int nonBlackPixels;
    public final int redMin;
    public final int redMax;
    public final int greenMin;
    public final int greenMax;
    public final int blueMin;
    public final int blueMax;

    private RgbFrameStats(int pixels, int nonBlackPixels, int[] min, int[] max) {
        this.pixels = pixels;
        this.nonBlackPixels = nonBlackPixels;
        redMin = min[0]; redMax = max[0];
        greenMin = min[1]; greenMax = max[1];
        blueMin = min[2]; blueMax = max[2];
    }

    /** Caller supplies decoded opaque JPEG pixels; alpha is deliberately ignored. */
    public static RgbFrameStats fromArgb(int[] argb) {
        if (argb == null || argb.length == 0) {
            throw new IllegalArgumentException("A nonempty decoded raster is required");
        }
        int[] min = {255, 255, 255};
        int[] max = {0, 0, 0};
        int nonBlack = 0;
        for (int pixel : argb) {
            if ((pixel & 0x00ffffff) != 0) nonBlack++;
            for (int channel = 0; channel < 3; channel++) {
                int value = (pixel >>> (16 - 8 * channel)) & 255;
                min[channel] = Math.min(min[channel], value);
                max[channel] = Math.max(max[channel], value);
            }
        }
        return new RgbFrameStats(argb.length, nonBlack, min, max);
    }

    public boolean allBlack() {
        return nonBlackPixels == 0;
    }
}
