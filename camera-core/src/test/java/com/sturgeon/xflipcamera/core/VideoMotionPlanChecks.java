package com.sturgeon.xflipcamera.core;

import java.util.Random;

final class VideoMotionPlanChecks {
    private static int assertions;

    static int run() {
        assertions = 0;
        byte[] frame = new byte[96 * 64];
        new Random(42).nextBytes(frame);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                byte[] next = translate(frame, 96, 64, dx, dy);
                VideoMotionPlan.Shift shift = VideoMotionPlan.estimate(frame, next, 96, 64);
                check(shift.reliable() && Math.abs(shift.x() - dx) < 0.05 && Math.abs(shift.y() - dy) < 0.05,
                        "Known two-axis translation " + dx + "," + dy);
            }
        }
        check(!VideoMotionPlan.estimate(new byte[96 * 64], new byte[96 * 64], 96, 64).reliable(),
                "Low texture must be rejected");
        byte[] unrelated = new byte[frame.length];
        new Random(77).nextBytes(unrelated);
        check(!VideoMotionPlan.estimate(frame, unrelated, 96, 64).reliable(), "Scene cut rejected");
        byte[] repeated = new byte[frame.length];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 96; x++) {
            repeated[y * 96 + x] = (byte) ((x % 4 < 2) == (y % 4 < 2) ? 30 : 220);
        }
        check(!VideoMotionPlan.estimate(repeated, translate(repeated, 96, 64, 1, 1), 96, 64).reliable(),
                "A second distinct minimum in repetitive texture is rejected");
        int size = 41;
        long[] times = new long[size];
        VideoMotionPlan.Shift[] shifts = new VideoMotionPlan.Shift[size];
        for (int i = 1; i < size; i++) {
            times[i] = i * 100000L;
            shifts[i] = new VideoMotionPlan.Shift(2, -1, true, 0);
        }
        VideoMotionPlan pan = VideoMotionPlan.create(times, shifts, 96, 64);
        for (long time : times) {
            check(Math.abs(pan.xAt(time)) < 0.000001 && Math.abs(pan.yAt(time)) < 0.000001,
                    "Constant speed pan preserved, including endpoints");
        }
        double beforeEnergy = 0, afterEnergy = 0;
        int[] position = new int[size];
        for (int i = 1; i < size; i++) {
            position[i] = (i % 2 == 0 ? -3 : 3);
            shifts[i] = new VideoMotionPlan.Shift(position[i] - position[i - 1], 0, true, 0);
        }
        VideoMotionPlan shake = VideoMotionPlan.create(times, shifts, 96, 64);
        for (int i = 4; i < size - 4; i++) {
            double compensated = position[i] + shake.xAt(times[i]) * 96 / (2 * VideoMotionPlan.SCALE);
            beforeEnergy += position[i] * position[i];
            afterEnergy += compensated * compensated;
        }
        check(afterEnergy < beforeEnergy * 0.1, "Synthetic shake energy reduced by over 90 percent");
        check(Math.abs(shake.xAt(450000) - (shake.xAt(400000) + shake.xAt(500000)) / 2) < 0.000001,
                "Correction interpolates between samples");
        for (int i = 1; i < size; i++) shifts[i] = new VideoMotionPlan.Shift(i % 2 == 0 ? -100 : 100, 100, true, 0);
        VideoMotionPlan bounded = VideoMotionPlan.create(times, shifts, 96, 64);
        for (long time : times) {
            check(Math.abs(bounded.xAt(time)) <= VideoMotionPlan.SCALE - 1
                    && Math.abs(bounded.yAt(time)) <= VideoMotionPlan.SCALE - 1, "No exposed black edges");
        }
        shifts[20] = new VideoMotionPlan.Shift(0, 0, false, 255);
        VideoMotionPlan segmented = VideoMotionPlan.create(times, shifts, 96, 64);
        check(segmented.acceptedPairs == size - 2, "Invalid pair excluded");
        check(segmented.sampleCount() == size, "Sample count");
        try {
            VideoMotionPlan.estimate(new byte[1], new byte[1], 1, 1);
            throw new AssertionError("Invalid raster accepted");
        } catch (IllegalArgumentException expected) { assertions++; }
        return assertions;
    }

    private static byte[] translate(byte[] input, int width, int height, int dx, int dy) {
        byte[] output = new byte[input.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int sx = x - dx, sy = y - dy;
            if (sx >= 0 && sy >= 0 && sx < width && sy < height) output[y * width + x] = input[sy * width + sx];
        }
        return output;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
