package com.sturgeon.xflipcamera.core;

final class InputLevelsChecks {
    static int run() {
        int checks = 0;
        for (int[] range : new int[][]{{0,255},{20,220},{100,101},{0,1},{254,255}}) {
            InputLevels levels = new InputLevels(range[0],range[1]);
            int previous = -1;
            for (int value = 0; value < 256; value++) {
                int expected = value <= range[0] ? 0 : value >= range[1] ? 255
                        : (int) Math.round((value-range[0])*255.0/(range[1]-range[0]));
                if (levels.channel(value) != expected || levels.channel(value) < previous) throw new AssertionError("Input level clipping/monotonicity");
                previous = levels.channel(value); checks++;
            }
        }
        int[] pixels = {0x7f102030,0xff000000,0xffffffff,0xffff0000};
        int[] original = pixels.clone(); new InputLevels(0,255).apply(pixels);
        if (!java.util.Arrays.equals(pixels,original)) throw new AssertionError("Identity preserves RGB and alpha"); checks++;
        new InputLevels(32,224).apply(pixels);
        if (pixels[0] != 0x7f000015 || pixels[1] != 0xff000000 || pixels[2] != 0xffffffff || pixels[3] != 0xffff0000) throw new AssertionError("RGB composite levels and alpha"); checks++;
        long[] histogram = new long[256]; InputLevels.addHistogram(histogram,original);
        if (histogram[0] != 5 || histogram[255] != 4 || java.util.Arrays.stream(histogram).sum() != 12) throw new AssertionError("Composite RGB histogram"); checks++;
        for (int[] range : new int[][]{{-1,255},{0,256},{50,50},{100,20}}) {
            try { new InputLevels(range[0],range[1]); throw new AssertionError("Invalid endpoints accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        return checks;
    }
}
