package com.sturgeon.xflipcamera;

import android.app.ActivityOptions;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

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
    private TextView status;
    private ImageView image;
    private Button shutter;
    private final ActivityResultLauncher<Intent> camera = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Log.i("XFlipSystemCapture", "result_code=" + result.getResultCode());
                if (result.getResultCode() == RESULT_OK && photo != null) {
                    showPhoto();
                } else {
                    pending = false;
                    shutter.setEnabled(true);
                    discardPendingPhoto();
                    status.setText("已取消拍照");
                }
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(-1, -1));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(0xaa000000);
        status.setText("点击拍照，使用 vivo 系统相机");
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        shutter = new Button(this);
        shutter.setText("拍照");
        shutter.setOnClickListener(view -> capture());
        Button back = new Button(this);
        back.setText("返回");
        back.setOnClickListener(view -> finish());
        controls.addView(shutter, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(controls, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        if (savedInstanceState != null) {
            String uri = savedInstanceState.getString("photo");
            photo = uri == null ? null : Uri.parse(uri);
            String destination = savedInstanceState.getString("output");
            output = destination == null ? null : Uri.parse(destination);
            pending = savedInstanceState.getBoolean("pending");
            reading = savedInstanceState.getBoolean("reading");
            shutter.setEnabled(!pending);
            if (photo != null && (!pending || reading)) showPhoto();
        }
    }

    private void capture() {
        if (pending) return;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,
                "XFlipSystem_" + System.currentTimeMillis() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XFlipCamera");
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
            output = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (output == null) throw new IllegalStateException("Could not create photo");
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    .setPackage("com.android.camera")
                    .putExtra(MediaStore.EXTRA_OUTPUT, photo)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("photo", photo));
            pending = true;
            shutter.setEnabled(false);
            ActivityOptions options = ActivityOptions.makeBasic().setLaunchDisplayId(getDisplay().getDisplayId());
            intent.putExtra(ActivityResultContracts.StartActivityForResult.EXTRA_ACTIVITY_OPTIONS_BUNDLE,
                    options.toBundle());
            camera.launch(intent);
        } catch (RuntimeException failure) {
            pending = false;
            shutter.setEnabled(true);
            discardPendingPhoto();
            status.setText("系统相机启动失败：" + failure.getClass().getSimpleName());
        }
    }

    private void showPhoto() {
        Uri saved = photo;
        Uri destination = output;
        boolean newPhoto = pending;
        reading = true;
        reader.execute(() -> {
            try {
                android.graphics.Bitmap bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(), saved),
                        (decoder, info, source) -> decoder.setTargetSampleSize(4));
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
                runOnUiThread(() -> {
                    if (isDestroyed() || !saved.equals(photo)) return;
                    pending = false;
                    reading = false;
                    shutter.setEnabled(true);
                    image.setImageBitmap(bitmap);
                    status.setText("系统相机照片已保存");
                });
            } catch (Exception failure) {
                if (newPhoto) {
                    getContentResolver().delete(destination, null, null);
                    getContentResolver().delete(saved, null, null);
                }
                runOnUiThread(() -> {
                    if (!isDestroyed() && saved.equals(photo)) {
                        if (newPhoto) photo = null;
                        pending = false;
                        reading = false;
                        shutter.setEnabled(true);
                        status.setText("照片读取失败：" + failure.getClass().getSimpleName());
                    }
                });
            }
        });
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
    }

    @Override protected void onDestroy() {
        reader.shutdown();
        if (isFinishing() && !pending && photo != null) {
            getContentResolver().delete(photo, null, null);
        }
        super.onDestroy();
    }
}
