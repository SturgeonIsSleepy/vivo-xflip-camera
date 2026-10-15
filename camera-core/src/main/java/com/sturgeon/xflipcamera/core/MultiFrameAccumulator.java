package com.sturgeon.xflipcamera.core;

import java.util.Arrays;

/** Three already aligned SDR JPEG frames. Reference has weight two; moving patches keep the reference. */
public final class MultiFrameAccumulator {
    private final int width, height;
    private final int[] sums;
    private final byte[] weights;
    public final double noiseSigma;

    /** Takes ownership of pixels and reuses their storage as packed 10-bit RGB sums. */
    public MultiFrameAccumulator(int width, int height, int[] pixels) {
        if (width < 16 || height < 16 || (long) width * height > 13_000_000L
                || pixels.length != width * height) throw new IllegalArgumentException("Invalid fusion dimensions");
        this.width = width;
        this.height = height;
        noiseSigma = estimateNoise(pixels, width, height);
        sums = pixels;
        weights = new byte[pixels.length];
        Arrays.fill(weights, (byte) 2);
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            sums[i] = (((c >> 16) & 255) * 2 << 20) | (((c >> 8) & 255) * 2 << 10) | ((c & 255) * 2);
        }
    }

    /** Non-overlapping row tiles per candidate, at most two candidates. Alpha marks valid warp support. */
    public int addTile(int top, int rows, int[] pixels) {
        if (top < 0 || rows < 1 || top + rows > height || pixels.length < width * rows) {
            throw new IllegalArgumentException("Invalid fusion tile");
        }
        int accepted = 0;
        for (int by = 0; by < rows; by += 8) for (int bx = 0; bx < width; bx += 8) {
            double difference = 0, square = 0;
            int valid = 0;
            for (int y = by; y < Math.min(rows, by + 8); y++) for (int x = bx; x < Math.min(width, bx + 8); x++) {
                int candidate = pixels[y * width + x];
                if ((candidate >>> 24) < 254) continue;
                double d = luma(candidate) - luma(pixelAt((top + y) * width + x));
                difference += d;
                square += d * d;
                valid++;
            }
            if (valid < 8 || Math.abs(difference / valid) > Math.max(3, noiseSigma * 0.8)
                    || Math.sqrt(square / valid) > noiseSigma * 2.5 + 4) continue;
            for (int y = by; y < Math.min(rows, by + 8); y++) for (int x = bx; x < Math.min(width, bx + 8); x++) {
                int candidate = pixels[y * width + x];
                if ((candidate >>> 24) < 254) continue;
                int at = (top + y) * width + x;
                int current = pixelAt(at);
                int r = (candidate >> 16) & 255, g = (candidate >> 8) & 255, b = candidate & 255;
                int delta = Math.max(Math.abs(r - ((current >> 16) & 255)),
                        Math.max(Math.abs(g - ((current >> 8) & 255)), Math.abs(b - (current & 255))));
                if (delta > noiseSigma * 4 + 8) continue;
                if (weights[at] >= 4) throw new IllegalStateException("Only three frames are supported");
                sums[at] += (r << 20) | (g << 10) | b;
                weights[at]++;
                accepted++;
            }
        }
        return accepted;
    }

    public void readRow(int y, int[] destination) {
        if (y < 0 || y >= height || destination.length < width) throw new IllegalArgumentException("Invalid row");
        for (int x = 0; x < width; x++) destination[x] = pixelAt(y * width + x);
    }

    private int pixelAt(int at) {
        int sum = sums[at], n = weights[at];
        return 0xff000000 | ((((sum >> 20) & 1023) + n / 2) / n << 16)
                | ((((sum >> 10) & 1023) + n / 2) / n << 8) | (((sum & 1023) + n / 2) / n);
    }

    /** HH Haar median estimates noise without interpreting edges as independent noise samples. */
    public static double estimateNoise(int[] pixels, int width, int height) {
        double[] samples = new double[Math.min(65536, (width / 2) * (height / 2))];
        int step = Math.max(2, (int) Math.sqrt((double) width * height / samples.length));
        int count = 0;
        for (int y = 0; y + 1 < height && count < samples.length; y += step) {
            for (int x = 0; x + 1 < width && count < samples.length; x += step) {
                int at = y * width + x;
                samples[count++] = Math.abs(luma(pixels[at]) - luma(pixels[at + 1])
                        - luma(pixels[at + width]) + luma(pixels[at + width + 1])) / 2;
            }
        }
        Arrays.sort(samples, 0, count);
        return Math.max(2, Math.min(15, samples[count / 2] / 0.67448975));
    }

    /** Thresholded luminance unsharp mask; bounded to six levels and two-level local overshoot. */
    public static void sharpenRow(int[] before, int[] center, int[] after, int[] output, double sigma) {
        int width = center.length;
        output[0] = center[0];
        output[width - 1] = center[width - 1];
        for (int x = 1; x < width - 1; x++) {
            double blur = (luma(before[x - 1]) + 2 * luma(before[x]) + luma(before[x + 1])
                    + 2 * luma(center[x - 1]) + 4 * luma(center[x]) + 2 * luma(center[x + 1])
                    + luma(after[x - 1]) + 2 * luma(after[x]) + luma(after[x + 1])) / 16;
            double value = luma(center[x]), detail = value - blur;
            int adjustment = Math.abs(detail) < sigma ? 0 : (int) Math.round(Math.max(-6, Math.min(6, detail * 0.25)));
            double low = Math.min(value, Math.min(luma(center[x - 1]), luma(center[x + 1])));
            double high = Math.max(value, Math.max(luma(center[x - 1]), luma(center[x + 1])));
            adjustment = (int) Math.round(Math.max(low - 2, Math.min(high + 2, value + adjustment)) - value);
            int c = center[x];
            output[x] = 0xff000000 | (clamp(((c >> 16) & 255) + adjustment) << 16)
                    | (clamp(((c >> 8) & 255) + adjustment) << 8) | clamp((c & 255) + adjustment);
        }
    }

    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static double luma(int c) { return (77 * ((c >> 16) & 255) + 150 * ((c >> 8) & 255) + 29 * (c & 255)) / 256.0; }
}
