package com.sturgeon.xflipcamera.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static com.sturgeon.xflipcamera.core.ProcessingTimings.Stage.*;

/** Fake cumulative counters: verifies accounting, not device CPU performance. */
final class ProcessingTimingsChecks {
    private static int assertions;
    private ProcessingTimingsChecks() { }
    static int run() {
        assertions = 0;
        Script clock = new Script(new long[][]{{100,10,20},{110,15,24},{130,35,30},{160,60,40},{200,90,50}});
        ProcessingTimings timing = new ProcessingTimings(DECODE,clock);
        timing.mark(DECODE);
        check(clock.index == 1,"Repeated marker does not sample or split an interval");
        timing.mark(NLM_FILTER); timing.mark(NLM_TILE_OUTPUT); timing.mark(NLM_FILTER);
        ProcessingTimings.Snapshot result = timing.finish();
        check(result.wallNanos() == 100 && result.processCpuMillis() == 80 && result.threadCpuNanos() == 30,
                "Different counter units remain independent");
        check(result.stages().size() == 3,"Unused stages omitted");
        ProcessingTimings.Measurement filter = result.stages().stream().filter(s -> s.stage() == NLM_FILTER).findFirst().orElseThrow();
        check(filter.intervals() == 2 && filter.wallNanos() == 60 && filter.processCpuMillis() == 50
                && filter.threadCpuNanos() == 16,"Repeated tiles aggregate without retaining per-tile records");
        check(timing.finish() == result && clock.index == 5,"Idempotent finish reads no extra clocks");
        fails(IllegalStateException.class,() -> timing.mark(DECODE));
        fails(UnsupportedOperationException.class,() -> result.stages().clear());

        ProcessingTimings parallel = new ProcessingTimings(NLM_FILTER,new Script(new long[][]{{0,0,0},{1_000_000,4,0}}));
        check(parallel.finish().processCpuMillis() == 4,"Parallel process CPU may exceed elapsed wall time");
        check(parallel.finish().threadCpuNanos() == 0,"Measured coarse/idle zero is retained");
        ProcessingTimings unavailable = new ProcessingTimings(DECODE,new Script(new long[][]{{0,0,-1},{7,3,-1},{12,8,9}}));
        unavailable.mark(JPEG_TRANSACTION);
        ProcessingTimings.Snapshot unknown = unavailable.finish();
        check(unknown.threadCpuNanos() == -1 && unknown.wallNanos() == 12,"Unsupported thread clock does not erase wall time");
        check(unknown.stages().stream().allMatch(s -> s.threadCpuNanos() == -1),"Missing interval endpoint is unknown, not zero");

        ProcessingTimings regressed = new ProcessingTimings(DECODE,new Script(new long[][]{{100,20,30},{90,19,29},{110,30,50},{120,40,60}}));
        regressed.mark(JPEG_TRANSACTION);regressed.mark(DECODE);
        ProcessingTimings.Snapshot invalid = regressed.finish();
        check(invalid.wallNanos() == -1 && invalid.processCpuMillis() == -1 && invalid.threadCpuNanos() == -1,
                "Regressions invalidate totals rather than emitting negative durations or fake zeros");
        check(invalid.stages().get(0).wallNanos() == -1,"Unknown stage stays unknown after subsequent valid interval");
        check(invalid.stages().get(1).wallNanos() == 20,"Unrelated valid stage remains usable");

        long[] ticks = {0};
        ProcessingTimings all = new ProcessingTimings(DECODE,() -> {
            long now = ticks[0]++; return new ProcessingTimings.Sample(now * 10,now * 2,now * 3);
        });
        for (int tile = 0; tile < 200; tile++) {
            all.mark(NLM_ROW_WINDOW);all.mark(NLM_TILE_INPUT);all.mark(NLM_FILTER);all.mark(NLM_TILE_OUTPUT);
        }
        all.mark(BITMAP_RELEASE);
        ProcessingTimings.Snapshot many = all.finish();
        check(many.stages().size() == 6,"Storage bounded by fixed stages, not tile count");
        check(many.stages().stream().mapToLong(ProcessingTimings.Measurement::wallNanos).sum() == many.wallNanos(),"Exclusive wall durations sum exactly");
        check(many.stages().stream().mapToLong(ProcessingTimings.Measurement::processCpuMillis).sum() == many.processCpuMillis(),"Process CPU intervals sum exactly");
        check(many.stages().stream().mapToLong(ProcessingTimings.Measurement::threadCpuNanos).sum() == many.threadCpuNanos(),"Calling-thread intervals sum exactly");
        check(many.wallNanos() == (ticks[0]-1)*10,"No lost or nested wall interval");

        List<ProcessingTimings.Measurement> mutable = new java.util.ArrayList<>(many.stages());
        ProcessingTimings.Snapshot copied = new ProcessingTimings.Snapshot(mutable,1,2,3); mutable.clear();
        check(copied.stages().size() == 6,"Receipt does not alias mutable input");
        ProcessingTimings confined = new ProcessingTimings(DECODE,new Script(new long[][]{{0,0,0},{1,1,1}}));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {try { confined.mark(NLM_FILTER); } catch (Throwable expected) { failure.set(expected); }});
        other.start();
        try { other.join(); } catch (InterruptedException unexpected) { Thread.currentThread().interrupt(); throw new AssertionError(unexpected); }
        check(failure.get() instanceof IllegalStateException,"Another worker cannot corrupt thread CPU accounting");
        check(confined.finish().wallNanos() == 1,"Rejected cross-thread use did not sample or mutate timing");
        fails(NullPointerException.class,() -> new ProcessingTimings(null,clock));
        fails(NullPointerException.class,() -> new ProcessingTimings(DECODE,null));
        return assertions;
    }
    private static final class Script implements ProcessingTimings.Clock {
        final long[][] samples; int index;
        Script(long[][] samples) { this.samples = samples; }
        @Override public ProcessingTimings.Sample read() {
            long[] s = samples[index++]; return new ProcessingTimings.Sample(s[0],s[1],s[2]);
        }
    }
    private static void fails(Class<? extends Throwable> kind, Runnable action) {
        try { action.run(); } catch (Throwable failure) { check(kind.isInstance(failure),"Expected " + kind.getName()); return; }
        throw new AssertionError("Expected " + kind.getName());
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
