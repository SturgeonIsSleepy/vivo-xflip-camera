package com.sturgeon.xflipcamera.core;

/** Composite RGB input levels: values outside the selected endpoints are clipped. */
public final class InputLevels {
    private final int[] table = new int[256];
    public final int black;
    public final int white;

    public InputLevels(int black, int white) {
        if (black < 0 || white > 255 || black >= white) {
            throw new IllegalArgumentException("Require 0 <= black < white <= 255");
        }
        this.black = black;
        this.white = white;
        for (int i = 0; i < 256; i++) {
            table[i] = Math.max(0, Math.min(255, (int) Math.round((i - black) * 255.0 / (white - black))));
        }
    }

    public int channel(int value) { return table[value]; }

    public void apply(int[] colors) {
        for (int x = 0; x < colors.length; x++) {
            int c = colors[x];
            colors[x] = (c & 0xff000000) | table[(c >>> 16) & 255] << 16
                    | table[(c >>> 8) & 255] << 8 | table[c & 255];
        }
    }

    /** Each RGB sample contributes once; the display and processing use the same sRGB values. */
    public static void addHistogram(long[] bins, int[] colors) {
        if (bins.length != 256) throw new IllegalArgumentException("256 bins required");
        for (int c : colors) {
            bins[(c >>> 16) & 255]++;
            bins[(c >>> 8) & 255]++;
            bins[c & 255]++;
        }
    }
}
