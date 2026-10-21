package com.sturgeon.xflipcamera;

import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;
import android.widget.MediaController;
import android.widget.CheckBox;

import androidx.activity.ComponentActivity;
import androidx.core.content.FileProvider;

import com.sturgeon.xflipcamera.core.MediaPreviewRestore;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The OEM app owns camera access; this activity owns the returned photo. */
public final class SystemCaptureActivity extends ComponentActivity {
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private Uri photo;
    private Uri output;
    private boolean pending;
    private boolean reading;
    private boolean video;
    private boolean editing;
    private boolean edited;
    private TextView status;
    private ImageView image;
    private Button shutter;
    private Button record;
    private Button flip;
    private VideoView player;
    private CheckBox mirrorPreview;
    private VideoStabilizer stabilization;
    private static final int CAMERA_REQUEST = 4102;
    private Uri originalPhoto;
    private FrameLayout root;
    private final PreviewBitmapOwner previewBitmaps = new PreviewBitmapOwner();
    private PhotoEditor editor;
    private Uri editorSource;
    private static SystemCaptureActivity active;
    private Boolean requestedMode;
    private boolean autoOpen;
    private boolean foreground;
    private Boolean waitingMode;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable workerTimeout = () -> {
        if (waitingMode != null && PreviewWorkerReceiver.availableWorker() == null) {
            waitingMode = null;
            autoOpen = false;
            status.setText("预览服务未连接，请初始化桥接后重试");
        }
    };
    private Uri lastSaved;
    private boolean lastSavedVideo;
    private final java.util.List<Uri> burstFrames = new java.util.ArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean processingCancelled = new java.util.concurrent.atomic.AtomicBoolean();

