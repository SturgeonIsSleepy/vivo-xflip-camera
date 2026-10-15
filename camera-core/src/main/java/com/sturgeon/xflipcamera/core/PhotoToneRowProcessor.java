package com.sturgeon.xflipcamera.core;

import java.util.Objects;

/**
 * Allocation-free row kernel for the 0.4.1 JPEG tone loop. Inputs are the already
 * level-mapped OpenCV gray/CLAHE/blur/base rows, not a new RGB-to-gray conversion.
 * The caller owns the bitmap, row buffers and cancellation checks between rows.
 */
public final class PhotoToneRowProcessor {
    private static final double LOG_HALF = Math.log(0.5);
    private static final double[] LOG_LUMA = new double[256];
    private static final double[] FEATHER = new double[256];

    static {
        for (int y = 1; y < 255; y++) {
            LOG_LUMA[y] = Math.log(y / 255.0);
            double feather = Math.min(1, Math.min(y, 255 - y) / 12.0);
            FEATHER[y] = feather * feather * (3 - 2 * feather);
        }
    }

    private final boolean detail;
    private final boolean hdr;
    private final double compressionDelta;
    private final double detailThreshold;
    private final double noise;
    private final double[] strength;

    /** One immutable instance per image, after its histogram and noise analysis. */
    public PhotoToneRowProcessor(boolean detail, double compression, double noise) {
        if (!Double.isFinite(compression) || compression <= 0 || compression > 1
                || !Double.isFinite(noise) || noise < 0) {
            throw new IllegalArgumentException("Finite compression in (0,1] and nonnegative noise required");
        }
        this.detail = detail;
        this.hdr = compression < 0.999;
        this.compressionDelta = compression - 1;
        this.detailThreshold = Math.max(2, noise * 1.8);
        this.noise = noise;
        strength = hdr ? new double[256] : null;
        if (hdr) {
            // Preserve multiplication order and double rounding of hdrLuma exactly.
            for (int y = 1; y < 255; y++) {
                strength[y] = 0.6 * Math.min(1, 5 / Math.max(2, noise)) * FEATHER[y];
            }
        }
    }

    /**
     * Replaces colors in place. All byte samples are unsigned. before/after must
     * be clamped to the first/last image row by the caller, as in PhotoEnhancer.
     * Disabled detail/HDR buffers may be null. Extra sample entries are ignored.
     * No sample buffer is changed. Validation completes before the first write.
     */
    public void process(int[] colors, byte[] before, byte[] center, byte[] after,
            byte[] local, byte[] smooth, float[] baseLog) {
        Objects.requireNonNull(colors, "colors");
        int width = colors.length;
        if (width == 0) throw new IllegalArgumentException("Empty pixel row");
        requireLength(center, width, "center");
        if (detail) {
            requireLength(before, width, "before");
            requireLength(after, width, "after");
            requireLength(local, width, "local");
            requireLength(smooth, width, "smooth");
        }
        if (hdr && (baseLog == null || baseLog.length < width)) {
            throw new IllegalArgumentException("Missing/short HDR base row");
        }
        for (int x = 0; x < width; x++) {
            int level = center[x] & 255;
            int mapped = hdr ? hdrLuma(level, baseLog[x]) : level;
            if (detail && x > 0 && x + 1 < width
                    && Math.abs(level - (smooth[x] & 255)) >= detailThreshold) {
                int lo = 255, hi = 0;
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    lo = Math.min(lo, Math.min(before[xx] & 255,
                            Math.min(center[xx] & 255, after[xx] & 255)));
                    hi = Math.max(hi, Math.max(before[xx] & 255,
                            Math.max(center[xx] & 255, after[xx] & 255)));
                }
                mapped += PhotoToneCurve.detailDelta(level, smooth[x] & 255,
                        local[x] & 255, noise, lo, hi);
            }
            colors[x] = PhotoToneCurve.withLuma(colors[x], Math.max(0, Math.min(255, mapped)));
        }
    }

    private int hdrLuma(int y, double baseLog) {
        if (y == 0 || y == 255) return y;
        double target = 255 * Math.exp(LOG_LUMA[y] + compressionDelta * (baseLog - LOG_HALF));
        target = Math.max(y * 0.8, Math.min(y * 2.0 + 2, target));
        return Math.max(0, Math.min(255, (int) Math.round(y + (target - y) * strength[y])));
    }

    private static void requireLength(byte[] row, int width, String name) {
        if (row == null || row.length < width) throw new IllegalArgumentException("Missing/short " + name + " row");
    }
}
