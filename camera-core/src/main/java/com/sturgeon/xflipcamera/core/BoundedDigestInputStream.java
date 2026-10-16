package com.sturgeon.xflipcamera.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Forward-only digest/budget guard below a parser's own mark/reset buffer. Owns and closes its source. */
public final class BoundedDigestInputStream extends InputStream {
    private final InputStream source;
    private final int limit;
    private final BooleanSupplier cancelled;
    private final MessageDigest digest;
    private final byte[] scratch = new byte[16 * 1024];
    private long count;
    private boolean eof, closed;
    private IOException failure;
    private String completedDigest;

    public BoundedDigestInputStream(InputStream source, int limit, BooleanSupplier cancelled) {
        this.source = Objects.requireNonNull(source, "source");
        this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
        if (limit <= 0) throw new IllegalArgumentException("Positive byte budget required");
        this.limit = limit;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
    }

    @Override public int read() throws IOException {
        guard();
        if (eof) return -1;
        try {
            int value = source.read();
            if (value < 0) { eof = true; return -1; }
            if (++count > limit) throw new IOException("JPEG byte budget exceeded");
            digest.update((byte) value); return value;
        } catch (IOException error) { failure = error; throw error; }
    }

    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (offset < 0 || length < 0 || offset > bytes.length - length) throw new IndexOutOfBoundsException();
        if (length == 0) return 0;
        guard();
        if (eof) return -1;
        if (count >= limit) { read(); return -1; } // Only an EOF probe may succeed; another byte throws.
        try {
            int size = source.read(bytes, offset, (int) Math.min(length, limit - count));
            if (size < 0) { eof = true; return -1; }
            if (size == 0) {
                int value = read(); if (value < 0) return -1; bytes[offset] = (byte) value; return 1;
            }
            digest.update(bytes, offset, size); count += size; return size;
        } catch (IOException error) { failure = error; throw error; }
    }

    @Override public long skip(long requested) throws IOException {
        long skipped = 0;
        while (skipped < requested) {
            int size = read(scratch, 0, (int) Math.min(scratch.length, requested - skipped));
            if (size < 0) break;
            skipped += size;
        }
        return skipped;
    }

    /** Drains bytes the metadata parser did not consume, so the fingerprint covers the complete file. */
    public String finishDigest() throws IOException {
        guard();
        if (completedDigest != null) return completedDigest;
        while (read(scratch, 0, scratch.length) >= 0) { /* Bounded reads and cancellation include skipped data. */ }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) {
            result.append(Character.forDigit((value & 255) >>> 4, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        completedDigest = result.toString(); return completedDigest;
    }

    @Override public int available() throws IOException {
        guard();
        try { return (int) Math.min(source.available(), Math.max(0, (long) limit + 1 - count)); }
        catch (IOException error) { failure = error; throw error; }
    }
    @Override public void close() throws IOException { if (!closed) { closed = true; source.close(); } }
    private void guard() throws IOException {
        if (failure != null) throw failure;
        if (closed) throw new IOException("JPEG input is closed");
        if (cancelled.getAsBoolean()) { failure = new InterruptedIOException("JPEG metadata inspection cancelled"); throw failure; }
    }
}
