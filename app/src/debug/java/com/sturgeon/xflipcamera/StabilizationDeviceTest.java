package com.sturgeon.xflipcamera;

import android.app.Instrumentation;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** ADB-only debug harness exercising the same app UID, decoder, GL effect, encoder and publisher. */
public final class StabilizationDeviceTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch done = new CountDownLatch(1);
        Bundle result = new Bundle();
        VideoStabilizer[] job = new VideoStabilizer[1];
        try {
            File source = new File(getTargetContext().getFilesDir(), "video-stabilization-test.mp4");
            if (!source.isFile()) throw new IllegalStateException("Push the synthetic input to app files first");
            getTargetContext().startActivity(new Intent(getTargetContext(), SystemCaptureActivity.class)
                    .putExtra("manual_entry", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            try (MediaMetadataRetriever decoder = new MediaMetadataRetriever()) {
                decoder.setDataSource(source.getAbsolutePath());
                for (int i = 0; i < 3; i++) {
                    Bitmap frame = decoder.getScaledFrameAtTime(i * 100000L, MediaMetadataRetriever.OPTION_CLOSEST, 160, 160);
                    if (frame == null) throw new IllegalStateException("Fixture decode failed");
                    try (OutputStream output = Files.newOutputStream(new File(getTargetContext().getFilesDir(),
                            "video-stabilization-frame" + i + ".png").toPath())) {
                        frame.compress(Bitmap.CompressFormat.PNG, 100, output);
                    } finally { frame.recycle(); }
                }
            }
            runOnMainSync(() -> {
                job[0] = new VideoStabilizer(getTargetContext(), executor);
                job[0].start(Uri.fromFile(source), new VideoStabilizer.Listener() {
                    @Override public void progress(String message) { }
                    @Override public void failed(String reason) { result.putString("error", reason); done.countDown(); }
                    @Override public void completed(Uri saved) {
                        executor.execute(() -> {
                            try (InputStream input = getTargetContext().getContentResolver().openInputStream(saved);
                                    OutputStream output = Files.newOutputStream(new File(getTargetContext().getFilesDir(),
                                            "video-stabilization-result.mp4").toPath())) {
                                if (input == null) throw new IllegalStateException("Missing result");
                                input.transferTo(output);
                                result.putString("output_uri", saved.toString());
                            } catch (Exception error) { result.putString("error", error.toString()); }
                            finally { done.countDown(); }
                        });
                    }
                });
            });
            if (!done.await(60, TimeUnit.SECONDS)) result.putString("error", "Processing timeout");
        } catch (Exception error) { result.putString("error", error.toString()); }
        finally {
            runOnMainSync(() -> { if (job[0] != null) job[0].cancel(); });
            executor.shutdown();
        }
        finish(result.containsKey("error") ? 0 : -1, result);
    }
}
