package com.sturgeon.xflipcamera.core;

import java.util.Arrays;
import java.util.Random;

final class PhotoToneRowChecks {
    private static int assertions;
    private PhotoToneRowChecks() { }

    static int run() {
        assertions = 0;
        allLumaValuesAndHdrBoundariesMatch();
        randomRowsMatchAndInputsStayReadOnly();
        disabledBuffersAreNotRequired();
        invalidInputsDoNotPartiallyWrite();
        return assertions;
    }

    private static void allLumaValuesAndHdrBoundariesMatch() {
        Row row = new Row(256);
        for (double compression : new double[]{0.55, 0.7, 0.998999999999, 0.999, 1}) {
            for (double noise : new double[]{0, 2, 2.5, 5, 8, 16}) {
                for (boolean detail : new boolean[]{false, true}) {
                    PhotoToneRowProcessor kernel = new PhotoToneRowProcessor(detail, compression, noise);
                    for (int sample = 0; sample < 48; sample++) {
                        for (int x = 0; x < 256; x++) {
                            row.colors[x] = switch (sample % 8) {
                                case 0 -> 0xff000000;
                                case 1 -> 0xffff0000;
                                case 2 -> 0xff00ff00;
                                case 3 -> 0xff0000ff;
                                case 4 -> 0xff000000 | (x * 0x010101);
                                case 5 -> 0x80d04422;
                                case 6 -> 0x00123456;
                                default -> 0xffe8c048;
                            };
                            row.center[x] = (byte) x;
                            row.before[x] = (byte) Math.max(0, x - 11);
                            row.after[x] = (byte) Math.min(255, x + 7);
                            row.local[x] = (byte) Math.min(255, x + 31);
                            row.smooth[x] = (byte) Math.max(0, x - sample % 27);
                            row.base[x] = (float) (-8 + sample * 0.2 + x / 1024.0);
                        }
                        compare(row, kernel, detail, compression, noise);
                    }
                }
            }
        }
    }

    private static void randomRowsMatchAndInputsStayReadOnly() {
        Random random = new Random(0x58464c4950L);
        for (int width : new int[]{1, 2, 3, 7, 64, 3072, 4096}) {
            Row row = new Row(width);
            for (int iteration = 0; iteration < 24; iteration++) {
                row.before = new byte[width]; row.after = new byte[width];
                random.nextBytes(row.before); random.nextBytes(row.center); random.nextBytes(row.after);
                random.nextBytes(row.local); random.nextBytes(row.smooth);
                for (int x = 0; x < width; x++) {
                    row.colors[x] = random.nextInt();
                    row.base[x] = (float) Math.log((random.nextInt(255) + 1) / 255.0);
                }
                // Same row aliasing is how the image's top/bottom clamp may be represented.
                if (iteration == 0) row.before = row.center;
                if (iteration == 1) row.after = row.center;
                boolean detail = iteration % 2 == 0;
                double compression = iteration % 3 == 0 ? 1 : 0.55 + random.nextDouble() * 0.449;
                double noise = 2 + random.nextDouble() * 14;
                byte[][] snapshots = {row.before.clone(), row.center.clone(), row.after.clone(),
                        row.local.clone(), row.smooth.clone()};
                float[] base = row.base.clone();
                compare(row, new PhotoToneRowProcessor(detail, compression, noise), detail, compression, noise);
                check(Arrays.equals(snapshots[0], row.before), "before unchanged");
                check(Arrays.equals(snapshots[1], row.center), "center unchanged");
                check(Arrays.equals(snapshots[2], row.after), "after unchanged");
                check(Arrays.equals(snapshots[3], row.local), "local unchanged");
                check(Arrays.equals(snapshots[4], row.smooth), "smooth unchanged");
                check(Arrays.equals(base, row.base), "HDR base unchanged");
            }
        }
    }

    private static void disabledBuffersAreNotRequired() {
        Row row = new Row(3);
        Arrays.fill(row.colors, 0xff777777);
        Arrays.fill(row.center, (byte) 119);
        new PhotoToneRowProcessor(false, 1, 2).process(row.colors, null, row.center, null, null, null, null);
        for (int color : row.colors) check(color == 0xff777777, "disabled branches need no buffers");
        int[] tiny = {0xffffffff};
        new PhotoToneRowProcessor(false, 1, 2).process(tiny, null, new byte[]{42, 99}, null, null, null, null);
        check(tiny[0] == 0xff2a2a2a, "extra samples ignored");
    }

    private static void invalidInputsDoNotPartiallyWrite() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 1.01}) {
            expectFailure(() -> new PhotoToneRowProcessor(true, invalid, 2));
        }
        for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, -1}) {
            expectFailure(() -> new PhotoToneRowProcessor(true, 0.55, invalid));
        }
        PhotoToneRowProcessor kernel = new PhotoToneRowProcessor(true, 0.55, 2);
        Row row = new Row(4);
        Arrays.fill(row.colors, 0xffb01234);
        int[] original = row.colors.clone();
        expectFailure(() -> kernel.process(row.colors, row.before, row.center, row.after, row.local, new byte[3], row.base));
        check(Arrays.equals(original, row.colors), "short smooth rejected before writes");
        expectFailure(() -> kernel.process(row.colors, row.before, row.center, row.after, row.local, row.smooth, null));
        check(Arrays.equals(original, row.colors), "missing HDR base rejected before writes");
        expectFailure(() -> kernel.process(row.colors, null, row.center, row.after, row.local, row.smooth, row.base));
        expectFailure(() -> kernel.process(row.colors, row.before, null, row.after, row.local, row.smooth, row.base));
        expectFailure(() -> kernel.process(new int[0], null, new byte[0], null, null, null, null));
        expectFailure(() -> kernel.process(null, null, null, null, null, null, null));
        check(Arrays.equals(original, row.colors), "all input failures leave colors unchanged");
    }

    private static void compare(Row row, PhotoToneRowProcessor kernel, boolean detail, double compression, double noise) {
        int[] expected = row.colors.clone();
        LegacyPhotoToneRow.process(expected, row.before, row.center, row.after, row.local, row.smooth, row.base,
                detail, compression, noise);
        kernel.process(row.colors, row.before, row.center, row.after, row.local, row.smooth, row.base);
        for (int x = 0; x < expected.length; x++) check(row.colors[x] == expected[x], "bit-exact legacy ARGB");
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | NullPointerException expected) {
            assertions++; return;
        }
        throw new AssertionError("Expected invalid input rejection");
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static final class Row {
        final int[] colors;
        byte[] before, center, after;
        final byte[] local, smooth;
        final float[] base;
        Row(int width) {
            colors = new int[width]; before = new byte[width]; center = new byte[width]; after = new byte[width];
            local = new byte[width]; smooth = new byte[width]; base = new float[width];
        }
    }
}
