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
import android.content.pm.PackageManager;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

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
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grant) -> {
        if (requestCode == 31) status.setText(grant == PackageManager.PERMISSION_GRANTED
                ? "预览翻转已授权，请拍照或录像" : "预览翻转未授权，可关闭此选项继续拍摄");
    };
    private final ActivityResultLauncher<Intent> camera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Log.i("XFlipSystemCapture", "result_code=" + result.getResultCode());
                stopService(new Intent(this, PreviewMirrorService.class));
                if (result.getResultCode() == RESULT_OK && photo != null) {
                    showPhoto();
                } else {
                    pending = false;
                    discardPendingPhoto();
                    updateControls();
                    status.setText(video ? "已取消录像" : "已取消拍照");
                }
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        FrameLayout root = new FrameLayout(this);
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
        flip.setText("翻转成片");
        flip.setOnClickListener(view -> { if (video) stabilizeVideo(); else flipPhoto(); });
        Button back = new Button(this);
        back.setText("返回");
        back.setOnClickListener(view -> finish());
        controls.addView(shutter, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(record, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(flip, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        mirrorPreview = new CheckBox(this);
        mirrorPreview.setText("系统预览左右纠正（需授权服务）");
        mirrorPreview.setTextColor(Color.WHITE);
        mirrorPreview.setBackgroundColor(0xaa000000);
        mirrorPreview.setChecked(getSharedPreferences("system_capture", MODE_PRIVATE)
                .getBoolean("mirror_preview", true));
        mirrorPreview.setOnCheckedChangeListener((button, checked) -> getSharedPreferences("system_capture", MODE_PRIVATE)
                .edit().putBoolean("mirror_preview", checked).apply());
        FrameLayout.LayoutParams mirrorLayout = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        mirrorLayout.bottomMargin = Math.round(48 * getResources().getDisplayMetrics().density);
        root.addView(mirrorPreview, mirrorLayout);
        setContentView(root);
        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString("photo");
            photo = uri == null ? null : Uri.parse(uri);
            String destination = savedInstanceState.getString("output");
            output = destination == null ? null : Uri.parse(destination);
            pending = savedInstanceState.getBoolean("pending");
            reading = savedInstanceState.getBoolean("reading");
            video = savedInstanceState.getBoolean("video");
            edited = savedInstanceState.getBoolean("edited");
            mirrorPreview.setChecked(savedInstanceState.getBoolean("mirror_preview"));
            if (photo != null && (!pending || reading)) showPhoto();
        }
        updateControls();
    }

    private void capture(boolean movie) {
        if (pending || editing) return;
        if (mirrorPreview.isChecked()) {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), ActivityOptions.makeBasic()
                        .setLaunchDisplayId(getDisplay().getDisplayId()).toBundle());
                status.setText("允许本应用悬浮窗后重试");
                return;
            }
            if (PreviewWorkerReceiver.availableWorker() == null && !Shizuku.pingBinder()) {
                status.setText("请先初始化预览纠正服务，再重试拍摄");
                return;
            }
            if (PreviewWorkerReceiver.availableWorker() == null
                    && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(31);
                return;
            }
        }
        player.stopPlayback();
        player.setVisibility(android.view.View.GONE);
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
            intent.putExtra(ActivityResultContracts.StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE,
                    options.toBundle());
            camera.launch(intent);
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
        reading = true;
        updateControls();
        reader.execute(() -> {
            try {
                android.graphics.Bitmap bitmap = null;
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
                getContentResolver().update(destination, values, null, null);
                android.graphics.Bitmap preview = bitmap;
                runOnUiThread(() -> {
                    if (isDestroyed() || !sourceCache.equals(photo)) return;
                    pending = false;
                    reading = false;
                    updateControls();
                    image.setImageBitmap(preview);
                    image.setVisibility(android.view.View.VISIBLE);
                    status.setText(video ? edited ? "防抖视频已另存，原片保留；点击播放" : "录像已保存，点击画面播放"
                            : edited ? "翻转版本已保存，原图保留" : "系统相机照片已保存");
                    if (video) {
                        image.setOnClickListener(view -> {
                            player.setVideoURI(output);
                            image.setVisibility(android.view.View.GONE);
                            player.setVisibility(android.view.View.VISIBLE);
                            player.start();
                        });
                    } else image.setOnClickListener(null);
                });
            } catch (Exception failure) {
                if (newPhoto) {
                    getContentResolver().delete(destination, null, null);
                    getContentResolver().delete(saved, null, null);
                }
                runOnUiThread(() -> {
                    if (!isDestroyed() && sourceCache.equals(photo)) {
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
            }
        });
    }

    private void flipPhoto() {
        if (output == null || video || pending || editing) return;
        Uri original = output;
        editing = true;
        updateControls();
        status.setText("正在翻转成片，原图保留");
        reader.execute(() -> {
            try {
                Uri edited = PhotoPostProcessor.flip(getContentResolver(), original);
                runOnUiThread(() -> {
                    if (isDestroyed() || !original.equals(output)) return;
                    output = edited;
                    this.edited = true;
                    editing = false;
                    showPhoto();
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || !original.equals(output)) return;
                    editing = false;
                    updateControls();
                    status.setText("翻转失败，原图保留：" + failure.getClass().getSimpleName());
                });
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
        flip.setText(video ? "视频防抖" : "翻转成片");
        flip.setEnabled(!pending && !editing && !reading && output != null && !edited);
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
        state.putBoolean("edited", edited);
        state.putBoolean("mirror_preview", mirrorPreview.isChecked());
    }

    @Override protected void onDestroy() {
        player.stopPlayback();
        if (stabilization != null) stabilization.cancel();
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        if (isFinishing()) stopService(new Intent(this, PreviewMirrorService.class));
        reader.shutdown();
        if (isFinishing() && !pending && photo != null) {
            getContentResolver().delete(photo, null, null);
        }
        super.onDestroy();
    }
}
