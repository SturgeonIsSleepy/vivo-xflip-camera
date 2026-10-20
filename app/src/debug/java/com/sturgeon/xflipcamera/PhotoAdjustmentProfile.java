package com.sturgeon.xflipcamera;

import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.os.Bundle;
import com.sturgeon.xflipcamera.core.ProcessingTimings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Synthetic device diagnostics only; not enabled by normal editor/benchmark calls. */
final class PhotoAdjustmentProfile {
    private PhotoAdjustmentProfile() { }

    static ProcessingTimings start() {
        return new ProcessingTimings(ProcessingTimings.Stage.DECODE, () -> new ProcessingTimings.Sample(
                SystemClock.elapsedRealtimeNanos(), Process.getElapsedCpuTime(), Debug.threadCpuTimeNanos()));
    }

    static String json(ProcessingTimings.Snapshot snapshot, String outcome) throws JSONException {
        JSONArray stages = new JSONArray();
        for (ProcessingTimings.Measurement measurement : snapshot.stages()) {
            stages.put(values(measurement.wallNanos(), measurement.processCpuMillis(), measurement.threadCpuNanos())
                    .put("stage",measurement.stage().name()).put("intervals",measurement.intervals()));
        }
        JSONObject receipt = new JSONObject().put("schema",1).put("outcome",outcome)
                .put("wall_clock","elapsedRealtimeNanos")
                .put("process_cpu_scope","entire_app_process")
                .put("thread_cpu_scope","calling_worker_only")
                .put("diagnostic_overhead_included",true)
                .put("stages",stages)
                .put("total",values(snapshot.wallNanos(),snapshot.processCpuMillis(),snapshot.threadCpuNanos()));
        if (snapshot.clockFailed()) receipt.put("diagnostic_error","clock_read_failed");
        return receipt.toString();
    }

    static void append(Bundle result, ProcessingTimings.Snapshot snapshot, String outcome) throws JSONException {
        result.putString("cpu_profile_json",json(snapshot,outcome));
        // Report clock failure as data so the caller can still copy/delete its synthetic output.
        if (snapshot.clockFailed()) result.putString("error","Diagnostic clock read failed");
    }

    private static JSONObject values(long wall, long process, long thread) throws JSONException {
        return new JSONObject().put("wall_ns",value(wall)).put("process_cpu_ms",value(process))
                .put("calling_thread_cpu_ns",value(thread));
    }
    private static Object value(long duration) { return duration < 0 ? JSONObject.NULL : duration; }
}
