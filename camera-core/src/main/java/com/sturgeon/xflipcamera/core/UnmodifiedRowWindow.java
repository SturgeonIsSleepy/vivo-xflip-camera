package com.sturgeon.xflipcamera.core;

import java.io.IOException;
import java.util.Objects;

/**
 * Forward-only snapshots for an in-place, tiled filter. Advance once per output band, then
 * write only that band. Retained halo rows must never be read back from the modified image.
 * Single-threaded; the reader must fill the requested original rows without retaining its array.
 */
public final class UnmodifiedRowWindow {
    @FunctionalInterface
    public interface Reader {
        void read(int[] destination, int offset, int firstRow, int rowCount) throws IOException;
    }

    private final int width;
    private final int height;
    private final int bandSize;
    private final int halo;
    private final Reader reader;
    private final int[] rows;
    private int nextTop;
    private int bandTop = -1;
    private int firstRow;
    private int rowCount;
    private boolean failed;

    public UnmodifiedRowWindow(int width, int height, int bandSize, int halo, Reader reader) {
        int capacity = requiredPixelCount(width, height, bandSize, halo);
        this.reader = Objects.requireNonNull(reader, "reader");
        this.width = width;
        this.height = height;
        this.bandSize = bandSize;
        this.halo = halo;
        rows = new int[capacity];
    }

    /** Pixel payload only, not a Java/native peak-memory estimate. Does not allocate. */
    public static int requiredPixelCount(int width, int height, int bandSize, int halo) {
        if (width <= 0 || height <= 0 || bandSize <= 0 || halo < 0) {
            throw new IllegalArgumentException("Invalid row-window dimensions");
        }
        long count = width * Math.min((long) height, bandSize + 2L * halo);
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Row window too large");
        return (int) count;
    }

    /** Reads each source row once, before it can be overwritten. A failed read is terminal. */
    public boolean nextBand() throws IOException {
        if (failed) throw new IllegalStateException("Failed row window cannot be reused");
        if (nextTop == height) {
            bandTop = -1;
            return false;
        }
        failed = true;
        int top = nextTop;
        int start = Math.max(0, top - halo);
        int end = (int) Math.min((long) height, (long) top + bandSize + halo);
        int retained = Math.max(0, firstRow + rowCount - start);
        if (retained > 0) {
            System.arraycopy(rows, (start - firstRow) * width, rows, 0, retained * width);
        }
        int readStart = start + retained;
        if (readStart < end) reader.read(rows, retained * width, readStart, end - readStart);
        firstRow = start;
        rowCount = end - start;
        bandTop = top;
        nextTop = (int) Math.min((long) height, (long) top + bandSize);
        failed = false;
        return true;
    }

    public int bandTop() {
        requireBand();
        return bandTop;
    }

    /** Copies a tile, including its halo, from the current snapshot into caller-owned storage. */
    public void copyPixels(int[] destination, int offset, int stride, int x, int y,
            int copyWidth, int copyHeight) {
        requireBand();
        Objects.requireNonNull(destination, "destination");
        if (x < 0 || y < firstRow || copyWidth <= 0 || copyHeight <= 0
                || (long) x + copyWidth > width || (long) y + copyHeight > firstRow + rowCount
                || offset < 0 || stride < copyWidth
                || (long) offset + (copyHeight - 1L) * stride + copyWidth > destination.length) {
            throw new IndexOutOfBoundsException("Tile outside row window or destination");
        }
        for (int row = 0; row < copyHeight; row++) {
            System.arraycopy(rows, (y - firstRow + row) * width + x,
                    destination, offset + row * stride, copyWidth);
        }
    }

    private void requireBand() {
        if (failed || bandTop < 0) throw new IllegalStateException("No usable row band");
    }
}
