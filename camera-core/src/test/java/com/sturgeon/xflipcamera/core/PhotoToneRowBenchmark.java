package com.sturgeon.xflipcamera.core;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/** Optional host microbenchmark; never an Android or end-to-end JPEG performance claim. */
public final class PhotoToneRowBenchmark {
    private static final int WIDTH = 3072;
    private static final int HEIGHT = 4096;
    private static volatile long sink;
    private PhotoToneRowBenchmark() { }

    public static void main(String[] args) {
        int trials = args.length == 0 ? 5 : Integer.parseInt(args[0]);
        if (trials < 3 || trials > 15 || trials % 2 == 0) throw new IllegalArgumentException("Use 3..15 odd paired trials");
        System.out.printf(Locale.ROOT,
                "{\"kind\":\"host_jvm_row_kernel_only\",\"width\":%d,\"height\":%d,\"pixels\":%d,"
                + "\"java_version\":\"%s\",\"arch\":\"%s\",\"trials\":%d,\"cases\":[%n",
                WIDTH, HEIGHT, WIDTH * HEIGHT, System.getProperty("java.version"), System.getProperty("os.arch"), trials);
        measure("detail_without_hdr", 1, trials);
        System.out.println(",");
        measure("detail_with_hdr", 0.65, trials);
        System.out.println("]}");
    }

    private static void measure(String name, double compression, int trials) {
        Fixture fixture = new Fixture();
        PhotoToneRowProcessor kernel = new PhotoToneRowProcessor(true, compression, 3.5);
        // Actual full-sized row-stream comparison, outside the timed region.
        int[] legacy = new int[WIDTH], candidate = new int[WIDTH];
        for (int y = 0; y < HEIGHT; y++) {
            int row = y % fixture.colors.length;
            System.arraycopy(fixture.colors[row], 0, legacy, 0, WIDTH);
            System.arraycopy(fixture.colors[row], 0, candidate, 0, WIDTH);
            fixture.process(legacy, row, null, compression);
            fixture.process(candidate, row, kernel, compression);
            if (!Arrays.equals(legacy, candidate)) throw new AssertionError("Pixel mismatch at row " + y);
        }
        for (int i = 0; i < 4; i++) {
            run(fixture, legacy, null, compression, 512);
            run(fixture, candidate, kernel, compression, 512);
        }
        double[] oldMs = new double[trials], newMs = new double[trials];
        for (int i = 0; i < trials; i++) {
            // Alternate order to reduce systematic warm-cache/frequency bias.
            if (i % 2 == 0) {
                oldMs[i] = time(fixture, legacy, null, compression);
                newMs[i] = time(fixture, candidate, kernel, compression);
            } else {
                newMs[i] = time(fixture, candidate, kernel, compression);
                oldMs[i] = time(fixture, legacy, null, compression);
            }
        }
        long allocated = allocatedBytesForKernel(fixture, candidate, kernel, compression);
        double oldMedian = median(oldMs), newMedian = median(newMs);
        System.out.printf(Locale.ROOT,
                "{\"case\":\"%s\",\"compression\":%.2f,\"noise\":3.5,\"different_pixels\":0,"
                + "\"legacy_ms\":%s,\"candidate_ms\":%s,\"legacy_median_ms\":%.3f,"
                + "\"candidate_median_ms\":%.3f,\"median_speedup\":%.4f,"
                + "\"candidate_loop_thread_allocated_bytes\":%d}",
                name, compression, Arrays.toString(oldMs), Arrays.toString(newMs), oldMedian, newMedian,
                oldMedian / newMedian, allocated);
    }

    private static double time(Fixture fixture, int[] output, PhotoToneRowProcessor kernel, double compression) {
        long begin = System.nanoTime();
        run(fixture, output, kernel, compression, HEIGHT);
        return (System.nanoTime() - begin) / 1_000_000.0;
    }

    private static long allocatedBytesForKernel(Fixture fixture, int[] output,
            PhotoToneRowProcessor kernel, double compression) {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean counter)
                || !counter.isThreadAllocatedMemorySupported()) return -1;
        if (!counter.isThreadAllocatedMemoryEnabled()) counter.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().getId();
        // Resolve counter/JIT setup before measuring the loop itself.
        counter.getThreadAllocatedBytes(id);
        long before = counter.getThreadAllocatedBytes(id);
        run(fixture, output, kernel, compression, HEIGHT);
        return counter.getThreadAllocatedBytes(id) - before;
    }

    private static void run(Fixture fixture, int[] output, PhotoToneRowProcessor kernel,
            double compression, int height) {
        long checksum = 1;
        for (int y = 0; y < height; y++) {
            int row = y % fixture.colors.length;
            System.arraycopy(fixture.colors[row], 0, output, 0, WIDTH);
            fixture.process(output, row, kernel, compression);
            checksum = 31 * checksum + Arrays.hashCode(output);
        }
        sink = checksum;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone(); Arrays.sort(sorted); return sorted[sorted.length / 2];
    }

    private static final class Fixture {
        // Sixteen deterministic synthetic rows are streamed repeatedly, not a private image.
        final int[][] colors = new int[16][WIDTH];
        final byte[][] before = new byte[16][WIDTH], center = new byte[16][WIDTH], after = new byte[16][WIDTH];
        final byte[][] local = new byte[16][WIDTH], smooth = new byte[16][WIDTH];
        final float[][] base = new float[16][WIDTH];
        Fixture() {
            Random random = new Random(0x13_58464c4950L);
            for (int row = 0; row < colors.length; row++) {
                for (int x = 0; x < WIDTH; x++) {
                    colors[row][x] = 0xff000000 | random.nextInt(0x1000000);
                    int level = PhotoToneCurve.luma(colors[row][x]);
                    center[row][x] = (byte) level;
                    before[row][x] = (byte) bounded(level + random.nextInt(33) - 16);
                    after[row][x] = (byte) bounded(level + random.nextInt(33) - 16);
                    smooth[row][x] = (byte) bounded(level + random.nextInt(25) - 12);
                    local[row][x] = (byte) bounded(level + random.nextInt(49) - 24);
                    base[row][x] = (float) Math.log((random.nextInt(255) + 1) / 255.0);
                }
            }
        }
        void process(int[] output, int row, PhotoToneRowProcessor kernel, double compression) {
            if (kernel == null) LegacyPhotoToneRow.process(output, before[row], center[row], after[row],
                    local[row], smooth[row], base[row], true, compression, 3.5);
            else kernel.process(output, before[row], center[row], after[row], local[row], smooth[row], base[row]);
        }
        private static int bounded(int value) { return Math.max(0, Math.min(255, value)); }
    }
}