    static int burstIndex() {
        SystemCaptureActivity owner = active;
        return owner != null && !owner.video && owner.getSharedPreferences("system_capture", MODE_PRIVATE)
                .getBoolean("burst_enabled", false) ? owner.burstFrames.size() + 1 : 0;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAMERA_REQUEST) return;
        Log.i("XFlipSystemCapture", "result_code=" + result);
        PreviewMirrorService.stopForResult(this, () -> {
            if (isDestroyed()) return;
            if (requestedMode != null) {
                boolean next = requestedMode;
                requestedMode = null;
                pending = false;
                discardPendingPhoto();
                capture(next);
            } else if (result == RESULT_OK && photo != null) showPhoto();
            else {
                burstFrames.clear();
                pending = false;
                discardPendingPhoto();
                if (BuildConfig.BASIC_CAPTURE) {
                    finish();
                    return;
                }
                if (autoOpen) {
                    workerReady();
                    return;
                }
                if (lastSaved != null) {
                    output = lastSaved;
                    video = lastSavedVideo;
                    showPhoto();
                } else {
                    updateControls();
                    status.setText("已退出取景，可重新拍照或录像");
                }
            }
        });
    }

    static void requestMode(boolean movie) {
        if (BuildConfig.BASIC_CAPTURE && movie) return;
        SystemCaptureActivity owner = active;
        if (owner == null || owner.isDestroyed() || owner.isFinishing() || owner.reading || owner.editing) return;
        if (owner.pending) {
            if (owner.video == movie) return;
            owner.requestedMode = movie;
            owner.finishActivity(CAMERA_REQUEST);
        } else owner.capture(movie);
    }

    static void workerReady() {
        SystemCaptureActivity owner = active;
        if (owner == null || owner.isFinishing() || !owner.foreground || owner.pending || owner.reading
                || owner.editing || owner.editor != null || !owner.autoOpen && owner.waitingMode == null) return;
        boolean movie = owner.waitingMode != null && owner.waitingMode;
        if (PreviewWorkerReceiver.availableWorker() == null) {
            owner.waitForWorker(movie);
            return;
        }
        owner.autoOpen = false;
        owner.waitingMode = null;
        owner.main.removeCallbacks(owner.workerTimeout);
        owner.capture(movie);
    }

    private void waitForWorker(boolean movie) {
        waitingMode = movie;
        status.setText("正在恢复预览服务，请稍候");
        main.removeCallbacks(workerTimeout);
        main.postDelayed(workerTimeout, 6000);
        PreviewWorkerReceiver.requestWorker(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (editor != null) { autoOpen = false; return; }
        autoOpen = !intent.getBooleanExtra("manual_entry", false);
        workerReady();
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
        workerReady();
    }

    @Override protected void onStop() {
        foreground = false;
        main.removeCallbacks(workerTimeout);
        super.onStop();
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        active = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        player = new VideoView(this);
        player.setVisibility(android.view.View.GONE);
        root.addView(player, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        player.setOnCompletionListener(media -> {
            player.setVisibility(android.view.View.GONE);
            image.setVisibility(android.view.View.VISIBLE);
        });
        MediaController mediaControls = new MediaController(this);
        mediaControls.setAnchorView(player);
        player.setMediaController(mediaControls);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xaa000000);
        status.setText("使用 vivo 系统相机拍照或录像");
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        shutter = new Button(this);
        shutter.setText("拍照");
        shutter.setOnClickListener(view -> capture(false));
        record = new Button(this);
        record.setText("录像");
        record.setOnClickListener(view -> capture(true));
        flip = new Button(this);
        flip.setText("直方图/算法");
        flip.setOnClickListener(view -> {
            if (video) stabilizeVideo();
            else if (output != null && !pending && !reading && !editing) {
                openEditor(originalPhoto == null ? output : originalPhoto, null);
            }
        });
        Button back = new Button(this);
        back.setText("返回");
        back.setOnClickListener(view -> finish());
        controls.addView(shutter, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(record, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(flip, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        if (BuildConfig.BASIC_CAPTURE) {
            record.setVisibility(android.view.View.GONE);
            flip.setVisibility(android.view.View.GONE);
        }
        root.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        mirrorPreview = new CheckBox(this);
        mirrorPreview.setText("实时左右纠正");
        mirrorPreview.setTextColor(Color.WHITE);
        mirrorPreview.setBackgroundColor(0xaa000000);
        mirrorPreview.setChecked(BuildConfig.BASIC_CAPTURE || getSharedPreferences("system_capture", MODE_PRIVATE)
                .getBoolean("mirror_preview", true));
        mirrorPreview.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("system_capture", MODE_PRIVATE)
                .edit().putBoolean("mirror_preview", checked).apply());
        FrameLayout.LayoutParams mirrorLayout = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        mirrorLayout.bottomMargin = Math.round(48 * getResources().getDisplayMetrics().density);
        LinearLayout previewOptions = new LinearLayout(this);
        previewOptions.setBackgroundColor(0xaa000000);
        previewOptions.addView(mirrorPreview);
        CheckBox liveHistogram = new CheckBox(this);
        liveHistogram.setText("取景直方图"); liveHistogram.setTextColor(Color.WHITE);
        liveHistogram.setChecked(getSharedPreferences("system_capture", MODE_PRIVATE).getBoolean("live_histogram", true));
        liveHistogram.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("system_capture", MODE_PRIVATE)
                .edit().putBoolean("live_histogram", checked).apply());
        previewOptions.addView(liveHistogram);
        root.addView(previewOptions, mirrorLayout);
        if (BuildConfig.BASIC_CAPTURE) previewOptions.setVisibility(android.view.View.GONE);
        setContentView(root);
        if (savedInstanceState != null) {
            java.util.ArrayList<String> frames = savedInstanceState.getStringArrayList("burst_frames");
            if (frames != null) for (String uri : frames) burstFrames.add(Uri.parse(uri));
            String uri = savedInstanceState.getString("photo");
            photo = uri == null ? null : Uri.parse(uri);
            String destination = savedInstanceState.getString("output");
            output = destination == null ? null : Uri.parse(destination);
            pending = savedInstanceState.getBoolean("pending");
            reading = savedInstanceState.getBoolean("reading");
            video = savedInstanceState.getBoolean("video");
            String original = savedInstanceState.getString("original_photo");
            originalPhoto = original == null ? null : Uri.parse(original);
            edited = savedInstanceState.getBoolean("edited");
            mirrorPreview.setChecked(BuildConfig.BASIC_CAPTURE || savedInstanceState.getBoolean("mirror_preview"));
            Uri restoredPreview = MediaPreviewRestore.source(pending, reading, photo, output);
            if (restoredPreview != null) showPhoto();
            else if (reading) {
                reading = false;
                if (pending) {
                    pending = false;
                    discardPendingPhoto();
                }
                status.setText("媒体预览状态已失效，可重新拍摄");
            }
        }
        updateControls();
        String latest = getSharedPreferences("system_capture", MODE_PRIVATE).getString("last_saved", null);
        lastSaved = latest == null ? null : Uri.parse(latest);
        lastSavedVideo = !BuildConfig.BASIC_CAPTURE && getSharedPreferences("system_capture", MODE_PRIVATE).getBoolean("last_video", false);
        if (originalPhoto == null && !lastSavedVideo) {
            String original = getSharedPreferences("system_capture", MODE_PRIVATE).getString("last_original", null);
            originalPhoto = original == null ? null : Uri.parse(original);
        }
        if (BuildConfig.BASIC_CAPTURE && getSharedPreferences("system_capture", MODE_PRIVATE).getBoolean("last_video", false)) lastSaved = null;
        autoOpen = !pending && !reading && (savedInstanceState == null || photo == null && output == null)
                && !getIntent().getBooleanExtra("manual_entry", false);
        if (autoOpen) new android.os.Handler(getMainLooper()).post(SystemCaptureActivity::workerReady);
        if (!BuildConfig.BASIC_CAPTURE && savedInstanceState != null && savedInstanceState.getString("editor_source") != null) {
            autoOpen = false;
            openEditor(Uri.parse(savedInstanceState.getString("editor_source")), savedInstanceState.getBundle("editor"));
        }
    }

    private void openEditor(Uri source, Bundle state) {
        if (BuildConfig.BASIC_CAPTURE || editor != null) return;
        editorSource = source; autoOpen = false;
        editor = new PhotoEditor(this, source, state, saved -> {
            editor = null; editorSource = null;
            if (isDestroyed()) return;
            setContentView(root);
            if (saved != null) { output = saved; edited = true; showPhoto(); }
        });
    }

    private void capture(boolean movie) {
        if (BuildConfig.BASIC_CAPTURE && movie) return;
        if (pending || editing || reading || editor != null) return;
        if (mirrorPreview.isChecked()) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), ActivityOptions.makeBasic()
                        .setLaunchDisplayId(getDisplay().getDisplayId()).toBundle());
                status.setText("允许本应用悬浮窗后重试");
                return;
            }
            if (PreviewWorkerReceiver.availableWorker() == null) {
                waitForWorker(movie);
                return;
            }
        }
        player.stopPlayback();
        player.setVisibility(android.view.View.GONE);
        if (movie || !getSharedPreferences("system_capture", MODE_PRIVATE).getBoolean("burst_enabled", false)) burstFrames.clear();
        video = movie;
        edited = false;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                (video ? "XFlipVideo_" : "XFlipSystem_") + System.currentTimeMillis()
                        + (video ? ".mp4" : ".jpg"));
        values.put(MediaStore.Images.Media.MIME_TYPE, video ? "video/mp4" : "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, video ? "Movies/XFlipCamera" : "Pictures/XFlipCamera");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        if (photo != null) getContentResolver().delete(photo, null, null);
        photo = null;
        output = null;
        try {
            File directory = new File(getCacheDir(), "system-camera");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IllegalStateException("Could not create photo directory");
            }
            photo = FileProvider.getUriForFile(this, getPackageName() + ".photos",
                    new File(directory, values.getAsString(MediaStore.Images.Media.DISPLAY_NAME)));
            output = getContentResolver().insert(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (output == null) throw new IllegalStateException("Could not create photo");
            Intent intent = new Intent(video ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE)
                    .setPackage("com.android.camera")
                    .putExtra(MediaStore.EXTRA_OUTPUT, photo)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("photo", photo));
            if (video) {
                intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                int limit = getIntent().getIntExtra("video_duration_limit", 0);
                if (limit > 0) intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, limit);
            }
            pending = true;
            updateControls();
            if (mirrorPreview.isChecked()) startForegroundService(
                    new Intent(this, PreviewMirrorService.class)
                            .putExtra("display_id", getDisplay().getDisplayId()).putExtra("video", video));
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(getDisplay().getDisplayId());
            startActivityForResult(intent, CAMERA_REQUEST, options.toBundle());
        } catch (RuntimeException failure) {
            stopService(new Intent(this, PreviewMirrorService.class));
            pending = false;
            discardPendingPhoto();
            updateControls();
            status.setText("系统相机启动失败：" + failure.getClass().getSimpleName());
        }
    }

    private void showPhoto() {
        Uri saved = pending ? photo : output;
        Uri sourceCache = photo;
        Uri destination = output;
        boolean newPhoto = pending;
        boolean collectBurst = !BuildConfig.BASIC_CAPTURE && newPhoto && !video && getSharedPreferences("system_capture", MODE_PRIVATE).getBoolean("burst_enabled", false);
        if (newPhoto && !collectBurst) burstFrames.clear();
        java.util.List<Uri> burstInputs = new java.util.ArrayList<>(burstFrames);
        if (collectBurst) burstInputs.add(destination);
        android.content.SharedPreferences photoPreferences = getSharedPreferences("system_capture",MODE_PRIVATE);
        PhotoEnhancer.Options photoOptions = BuildConfig.BASIC_CAPTURE ? PhotoEnhancer.Options.NONE : new PhotoEnhancer.Options(photoPreferences.getBoolean("auto_levels",false),
                photoPreferences.getBoolean("detail_enhance",false),photoPreferences.getBoolean("hdr_tone",false));
        reading = true;
        if (collectBurst && burstInputs.size() == 3) status.setText("正在合成三张照片，原图保留");
        updateControls();
        reader.execute(() -> {
            boolean originalPublished = false;
            android.graphics.Bitmap bitmap = null;
            try {
                if (video) {
                    try (MediaMetadataRetriever metadata = new MediaMetadataRetriever()) {
                        metadata.setDataSource(this, saved);
                        String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (duration == null || Long.parseLong(duration) <= 0) {
                            throw new IllegalStateException("Video has no duration");
                        }
                        bitmap = metadata.getScaledFrameAtTime(0,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512);
                        if (bitmap == null) throw new IllegalStateException("Video has no decodable frame");
                    }
                } else {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), saved),
                            (decoder, info, source) -> decoder.setTargetSampleSize(4));
                }
                if (newPhoto) {
                    try (InputStream source = getContentResolver().openInputStream(saved);
                            OutputStream target = getContentResolver().openOutputStream(destination, "w")) {
                        if (source == null || target == null) throw new IllegalStateException("Photo stream unavailable");
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = source.read(buffer)) != -1) target.write(buffer, 0, count);
                    }
                }
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                int publishedRows = getContentResolver().update(destination, values, null, null);
                if (newPhoto && publishedRows != 1) throw new IllegalStateException("Original photo publish failed");
                originalPublished = true;
                Uri displayed = destination;
                String fusionMessage = null;
                if (collectBurst && burstInputs.size() == 3) {
                    try {
                        PhotoBurstProcessor.Result fusion = PhotoBurstProcessor.merge(getContentResolver(), burstInputs,
                                processingCancelled::get, message -> runOnUiThread(() -> {
                                    if (!isDestroyed() && destination.equals(output)) status.setText(message);
                                }),photoOptions);
                        android.graphics.Bitmap fusedPreview = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), fusion.uri()),
                                (decoder, info, source) -> decoder.setTargetSampleSize(4));
                        bitmap.recycle(); bitmap = fusedPreview; displayed = fusion.uri();
                        fusionMessage = "多帧降噪增强已保存（" + fusion.acceptedFrames() + "/3 有效帧），三张原图保留";
                    } catch (Exception failure) {
                        fusionMessage = "合成未完成，原图保留：" + failure.getMessage();
                        Log.w("XFlipPhotoBurst", "fusion_failed_originals_preserved", failure);
                    }
                }
                if (newPhoto && !video && !collectBurst && photoOptions.enabled()) {
                    try {
                        PhotoEnhancer.Result enhancement=PhotoEnhancer.enhance(getContentResolver(),destination,photoOptions,
                                processingCancelled::get,message->runOnUiThread(()->{
                                    if(!isDestroyed() && destination.equals(output))status.setText(message);
                                }));
                        android.graphics.Bitmap enhancedPreview=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),enhancement.uri()),
                                (decoder,info,source)->decoder.setTargetSampleSize(4));
                        bitmap.recycle();bitmap=enhancedPreview;displayed=enhancement.uri();
                        fusionMessage="照片处理副本已保存，原图保留";
                    } catch(Exception failure) {
                        fusionMessage="照片增强未完成，原图保留";
                        Log.w("XFlipPhotoEnhancer","enhance_failed_original_preserved",failure);
                    }
                }
                boolean marked = false;
                boolean markFailed = false;
                if (!BuildConfig.BASIC_CAPTURE && newPhoto && !video && (!collectBurst || burstInputs.size() == 3)
                        && getSharedPreferences("system_capture", MODE_PRIVATE).getBoolean("watermark_enabled", false)) {
                    try {
                        String caption = "vivo X Flip  " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                                java.util.Locale.getDefault()).format(new java.util.Date());
                        Uri markedUri = WatermarkProcessor.mark(getContentResolver(), displayed, caption);
                        android.graphics.Bitmap markedPreview = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), markedUri),
                                (decoder, info, source) -> decoder.setTargetSampleSize(4));
                        bitmap.recycle();
                        bitmap = markedPreview;
                        displayed = markedUri;
                        marked = true;
                    } catch (Exception failure) {
                        markFailed = true;
                        Log.w("XFlipSystemCapture", "watermark_failed_original_preserved", failure);
                    }
                }
                android.graphics.Bitmap preview = bitmap;
                bitmap = null; // Ownership transfers to the main-thread callback.
                Uri savedOutput = displayed;
                boolean watermarked = marked;
                boolean watermarkFailed = markFailed;
                String burstStatus = fusionMessage;
                runOnUiThread(() -> {
                    if (isDestroyed() || !java.util.Objects.equals(sourceCache, photo) || !destination.equals(output)) {
                        previewBitmaps.discard(preview);
                        return;
                    }
                    output = savedOutput;
                    if (newPhoto) originalPhoto = video ? null : destination;
                    lastSaved = output;
                    lastSavedVideo = video;
                    getSharedPreferences("system_capture", MODE_PRIVATE).edit()
                            .putString("last_saved", output.toString()).putBoolean("last_video", video)
                            .putString("last_original", originalPhoto == null ? null : originalPhoto.toString()).apply();
                    pending = false;
                    reading = false;
                    updateControls();
                    previewBitmaps.show(image,preview);
                    image.setVisibility(android.view.View.VISIBLE);
                    if (collectBurst && burstInputs.size() < 3) {
                        burstFrames.clear(); burstFrames.addAll(burstInputs);
                        status.setText("已保存 " + burstInputs.size() + "/3 张，请继续拍摄并确认");
                        main.post(() -> capture(false));
                        return;
                    }
                    if (collectBurst) burstFrames.clear();
                    status.setText(burstStatus != null ? burstStatus + (watermarked ? "（含文字水印）" : "") : video ? edited ? "防抖视频已另存，原片保留；点击播放" : "录像已保存，点击画面播放"
                            : watermarked ? "水印副本已保存，原图保留" : watermarkFailed ? "水印未完成，原图已保留"
                            : edited ? "调整副本已保存，原图保留" : "系统相机照片已保存");
                    if (video) {
                        image.setOnClickListener(view -> {
                            if (pending || editing || reading) return;
                            player.setVideoURI(output);
                            image.setVisibility(android.view.View.GONE);
                            player.setVisibility(android.view.View.VISIBLE);
                            player.start();
                        });
                    } else image.setOnClickListener(null);
                    if (newPhoto && video && getSharedPreferences("system_capture", MODE_PRIVATE)
                            .getBoolean("auto_stabilize", false)) stabilizeVideo();
                });
            } catch (Exception failure) {
                if (newPhoto && !originalPublished) {
                    getContentResolver().delete(destination, null, null);
                    getContentResolver().delete(saved, null, null);
                }
                runOnUiThread(() -> {
                    if (!isDestroyed() && java.util.Objects.equals(sourceCache, photo) && destination.equals(output)) {
                        if (newPhoto) {
                            photo = null;
                            output = null;
                        }
                        pending = false;
                        reading = false;
                        updateControls();
                        status.setText("媒体读取失败：" + failure.getClass().getSimpleName());
                    }
                });
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            }
        });
    }

    private void stabilizeVideo() {
        if (output == null || !video || pending || editing || reading) return;
        Uri original = output;
        player.stopPlayback();
        player.setVisibility(android.view.View.GONE);
        editing = true;
        updateControls();
        status.setText("正在分析视频运动，原片保留");
        stabilization = new VideoStabilizer(this, reader);
        stabilization.start(original, new VideoStabilizer.Listener() {
            @Override public void progress(String message) {
                if (!isDestroyed() && original.equals(output)) status.setText(message);
            }
            @Override public void completed(Uri result) {
                if (isDestroyed() || !original.equals(output)) return;
                output = result;
                edited = true;
                editing = false;
                showPhoto();
            }
            @Override public void failed(String reason) {
                if (isDestroyed() || !original.equals(output)) return;
                editing = false;
                updateControls();
                status.setText("防抖未完成：" + reason);
            }
        });
    }

    private void updateControls() {
        if (shutter == null) return;
        shutter.setEnabled(!pending && !editing && !reading);
        record.setEnabled(!pending && !editing && !reading);
        flip.setText(video ? "视频防抖" : "直方图/算法");
        flip.setEnabled(!pending && !editing && !reading && output != null && (!video || !edited));
        mirrorPreview.setEnabled(!pending && !editing && !reading);
    }

    private void discardPendingPhoto() {
        if (photo != null) getContentResolver().delete(photo, null, null);
        if (output != null) getContentResolver().delete(output, null, null);
        photo = null;
        output = null;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("photo", photo == null ? null : photo.toString());
        state.putString("output", output == null ? null : output.toString());
        state.putBoolean("pending", pending);
        state.putBoolean("reading", reading);
        state.putBoolean("video", video);
        state.putString("original_photo", originalPhoto == null ? null : originalPhoto.toString());
        if (editor != null) {
            state.putString("editor_source", editorSource.toString()); state.putBundle("editor", editor.saveState());
        }
        state.putBoolean("edited", edited);
        state.putBoolean("mirror_preview", mirrorPreview.isChecked());
        java.util.ArrayList<String> frames = new java.util.ArrayList<>();
        for (Uri uri : burstFrames) frames.add(uri.toString());
        state.putStringArrayList("burst_frames", frames);
    }

    @Override protected void onDestroy() {
        if (active == this) active = null;
        if (editor != null) editor.close();
        processingCancelled.set(true);
        main.removeCallbacks(workerTimeout);
        player.stopPlayback();
        previewBitmaps.close(image);
        if (stabilization != null) stabilization.cancel();
        if (isFinishing()) stopService(new Intent(this, PreviewMirrorService.class));
        reader.shutdown();
        if (isFinishing() && !pending && photo != null) {
            getContentResolver().delete(photo, null, null);
        }
        super.onDestroy();
    }
}
