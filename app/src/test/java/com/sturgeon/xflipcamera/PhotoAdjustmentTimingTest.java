package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import com.sturgeon.xflipcamera.core.ProcessingTimings;
import java.io.InterruptedIOException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Native Bitmap/levels path only; ARM64 OpenCV NLM still requires an actual device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PhotoAdjustmentTimingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void apkIdentityUsesExactKnownShaAndStableReadOnlyFile() throws Exception {
        File file = temporary.newFile("synthetic-base.apk");byte[] bytes = {97,98,99};
        Files.write(file.toPath(),bytes);
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        assertEquals(expected,PhotoAdjustmentDeviceTest.apkDigest(file));
        PhotoAdjustmentDeviceTest.verifyApk(file,expected);
        assertArrayEquals(bytes,Files.readAllBytes(file.toPath()));
    }
    @Test public void changedOrMissingApkCannotPassIdentityCheck() throws Exception {
        File file = temporary.newFile("synthetic-changing.apk");Files.write(file.toPath(),new byte[]{1,2,3});
        String before = PhotoAdjustmentDeviceTest.apkDigest(file);Files.write(file.toPath(),new byte[]{1,2,4});
        assertThrows(IllegalStateException.class,() -> PhotoAdjustmentDeviceTest.verifyApk(file,before));
        Files.delete(file.toPath());
        assertThrows(IOException.class,() -> PhotoAdjustmentDeviceTest.verifyApk(file,before));
    }
    @Test public void apkIdentityReadsTrailingBytesBeyondHashBuffer() throws Exception {
        File file = temporary.newFile("synthetic-large.apk");byte[] bytes = new byte[65537];
        java.util.Arrays.fill(bytes,(byte)7);Files.write(file.toPath(),bytes);
        String before = PhotoAdjustmentDeviceTest.apkDigest(file);bytes[bytes.length-1]=8;Files.write(file.toPath(),bytes);
        assertNotEquals(before,PhotoAdjustmentDeviceTest.apkDigest(file));
    }
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
    @Test public void failedClockIsExportedAsDiagnosticErrorWithoutThrowingFromReceiptAppend() throws Exception {
        ProcessingTimings timing = new ProcessingTimings(ProcessingTimings.Stage.DECODE,() -> {
            throw new UnsupportedOperationException("Synthetic detail that must not appear in public JSON");
        });
        android.os.Bundle result = new android.os.Bundle();
        PhotoAdjustmentProfile.append(result,timing.finish(),"saved");
        assertTrue(result.containsKey("error"));
        String text = result.getString("cpu_profile_json");
        org.json.JSONObject json = new org.json.JSONObject(text);
        assertEquals("saved",json.getString("outcome"));
        assertEquals("clock_read_failed",json.getString("diagnostic_error"));
        assertTrue(json.getJSONObject("total").isNull("wall_ns"));
        assertFalse(text.contains("Synthetic detail"));
        assertEquals("Diagnostic clock read failed",result.getString("error"));
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
