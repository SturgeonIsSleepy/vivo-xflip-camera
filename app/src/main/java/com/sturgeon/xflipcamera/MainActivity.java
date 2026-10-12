package com.sturgeon.xflipcamera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MirrorMode;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.exifinterface.media.ExifInterface;

import com.google.common.util.concurrent.ListenableFuture;
import com.sturgeon.xflipcamera.core.CaptureGate;
import com.sturgeon.xflipcamera.core.ExifTransform;
import com.sturgeon.xflipcamera.core.MirrorSettings;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground-only camera activity, with no OEM/private display APIs. */
public final class MainActivity extends ComponentActivity {
    private static final String TAG = "XFlipCamera";
    private static final int CAMERA_REQUEST = 10;
    private final ExecutorService exifReader = Executors.newSingleThreadExecutor();
    private PreviewView previewView;
    private TextView status;
    private Button shutter;
    private Button switchLens;
    private Button settingsButton;
    private SharedPreferences preferences;
    private DisplayManager displayManager;
    private ProcessCameraProvider provider;
    private ImageCapture imageCapture;
    private MirrorSettings mirrors = MirrorSettings.defaults();
    private final CaptureGate captureGate = new CaptureGate();
    private boolean front;
    private boolean foreground;
    private int boundWidth;
    private int boundHeight;
    private int boundRotation = -1;
    private int lastExif;
    private long lastSavedToken;
    private String lastCapture = "none";
    private String lastError = "none";

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override public void onDisplayAdded(int id) { refreshDisplay(); }
                @Override public void onDisplayRemoved(int id) { refreshDisplay(); }
                @Override public void onDisplayChanged(int id) { refreshDisplay(); }
            };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("mirror-preferences", MODE_PRIVATE);
        front = preferences.getBoolean("front-selected", false);
        loadMirrors();
        displayManager = getSystemService(DisplayManager.class);
        createInterface();
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isDestroyed()) return;
            try {
                provider = future.get();
                previewView.post(this::bindCamera);
            } catch (Exception failure) {
                cameraError("相机初始化失败", failure);
            }
        }, getMainExecutor());
        if (!hasCameraPermission()) requestCameraPermission();
    }

    private void createInterface() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        previewView.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) bindCamera();
        });

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setMaxLines(2);
        status.setPadding(dp(8), dp(4), dp(8), dp(4));
        status.setBackgroundColor(0x99000000);
        status.setText("正在准备相机");
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), 0, dp(4), 0);
        controls.setBackgroundColor(0xcc000000);
        switchLens = button("切换", this::changeLens);
        shutter = button("拍照", this::capturePhoto);
        settingsButton = button("设置", this::showSettings);
        controls.addView(switchLens, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(shutter, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(settingsButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(controls, new FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM));
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        setContentView(root);
        root.requestApplyInsets();
        updateControls();
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    @Override protected void onStart() {
        super.onStart();
        foreground = true;
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, new Handler(getMainLooper()));
        }
        if (previewView != null) previewView.post(this::bindCamera);
    }

    @Override protected void onStop() {
        foreground = false;
        captureGate.cancel();
        if (displayManager != null) displayManager.unregisterDisplayListener(displayListener);
        if (provider != null) provider.unbindAll();
        imageCapture = null;
        boundWidth = 0;
        boundHeight = 0;
        updateControls();
        super.onStop();
    }

    @Override protected void onDestroy() {
        exifReader.shutdown();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        refreshDisplay();
    }

    private void refreshDisplay() {
        if (previewView != null) previewView.post(this::bindCamera);
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void bindCamera() {
        // CameraX 1.6's preview mirror builder is explicitly opted into here.
        if (!foreground || isDestroyed() || captureGate.isBusy() || provider == null) return;
        if (!hasCameraPermission()) {
            status.setText("需要相机权限，点击拍照授权");
            updateControls();
            return;
        }
        if (previewView.getWidth() == 0 || previewView.getHeight() == 0
                || previewView.getDisplay() == null) return;
        int rotation = previewView.getDisplay().getRotation();
        if (imageCapture != null && boundWidth == previewView.getWidth()
                && boundHeight == previewView.getHeight() && boundRotation == rotation) return;
        try {
            CameraSelector selector = front
                    ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
            if (!provider.hasCamera(selector)) {
                front = !front;
                loadMirrors();
                preferences.edit().putBoolean("front-selected", front).apply();
                selector = front ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;
                if (!provider.hasCamera(selector)) {
                    throw new IllegalStateException("No available front or back camera");
                }
            }
            ViewPort viewPort = previewView.getViewPort();
            if (viewPort == null) return;
            Preview preview = new Preview.Builder()
                    .setTargetRotation(rotation)
                    .setMirrorMode(mirrors.previewMirrored
                            ? MirrorMode.MIRROR_MODE_ON : MirrorMode.MIRROR_MODE_OFF)
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            ImageCapture capture = new ImageCapture.Builder()
                    .setTargetRotation(rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();
            UseCaseGroup group = new UseCaseGroup.Builder()
                    .setViewPort(viewPort).addUseCase(preview).addUseCase(capture).build();
            provider.unbindAll();
            imageCapture = null;
            provider.bindToLifecycle(this, selector, group);
            imageCapture = capture;
            boundWidth = previewView.getWidth();
            boundHeight = previewView.getHeight();
            boundRotation = rotation;
            status.setText((front ? "前摄" : "后摄")
                    + (mirrors.previewMirrored ? " · 预览镜像" : " · 预览不镜像"));
            Log.i(TAG, diagnostics());
        } catch (Exception failure) {
            imageCapture = null;
            cameraError("相机启动失败，请查看诊断", failure);
        }
        updateControls();
    }

    private void changeLens() {
        if (provider == null || captureGate.isBusy()) return;
        try {
            CameraSelector candidate = front
                    ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
            if (!provider.hasCamera(candidate)) {
                Toast.makeText(this, "另一摄像头不可用", Toast.LENGTH_SHORT).show();
                return;
            }
            front = !front;
            preferences.edit().putBoolean("front-selected", front).apply();
            loadMirrors();
            boundRotation = -1;
            bindCamera();
        } catch (Exception failure) {
            cameraError("摄像头切换失败", failure);
        }
    }

    private void showSettings() {
        if (captureGate.isBusy()) return;
        boolean[] selected = {mirrors.previewMirrored, mirrors.photoMirrored};
        new AlertDialog.Builder(this)
                .setTitle(front ? "前摄镜像设置" : "后摄镜像设置")
                .setMultiChoiceItems(new String[]{"预览镜像", "照片镜像"}, selected,
                        (dialog, index, checked) -> {
                            mirrors = index == 0
                                    ? mirrors.withPreview(checked) : mirrors.withPhoto(checked);
                            String lens = front ? "front." : "back.";
                            preferences.edit()
                                    .putBoolean(lens + "preview", mirrors.previewMirrored)
                                    .putBoolean(lens + "photo", mirrors.photoMirrored).apply();
                            if (index == 0) {
                                boundRotation = -1;
                                bindCamera();
                            }
                        })
                .setNeutralButton("诊断", (dialog, which) -> showDiagnostics())
                .setPositiveButton("完成", null)
                .show();
    }

    private void loadMirrors() {
        String lens = front ? "front." : "back.";
        mirrors = new MirrorSettings(preferences.getBoolean(lens + "preview", false),
                preferences.getBoolean(lens + "photo", false));
    }

    private void capturePhoto() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }
        if (!foreground || imageCapture == null || captureGate.isBusy()) return;
        final MirrorSettings snapshot = mirrors;
        final String lens = front ? "front" : "back";
        Display display = previewView.getDisplay();
        if (display == null) return;
        imageCapture.setTargetRotation(display.getRotation());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "XFlip_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XFlipCamera");
        ImageCapture.Metadata metadata = new ImageCapture.Metadata();
        // Always set this explicitly, including false; preview preferences never leak here.
        metadata.setReversedHorizontal(snapshot.photoMirrored);
        metadata.setReversedVertical(false);
        ImageCapture.OutputFileOptions output = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                .setMetadata(metadata).build();
        final long captureToken = captureGate.tryStart();
        if (captureToken == 0) return;
        status.setText("正在保存");
        updateControls();
        try {
            imageCapture.takePicture(output, getMainExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(ImageCapture.OutputFileResults result) {
                        if (!finishCapture(captureToken)) return;
                        lastSavedToken = captureToken;
                        lastExif = 0;
                        lastCapture = lens + ", requested_mirror=" + snapshot.photoMirrored;
                        status.setText("照片已保存");
                        readSavedExif(result.getSavedUri(), captureToken);
                    }
                    @Override public void onError(ImageCaptureException failure) {
                        if (finishCapture(captureToken)) cameraError("拍照失败", failure);
                    }
                });
        } catch (RuntimeException failure) {
            if (finishCapture(captureToken)) cameraError("拍照启动失败", failure);
        }
    }

    private boolean finishCapture(long token) {
        if (!captureGate.finish(token) || isDestroyed()) return false;
        bindCamera();
        updateControls();
        return true;
    }

    private void readSavedExif(Uri uri, long savedToken) {
        if (uri == null || exifReader.isShutdown()) return;
        exifReader.execute(() -> {
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                int orientation = stream == null ? 0 : new ExifInterface(stream)
                        .getAttributeInt(ExifInterface.TAG_ORIENTATION, 0);
                runOnUiThread(() -> {
                    if (isDestroyed() || lastSavedToken != savedToken) return;
                    lastExif = orientation;
                    Log.i(TAG, "saved EXIF=" + orientation + ", " + lastCapture);
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isDestroyed() || lastSavedToken != savedToken) return;
                    lastExif = 0;
                    lastError = "EXIF inspection: " + failure.getClass().getSimpleName();
                    Log.w(TAG, lastError, failure);
                });
            }
        });
    }

    private String diagnostics() {
        Rect window = getWindowManager().getCurrentWindowMetrics().getBounds();
        Display display = getDisplay();
        StringBuilder text = new StringBuilder()
                .append("model=").append(Build.MODEL)
                .append("\nsdk=").append(Build.VERSION.SDK_INT)
                .append("\nwindow_px=").append(window.width()).append('x').append(window.height())
                .append("\npreview_px=").append(previewView.getWidth())
                .append('x').append(previewView.getHeight())
                .append("\ndisplay_id=").append(display == null ? -1 : display.getDisplayId())
                .append("\ndisplay_rotation=").append(display == null
                        ? Surface.ROTATION_0 : display.getRotation())
                .append("\nselected_lens=").append(front ? "front" : "back")
                .append("\npreview_mirror=").append(mirrors.previewMirrored)
                .append("\nphoto_mirror=").append(mirrors.photoMirrored)
                .append("\nlast_capture=").append(lastCapture)
                .append("\nlast_exif_orientation=").append(lastExif)
                .append("\nlast_exif_reflection=").append(lastExif >= 1 && lastExif <= 8
                        ? String.valueOf(ExifTransform.isMirrored(lastExif)) : "unknown")
                .append("\nlast_error=").append(lastError);
        if (hasCameraPermission()) {
            try {
                CameraManager manager = getSystemService(CameraManager.class);
                if (manager != null) {
                    for (String id : manager.getCameraIdList()) {
                        CameraCharacteristics info = manager.getCameraCharacteristics(id);
                        text.append("\ncamera ").append(id)
                                .append(": facing=").append(info.get(CameraCharacteristics.LENS_FACING))
                                .append(", sensor_rotation=")
                                .append(info.get(CameraCharacteristics.SENSOR_ORIENTATION));
                    }
                }
            } catch (Exception failure) {
                text.append("\ncamera_query_error=").append(failure.getClass().getSimpleName());
            }
        }
        return text.toString();
    }

    private void showDiagnostics() {
        String report = diagnostics();
        new AlertDialog.Builder(this).setTitle("相机诊断").setMessage(report)
                .setPositiveButton("复制", (dialog, which) -> {
                    ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("X Flip 相机诊断", report));
                    }
                }).setNegativeButton("关闭", null).show();
    }

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST) {
            if (hasCameraPermission()) {
                previewView.post(this::bindCamera);
            } else {
                status.setText("未获相机权限，点击拍照重试");
                updateControls();
            }
        }
    }

    private void updateControls() {
        if (shutter == null) return;
        shutter.setEnabled(foreground && !captureGate.isBusy()
                && (imageCapture != null || !hasCameraPermission()));
        switchLens.setEnabled(foreground && provider != null && !captureGate.isBusy());
        settingsButton.setEnabled(!captureGate.isBusy());
    }

    private void cameraError(String message, Exception failure) {
        lastError = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        status.setText(message);
        Log.e(TAG, message, failure);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
