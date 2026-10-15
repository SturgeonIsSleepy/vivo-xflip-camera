package com.sturgeon.xflipcamera.core;

import java.util.Arrays;
import java.util.Random;

final class MultiFrameAccumulatorChecks {
    private static int assertions;
    static int run() {
        int width = 128, height = 96;
        Random random = new Random(117);
        int[][] frames = new int[3][width * height];
        double originalError = 0;
        for (int i = 0; i < frames[0].length; i++) for (int n = 0; n < 3; n++) {
            int value = 120 + (int) Math.round(random.nextGaussian() * 8);
            frames[n][i] = gray(value);
            if (n == 0) originalError += (value - 120) * (value - 120);
        }
        MultiFrameAccumulator merge = new MultiFrameAccumulator(width, height, frames[0].clone());
        check(merge.noiseSigma > 5 && merge.noiseSigma < 12, "Noise estimate");
        merge.addTile(0, height, frames[1]);
        merge.addTile(0, height, frames[2]);
        double mergedError = 0;
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            merge.readRow(y, row);
            for (int c : row) mergedError += ((c & 255) - 120) * ((c & 255) - 120);
        }
        check(mergedError < originalError * 0.48, "Independent noisy frames improve actual error");
        int[] reference = new int[width * height];
        Arrays.fill(reference, gray(120));
        int[] changed = reference.clone();
        for (int y = 16; y < 64; y++) for (int x = 32; x < 80; x++) changed[y * width + x] = gray(210);
        MultiFrameAccumulator moving = new MultiFrameAccumulator(width, height, reference.clone());
        moving.addTile(0, height, changed);
        for (int y = 0; y < height; y++) {
            moving.readRow(y, row);
            for (int c : row) check(c == gray(120), "Moving object does not leave a ghost");
        }
        int[] invalid = new int[width * height];
        moving.addTile(0, height, invalid);
        moving.readRow(0, row);
        check(row[0] == gray(120), "Invalid warp borders keep reference");
        int[] edge = new int[]{gray(30), gray(30), gray(60), gray(120), gray(180), gray(210), gray(210)};
        int[] sharpened = new int[edge.length];
        MultiFrameAccumulator.sharpenRow(edge, edge, edge, sharpened, 2);
        check((sharpened[4] & 255) - (sharpened[2] & 255) > 120, "Blurred edge contrast improves");
        for (int i = 0; i < edge.length; i++) check(Math.abs((edge[i] & 255) - (sharpened[i] & 255)) <= 6, "Bounded sharpening");
        boolean rejected = false;
        try { moving.addTile(-1, 1, new int[width]); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Invalid tile rejected");
        System.out.println("MULTIFRAME noise_mse_ratio=" + mergedError / originalError + ", moving_patch=reference");
        return assertions;
    }
    private static int gray(int value) { return 0xff000000 | value << 16 | value << 8 | value; }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
