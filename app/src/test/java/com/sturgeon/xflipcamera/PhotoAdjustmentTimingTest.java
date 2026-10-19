package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import com.sturgeon.xflipcamera.core.ProcessingTimings;
import java.io.InterruptedIOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Native Bitmap/levels path only; ARM64 OpenCV NLM still requires an actual device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PhotoAdjustmentTimingTest {
    @Test public void profilingPreservesIdentityAndManualLevelPixelsAndDescriptions() throws Exception {
        for (int[] limits : new int[][]{{0,255},{40,215},{120,121}}) {
            Bitmap plain = fixture(), profiled = plain.copy(Bitmap.Config.ARGB_8888,true);
            try {
                PhotoAdjustmentProcessor.Options options = new PhotoAdjustmentProcessor.Options(limits[0],limits[1],false,false,false);
                String expected = PhotoAdjustmentProcessor.apply(plain,options,()->false,message->{});
                ProcessingTimings timing = timing();
                String actual = PhotoAdjustmentProcessor.apply(profiled,options,()->false,message->{},768,4,timing);
                assertEquals(expected,actual); assertTrue(plain.sameAs(profiled));
                assertEquals(java.util.List.of(ProcessingTimings.Stage.INPUT_LEVELS,ProcessingTimings.Stage.TONE_DETAIL),
                        timing.finish().stages().stream().map(ProcessingTimings.Measurement::stage).toList());
                assertEquals(2,timing.finish().wallNanos());
            } finally { plain.recycle();profiled.recycle(); }
        }
    }
    @Test public void cancellationBeforeApplyStillPreservesWorkingPixels() {
        Bitmap bitmap = fixture(), before = bitmap.copy(Bitmap.Config.ARGB_8888,false);
        try {
            ProcessingTimings timing = timing();
            assertThrows(InterruptedIOException.class,() -> PhotoAdjustmentProcessor.apply(bitmap,
                    new PhotoAdjustmentProcessor.Options(40,215,false,false,false),()->true,message->{},768,4,timing));
            assertTrue(bitmap.sameAs(before));assertEquals(1,timing.finish().stages().size());
        } finally { bitmap.recycle();before.recycle(); }
    }
    @Test public void profilingDoesNotChangeCancellationCheckSequence() {
        Bitmap plain = fixture(), profiled = plain.copy(Bitmap.Config.ARGB_8888,true);
        AtomicInteger a = new AtomicInteger(), b = new AtomicInteger();
        PhotoAdjustmentProcessor.Options options = new PhotoAdjustmentProcessor.Options(40,215,false,false,false);
        try {
            assertThrows(InterruptedIOException.class,() -> PhotoAdjustmentProcessor.apply(plain,options,()->a.incrementAndGet()>=5,message->{}));
            ProcessingTimings timing = timing();
            assertThrows(InterruptedIOException.class,() -> PhotoAdjustmentProcessor.apply(profiled,options,()->b.incrementAndGet()>=5,message->{},768,4,timing));
            assertEquals(a.get(),b.get());assertTrue(plain.sameAs(profiled));
            assertEquals(ProcessingTimings.Stage.INPUT_LEVELS,timing.finish().stages().get(0).stage());
        } finally { plain.recycle();profiled.recycle(); }
    }
    @Test public void debugJsonKeepsUnknownCountersAndExplicitClockScopes() throws Exception {
        long[] samples = {0};
        ProcessingTimings timing = new ProcessingTimings(ProcessingTimings.Stage.DECODE,() -> {
            long n = samples[0]++;return new ProcessingTimings.Sample(n*10,n*2,-1);
        });
        timing.mark(ProcessingTimings.Stage.JPEG_TRANSACTION);
        org.json.JSONObject json = new org.json.JSONObject(PhotoAdjustmentProfile.json(timing.finish(),"saved"));
        assertEquals(1,json.getInt("schema"));assertEquals("saved",json.getString("outcome"));
        assertEquals("elapsedRealtimeNanos",json.getString("wall_clock"));
        assertEquals("entire_app_process",json.getString("process_cpu_scope"));
        assertEquals("calling_worker_only",json.getString("thread_cpu_scope"));
        assertTrue(json.getBoolean("diagnostic_overhead_included"));
        assertEquals(20,json.getJSONObject("total").getLong("wall_ns"));
        assertEquals(4,json.getJSONObject("total").getLong("process_cpu_ms"));
        assertTrue(json.getJSONObject("total").isNull("calling_thread_cpu_ns"));
        assertEquals(2,json.getJSONArray("stages").length());
        assertEquals("DECODE",json.getJSONArray("stages").getJSONObject(0).getString("stage"));
        assertTrue(json.getJSONArray("stages").getJSONObject(0).isNull("calling_thread_cpu_ns"));
    }
    private static ProcessingTimings timing() {
        long[] sample = {0};
        return new ProcessingTimings(ProcessingTimings.Stage.INPUT_LEVELS,() -> {
            long n = sample[0]++; return new ProcessingTimings.Sample(n,n,n);
        });
    }
    private static Bitmap fixture() {
        Bitmap bitmap = Bitmap.createBitmap(37,29,Bitmap.Config.ARGB_8888);
        int[] pixels = new int[37*29];Random random = new Random(20261020);
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0xff000000 | random.nextInt(0x1000000);
        bitmap.setPixels(pixels,0,37,0,0,37,29);return bitmap;
    }
}
