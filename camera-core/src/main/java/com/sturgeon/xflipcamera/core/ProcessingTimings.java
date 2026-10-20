package com.sturgeon.xflipcamera.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Optional single-worker, non-overlapping intervals. No Android clocks or image data here. */
public final class ProcessingTimings {
    public enum Stage {
        DECODE, NLM_SETUP, NLM_ROW_WINDOW, NLM_TILE_INPUT, NLM_FILTER, NLM_TILE_OUTPUT,
        NLM_RELEASE, INPUT_LEVELS, TONE_DETAIL, JPEG_TRANSACTION, BITMAP_RELEASE
    }
    public record Sample(long wallNanos, long processCpuMillis, long threadCpuNanos) { }
    @FunctionalInterface public interface Clock { Sample read(); }
    /** A negative duration means unsupported/invalid, never a measured zero. */
    public record Measurement(Stage stage, long intervals, long wallNanos,
            long processCpuMillis, long threadCpuNanos) { }
    public record Snapshot(List<Measurement> stages, long wallNanos,
            long processCpuMillis, long threadCpuNanos, boolean clockFailed) {
        public Snapshot { stages = List.copyOf(stages); }
        public Snapshot(List<Measurement> stages, long wallNanos, long processCpuMillis, long threadCpuNanos) {
            this(stages,wallNanos,processCpuMillis,threadCpuNanos,false);
        }
    }

    private static final Sample UNKNOWN = new Sample(-1,-1,-1);
    private final Clock clock;
    private final Thread owner = Thread.currentThread();
    private final long[][] durations = new long[Stage.values().length][3];
    private final long[] intervals = new long[Stage.values().length];
    private Stage current;
    private Sample previous;
    private Snapshot finished;
    private boolean clockFailed;

    public ProcessingTimings(Stage first, Clock clock) {
        current = Objects.requireNonNull(first);
        this.clock = Objects.requireNonNull(clock);
        previous = sample();
    }

    public void mark(Stage next) {
        requireOwner();
        if (finished != null) throw new IllegalStateException("Timing already finished");
        Objects.requireNonNull(next);
        if (next == current) return;
        collect();
        current = next;
    }

    /** Closes the final interval once. Repeat calls return the same immutable receipt. */
    public Snapshot finish() {
        requireOwner();
        if (finished != null) return finished;
        collect();
        List<Measurement> stages = new ArrayList<>();
        long wall = 0, process = 0, thread = 0;
        for (Stage stage : Stage.values()) {
            int index = stage.ordinal();
            if (intervals[index] == 0) continue;
            long[] values = durations[index];
            stages.add(new Measurement(stage, intervals[index], values[0], values[1], values[2]));
            wall = add(wall, values[0]);
            process = add(process, values[1]);
            thread = add(thread, values[2]);
        }
        finished = new Snapshot(stages, wall, process, thread, clockFailed);
        return finished;
    }

    private void collect() {
        Sample next = sample();
        int index = current.ordinal();
        long[] values = durations[index];
        values[0] = add(values[0], delta(previous.wallNanos(), next.wallNanos()));
        values[1] = add(values[1], delta(previous.processCpuMillis(), next.processCpuMillis()));
        values[2] = add(values[2], delta(previous.threadCpuNanos(), next.threadCpuNanos()));
        intervals[index]++;
        previous = next;
    }

    /** A diagnostic RuntimeException must not replace processing failure or skip image cleanup. */
    private Sample sample() {
        if (clockFailed) return UNKNOWN;
        try { return Objects.requireNonNull(clock.read()); }
        catch (RuntimeException unavailable) {
            clockFailed = true;
            return UNKNOWN;
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Timing belongs to another worker");
    }
    private static long delta(long before, long after) {
        if (before < 0 || after < before) return -1;
        return after - before;
    }
    private static long add(long a, long b) {
        if (a < 0 || b < 0 || Long.MAX_VALUE - a < b) return -1;
        return a + b;
    }
}
