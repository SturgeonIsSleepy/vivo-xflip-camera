package com.sturgeon.xflipcamera.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Random;

/** Uses a live, overwritten image as the reader, not an immutable mock source. */
final class UnmodifiedRowWindowChecks {
    private static int assertions;

    static int run() {
        try {
            for (int[] shape : new int[][]{{1,1},{1,17},{19,1},{31,35},{513,1025},
                    {1025,513},{3072,4096},{4096,3072}}) {
                verifyOriginalPixels(shape[0], shape[1], 512, 16);
            }
            Random random = new Random(18743);
            for (int i = 0; i < 48; i++) {
                verifyOriginalPixels(1 + random.nextInt(80), 1 + random.nextInt(90),
                        1 + random.nextInt(17), random.nextInt(24));
            }
            boundsAndCapacity();
            failureIsTerminal();
        } catch (IOException failure) {
            throw new AssertionError("Unexpected source I/O", failure);
        }
        return assertions;
    }

    private static void verifyOriginalPixels(int width, int height, int size, int halo) throws IOException {
        int[] live = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) live[y * width + x] = pixel(x,y);
        boolean[] overwritten = new boolean[height];
        int[] reads = new int[height];
        UnmodifiedRowWindow window = new UnmodifiedRowWindow(width,height,size,halo,(target,offset,first,count)->{
            for (int row = first; row < first + count; row++) {
                check(!overwritten[row], "Never read an already modified source row");
                reads[row]++;
            }
            System.arraycopy(live,first * width,target,offset,count * width);
        });
        int expectedTop = 0;
        while (window.nextBand()) {
            int top = window.bandTop();
            check(top == expectedTop, "Output bands are ordered and never skipped");
            for (int left = 0; left < width; left += size) {
                int x = Math.max(0,left - halo), y = Math.max(0,top - halo);
                int right = (int) Math.min(width,(long) left + size + halo);
                int bottom = (int) Math.min(height,(long) top + size + halo);
                int tileWidth = right - x, tileHeight = bottom - y, stride = tileWidth + 3;
                int[] tile = new int[2 + stride * tileHeight];
                Arrays.fill(tile,0x13579bdf);
                window.copyPixels(tile,2,stride,x,y,tileWidth,tileHeight);
                check(tile[0] == 0x13579bdf && tile[1] == 0x13579bdf, "Destination prefix preserved");
                for (int row = 0; row < tileHeight; row++) {
                    for (int col = 0; col < tileWidth; col++) {
                        check(tile[2 + row * stride + col] == pixel(x + col,y + row),
                                "Every tile/halo pixel equals the frozen-original oracle");
                    }
                    for (int col = tileWidth; col < stride; col++) {
                        check(tile[2 + row * stride + col] == 0x13579bdf, "Destination padding preserved");
                    }
                }
                // A preceding tile/band is deliberately poisoned before the next snapshot read.
                for (int row = top; row < Math.min(height,top + size); row++) {
                    for (int col = left; col < Math.min(width,left + size); col++) {
                        live[row * width + col] ^= 0x00ffffff;
                    }
                }
            }
            Arrays.fill(overwritten,top,Math.min(height,top + size),true);
            expectedTop = Math.min(height,top + size);
        }
        check(expectedTop == height && !window.nextBand(), "Exhaustion is stable");
        for (int row = 0; row < height; row++) {
            check(reads[row] == 1, "Each original row is fetched exactly once");
            for (int col = 0; col < width; col++) {
                check(live[row * width + col] == (pixel(col,row) ^ 0x00ffffff), "Each output pixel written once");
            }
        }
    }

    private static int pixel(int x, int y) { return 0xff000000 | ((x * 7331 ^ y * 57943) & 0x00ffffff); }

    private static void boundsAndCapacity() throws IOException {
        check(UnmodifiedRowWindow.requiredPixelCount(3072,4096,512,16) * 4L == 6_684_672L,
                "Portrait cache is 6,684,672 payload bytes, not a full 50,331,648-byte copy");
        check(UnmodifiedRowWindow.requiredPixelCount(4096,3072,512,16) * 4L == 8_912_896L,
                "Landscape cache capacity uses actual runtime width");
        check(UnmodifiedRowWindow.requiredPixelCount(19,1,512,16) == 19, "Small image is not overallocated");
        check(UnmodifiedRowWindow.requiredPixelCount(1,3,Integer.MAX_VALUE,Integer.MAX_VALUE) == 3,
                "Halo arithmetic does not overflow");
        expect(IllegalArgumentException.class,()->UnmodifiedRowWindow.requiredPixelCount(0,1,1,0));
        expect(IllegalArgumentException.class,()->UnmodifiedRowWindow.requiredPixelCount(1,-1,1,0));
        expect(IllegalArgumentException.class,()->UnmodifiedRowWindow.requiredPixelCount(1,1,0,0));
        expect(IllegalArgumentException.class,()->UnmodifiedRowWindow.requiredPixelCount(1,1,1,-1));
        expect(IllegalArgumentException.class,()->UnmodifiedRowWindow.requiredPixelCount(Integer.MAX_VALUE,2,2,0));
        expect(NullPointerException.class,()->new UnmodifiedRowWindow(1,1,1,0,null));
        UnmodifiedRowWindow window = new UnmodifiedRowWindow(4,9,2,1,(target,offset,first,count)->{});
        expect(IllegalStateException.class,window::bandTop);
        expect(IllegalStateException.class,()->window.copyPixels(new int[1],0,1,0,0,1,1));
        check(window.nextBand(), "First band loads");
        expect(NullPointerException.class,()->window.copyPixels(null,0,1,0,0,1,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],-1,1,0,0,1,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,1,0,0,2,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,1,0,3,1,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,1,-1,0,1,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,1,0,0,0,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,1,Integer.MAX_VALUE,0,2,1));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,Integer.MAX_VALUE,0,0,1,2));
        expect(IndexOutOfBoundsException.class,()->window.copyPixels(new int[4],0,2,0,0,2,3));
        while (window.nextBand()) { /* Exhaust without modifying the fixture. */ }
        expect(IllegalStateException.class,window::bandTop);
        expect(IllegalStateException.class,()->window.copyPixels(new int[1],0,1,0,0,1,1));
    }

    private static void failureIsTerminal() {
        for (Throwable failure : new Throwable[]{new IOException("read failure"),
                new InterruptedIOException("cancel"),new IllegalArgumentException("reader"),new AssertionError("reader")}) {
            int[] calls = {0};
            UnmodifiedRowWindow window = new UnmodifiedRowWindow(3,12,2,1,(target,offset,first,count)->{
                if (++calls[0] == 2) {
                    target[offset] = -123;
                    if (failure instanceof IOException io) throw io;
                    if (failure instanceof RuntimeException runtime) throw runtime;
                    throw (Error) failure;
                }
            });
            try {
                check(window.nextBand(), "First read before failure succeeds");
                window.nextBand();
                throw new AssertionError("Reader failure was swallowed");
            } catch (Throwable actual) {
                check(actual == failure, "Original read/cancellation/Error instance propagates");
            }
            expect(IllegalStateException.class,()->window.copyPixels(new int[9],0,3,0,1,3,3));
            expect(IllegalStateException.class,window::nextBand);
            check(calls[0] == 2, "No retry or stale pixels after a partial failed read");
        }
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void expect(Class<? extends Throwable> type, Action action) {
        try { action.run(); } catch (Throwable actual) {
            check(type.isInstance(actual), "Expected explicit invalid-state/bounds failure");return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
