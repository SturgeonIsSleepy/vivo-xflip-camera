package com.sturgeon.xflipcamera;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.effect.MatrixTransformation;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import com.sturgeon.xflipcamera.core.PendingMediaWrite;
import com.sturgeon.xflipcamera.core.VideoMotionPlan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** One cancellable, recording-afterwards job. Originals are read only. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class VideoStabilizer {
    public interface Listener {
        void progress(String message);
        void completed(Uri result);
        void failed(String reason);
    }

    private final Context context;
    private final ExecutorService executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean active = new AtomicBoolean(true);
    private Transformer transformer;
    private volatile File temporary;

    public VideoStabilizer(Context context, ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.executor = executor;
    }

    public void start(Uri original, Listener listener) {
        executor.execute(() -> {
            try {
                VideoMotionPlan plan = analyze(original);
                if (!active.get()) return;
                temporary = File.createTempFile("stabilized-", ".mp4", context.getCacheDir());
                if (!temporary.delete()) throw new IOException("Cannot prepare export file");
                main.post(() -> {
                    if (!active.get()) return;
                    listener.progress("正在生成防抖视频，原片保留");
                    export(original, plan, listener);
                });
            } catch (Exception error) { fail(listener, error); }
        });
    }

    private VideoMotionPlan analyze(Uri original) throws Exception {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(context, original);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration == null) throw new IOException("视频时长不可读取");
            long durationMs = Long.parseLong(duration);
            if (durationMs < 300 || durationMs > 60000) throw new IOException("第一版支持 0.3 至 60 秒视频");
            String transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER);
            if (String.valueOf(MediaFormat.COLOR_TRANSFER_ST2084).equals(transfer)
                    || String.valueOf(MediaFormat.COLOR_TRANSFER_HLG).equals(transfer)) {
                throw new IOException("第一版暂不处理 HDR 视频");
            }
            int size = (int) ((durationMs - 1) / 100) + 1;
            long[] times = new long[size];
            VideoMotionPlan.Shift[] shifts = new VideoMotionPlan.Shift[size];
            byte[] previous = null;
            int width = 0, height = 0;
            for (int i = 0; i < size; i++) {
                if (!active.get()) throw new IOException("已取消");
                times[i] = i * 100000L;
                Bitmap frame = retriever.getScaledFrameAtTime(times[i], MediaMetadataRetriever.OPTION_CLOSEST, 160, 160);
                if (frame == null) throw new IOException("视频帧无法解码");
                byte[] gray;
                try {
                    if (i == 0) { width = frame.getWidth(); height = frame.getHeight(); }
                    if (frame.getWidth() != width || frame.getHeight() != height) throw new IOException("视频尺寸发生变化");
                    int[] pixels = new int[width * height];
                    frame.getPixels(pixels, 0, width, 0, 0, width, height);
                    gray = new byte[pixels.length];
                    for (int p = 0; p < pixels.length; p++) {
                        int c = pixels[p];
                        gray[p] = (byte) ((77 * ((c >> 16) & 255) + 150 * ((c >> 8) & 255) + 29 * (c & 255)) >> 8);
                    }
                } finally { frame.recycle(); }
                if (previous != null) {
                    shifts[i] = VideoMotionPlan.estimate(previous, gray, width, height);
                    if (BuildConfig.DEBUG && i < 6) {
                        Log.i("XFlipVideoStabilizer", "sample=" + i + ", px=" + width + "x" + height
                                + ", shift=" + shifts[i]);
                    }
                }
                previous = gray;
            }
            VideoMotionPlan plan = VideoMotionPlan.create(times, shifts, width, height);
            Log.i("XFlipVideoStabilizer", "analysis_frames=" + size + ", accepted_pairs=" + plan.acceptedPairs
                    + ", analysis_px=" + width + "x" + height + ", zoom=" + VideoMotionPlan.SCALE);
            if (plan.acceptedPairs * 2 < size - 1) throw new IOException("画面纹理不足或运动过大，保留原片");
            return plan;
        }
    }

    private void export(Uri original, VideoMotionPlan plan, Listener listener) {
        try {
            MatrixTransformation motion = timeUs -> {
                Matrix matrix = new Matrix();
                matrix.setScale(VideoMotionPlan.SCALE, VideoMotionPlan.SCALE);
                matrix.postTranslate(plan.xAt(timeUs), plan.yAt(timeUs));
                return matrix;
            };
            EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(original))
                    .setEffects(new Effects(List.of(), List.of(motion))).build();
            transformer = new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(new Transformer.Listener() {
                        @Override public void onCompleted(Composition composition, ExportResult result) {
                            if (!active.get()) return;
                            executor.execute(() -> publish(listener, plan));
                        }

                        @Override public void onError(Composition composition, ExportResult result, ExportException error) {
                            fail(listener, error);
                        }
                    }).build();
            transformer.start(item, temporary.getAbsolutePath());
            ProgressHolder progress = new ProgressHolder();
            Runnable poll = new Runnable() {
                @Override public void run() {
                    if (!active.get()) return;
                    if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        listener.progress("视频防抖 " + progress.progress + "% ，原片保留");
                    }
                    main.postDelayed(this, 500);
                }
            };
            main.postDelayed(poll, 500);
        } catch (Exception error) { fail(listener, error); }
    }

    private void publish(Listener listener, VideoMotionPlan plan) {
        try {
            try (MediaMetadataRetriever check = new MediaMetadataRetriever()) {
                check.setDataSource(temporary.getAbsolutePath());
                Bitmap frame = check.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160);
                if (frame == null) throw new IOException("防抖输出无法解码");
                frame.recycle();
                Log.i("XFlipVideoStabilizer", "output_bytes=" + temporary.length() + ", duration_ms="
                        + check.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) + ", width="
                        + check.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) + ", height="
                        + check.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) + ", rotation="
                        + check.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) + ", has_audio="
                        + check.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
            }
            if (!active.get()) { cleanup(); return; }
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, "XFlipStabilized_" + System.currentTimeMillis() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/XFlipCamera");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri saved = PendingMediaWrite.execute(new PendingMediaWrite.Storage<Uri>() {
                @Override public Uri insertPending() { return context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values); }
                @Override public void writeAndClose(Uri output) throws IOException {
                    try (FileInputStream source = new FileInputStream(temporary);
                            OutputStream target = context.getContentResolver().openOutputStream(output, "w")) {
                        if (target == null) throw new IOException("视频保存失败");
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = source.read(buffer)) != -1) {
                            if (!active.get()) throw new IOException("已取消");
                            target.write(buffer, 0, count);
                        }
                    }
                }
                @Override public int publish(Uri output) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Video.Media.IS_PENDING, 0);
                    return context.getContentResolver().update(output, done, null, null);
                }
                @Override public void deletePending(Uri output) { context.getContentResolver().delete(output, null, null); }
            });
            cleanup();
            main.post(() -> dispatchPublished(saved, plan.sampleCount(), listener));
        } catch (Exception error) { fail(listener, error); }
    }

    /** Delivers one published result, or safely discards it when cancellation won the race. */
    void dispatchPublished(Uri saved, int sampleCount, Listener listener) {
        if (active.getAndSet(false)) {
            Log.i("XFlipVideoStabilizer", "published=true, samples=" + sampleCount);
            listener.completed(saved);
            return;
        }
        IllegalStateException cleanup = new IllegalStateException(
                "Cancelled stabilized video cleanup failed");
        PendingMediaWrite.cleanupPreserving(cleanup,
                () -> context.getContentResolver().delete(saved, null, null));
        Throwable[] failures = cleanup.getSuppressed();
        if (failures.length == 0) return;
        Log.w("XFlipVideoStabilizer", "cancelled_result_cleanup_failed", cleanup);
        for (Throwable failure : failures) {
            if (failure instanceof Error) throw (Error) failure;
        }
    }

    private void fail(Listener listener, Exception error) {
        Log.w("XFlipVideoStabilizer", "processing_failed", error);
        cleanup();
        main.post(() -> {
            if (active.getAndSet(false)) listener.failed(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        });
    }

    public void cancel() {
        active.set(false);
        if (transformer != null) transformer.cancel();
        // Runs after any in-flight analysis/publishing has released its file.
        executor.execute(this::cleanup);
    }

    private void cleanup() {
        File file = temporary;
        if (file != null && file.exists() && !file.delete()) Log.w("XFlipVideoStabilizer", "temporary_cleanup_failed");
    }
}
