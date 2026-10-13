package com.sturgeon.xflipcamera.core;

/** Bounds a JPEG diagnostic decode; never allocates the full-resolution raster. */
public final class JpegSamplePlan {
    public static final int MAX_EDGE = 512;
    public static final long MAX_ENCODED_PIXELS = 100_000_000L;
    public final int inSampleSize;

    private JpegSamplePlan(int sampleSize) {
        inSampleSize = sampleSize;
    }

    public static JpegSamplePlan forDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > MAX_ENCODED_PIXELS) {
            throw new IllegalArgumentException("Invalid or oversized JPEG dimensions");
        }
        int sampleSize = 1;
        while (ceilingDivide(width, sampleSize) > MAX_EDGE
                || ceilingDivide(height, sampleSize) > MAX_EDGE) {
            sampleSize *= 2;
        }
        return new JpegSamplePlan(sampleSize);
    }

    private static long ceilingDivide(int value, int divisor) {
        return ((long) value + divisor - 1) / divisor;
    }
}
