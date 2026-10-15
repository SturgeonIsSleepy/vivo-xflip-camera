package com.sturgeon.xflipcamera.core;

/** Frozen pixel-loop oracle from 44c2006 / PhotoEnhancer and PhotoToneCurve. Test/benchmark only. */
final class LegacyPhotoToneRow {
    private LegacyPhotoToneRow() { }

    static void process(int[] colors, byte[] before, byte[] center, byte[] after,
            byte[] local, byte[] smooth, float[] baseLog, boolean detail, double compression, double noise) {
        for (int x = 0; x < colors.length; x++) {
            int level = center[x] & 255;
            int mapped = compression < 0.999 ? hdrLuma(level, baseLog[x], compression, noise) : level;
            if (detail && x > 0 && x + 1 < colors.length
                    && Math.abs(level - (smooth[x] & 255)) >= Math.max(2, noise * 1.8)) {
                int lo = 255, hi = 0;
                for (int xx = x - 1; xx <= x + 1; xx++) {
                    lo = Math.min(lo, Math.min(before[xx] & 255, Math.min(center[xx] & 255, after[xx] & 255)));
                    hi = Math.max(hi, Math.max(before[xx] & 255, Math.max(center[xx] & 255, after[xx] & 255)));
                }
                mapped += detailDelta(level, smooth[x] & 255, local[x] & 255, noise, lo, hi);
            }
            colors[x] = withLuma(colors[x], Math.max(0, Math.min(255, mapped)));
        }
    }

    private static int hdrLuma(int y, double baseLog, double compression, double noise) {
        if (y == 0 || y == 255 || compression >= 0.999) return y;
        double log = Math.log(y / 255.0);
        double target = 255 * Math.exp(log + (compression - 1) * (baseLog - Math.log(0.5)));
        target = Math.max(y * 0.8, Math.min(y * 2.0 + 2, target));
        double feather = Math.min(1, Math.min(y, 255 - y) / 12.0);
        feather = feather * feather * (3 - 2 * feather);
        double strength = 0.6 * Math.min(1, 5 / Math.max(2, noise)) * feather;
        return clamp((int) Math.round(y + (target - y) * strength));
    }

    private static int detailDelta(int y, double blurred, int clahe, double noise, int localMin, int localMax) {
        double detail = y - blurred;
        if (Math.abs(detail) < Math.max(2, noise * 1.8)) return 0;
        double local = clahe - y;
        if (local * detail < 0) local = 0;
        double adjustment = Math.max(-12, Math.min(12, detail * 0.55 + Math.max(-24, Math.min(24, local)) * 0.25));
        return (int) Math.round(Math.max(localMin - 3, Math.min(localMax + 3, y + adjustment)) - y);
    }

    private static int withLuma(int color, int target) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        double y = (77 * r + 150 * g + 29 * b) / 256.0;
        if (y == 0) return color & 0xff000000;
        double gain = Math.min(target / y, 255.0 / Math.max(r, Math.max(g, b)));
        return (color & 0xff000000) | clamp((int) Math.round(r * gain)) << 16
                | clamp((int) Math.round(g * gain)) << 8 | clamp((int) Math.round(b * gain));
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
}
