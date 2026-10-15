package com.sturgeon.xflipcamera;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import rikka.shizuku.Shizuku;

/** The overlay covers only the OEM preview; native shutter and confirmation stay outside it. */
public final class PreviewMirrorService extends Service implements SurfaceHolder.Callback {
    private static PreviewMirrorService active;
    private WindowManager windows;
    private SurfaceView preview;
    private IBinder worker;
    private Shizuku.UserServiceArgs args;
    private boolean movie;
    private int displayId;
    private boolean rendererStarted;
    private CoverControls controls;
    private Context screenContext;
    private android.app.AlertDialog settingsDialog;
    private Runnable afterStopped;
    private boolean destroying;
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable controlsPoll = new Runnable() {
        @Override public void run() {
            if (destroying) return;
            try {
                if (worker == null || !worker.pingBinder()) {
                    rendererStarted = false;
                    PreviewWorkerReceiver.requestWorker(PreviewMirrorService.this);
                }
                boolean live = rendererStarted && readWorkerStatus().contains("phase=1,");
                if (controls != null && (settingsDialog == null || !settingsDialog.isShowing())) {
                    controls.setPhotoProgress(movie ? 0 : SystemCaptureActivity.burstIndex());
                    if (live) controls.attach(); else controls.detach();
                }
            } catch (RuntimeException error) {
                android.util.Log.w("XFlipPreviewMirror", "control update failed", error);
            }
            main.postDelayed(this, 200);
        }
    };

    static void stopForResult(Context context, Runnable continuation) {
        if (active != null) {
            active.afterStopped = continuation;
            context.stopService(new Intent(context, PreviewMirrorService.class));
        } else {
            context.stopService(new Intent(context, PreviewMirrorService.class));
            continuation.run();
        }
    }
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            worker = service;
            startRendering();
        }
        @Override public void onServiceDisconnected(ComponentName name) { worker = null; stopSelf(); }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    static void attachExternalWorker(IBinder service) {
        if (active != null) {
            boolean replaced = active.worker != null && active.worker != service;
            if (active.worker != service) active.stopRendering();
            active.worker = service;
            if (replaced && active.preview != null && active.windows != null) {
                // A dead remote GPU producer can abandon its buffer queue. Give the replacement a new one.
                WindowManager.LayoutParams layout = (WindowManager.LayoutParams) active.preview.getLayoutParams();
                active.preview.getHolder().removeCallback(active);
                active.windows.removeViewImmediate(active.preview);
                active.preview = new SurfaceView(active.screenContext);
                active.preview.setZOrderOnTop(true);
                active.preview.getHolder().setFormat(PixelFormat.TRANSLUCENT);
                active.preview.getHolder().addCallback(active);
                active.windows.addView(active.preview, layout);
            }
            active.startRendering();
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || preview != null) return START_NOT_STICKY;
        try {
            active = this;
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("preview-mirror", "外屏预览翻转", NotificationManager.IMPORTANCE_LOW));
            startForeground(12, new Notification.Builder(this, "preview-mirror")
                    .setContentTitle("系统相机预览翻转中")
                    .setSmallIcon(android.R.drawable.ic_menu_camera).build());
            displayId = intent.getIntExtra("display_id", -1);
            movie = intent.getBooleanExtra("video", false);
            Display display = getSystemService(DisplayManager.class).getDisplay(displayId);
            if (display == null) throw new IllegalStateException("Display unavailable");
            Context context = createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            screenContext = context;
            windows = context.getSystemService(WindowManager.class);
            android.graphics.Point size = new android.graphics.Point();
            display.getRealSize(size);
            int width = Math.round(size.y * (movie ? 9f / 16 : 3f / 4));
            preview = new SurfaceView(context);
            preview.setZOrderOnTop(true);
            preview.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            preview.getHolder().addCallback(this);
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams(width, size.y,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.TOP | Gravity.LEFT;
            layout.x = (size.x - width) / 2;
            windows.addView(preview, layout);
            controls = new CoverControls(context, new CoverControls.Callbacks() {
                @Override public void onMode(boolean videoMode) {
                    controls.setEnabled(false);
                    SystemCaptureActivity.requestMode(videoMode);
                }
                @Override public void onSettings() { openSettings(); }
            });
            controls.setMode(movie);
            main.post(controlsPoll);
            worker = PreviewWorkerReceiver.availableWorker();
            if (worker != null) startRendering();
            else {
                args = new Shizuku.UserServiceArgs(new ComponentName(this, PreviewMirrorWorker.class))
                        .tag("cover-preview").processNameSuffix("preview")
                        .version(BuildConfig.VERSION_CODE).daemon(false);
                Shizuku.bindUserService(args, connection);
            }
        } catch (RuntimeException failure) {
            android.util.Log.e("XFlipPreviewMirror", "start failed", failure);
            android.widget.Toast.makeText(this, "预览翻转暂不可用，系统拍照仍可使用",
                    android.widget.Toast.LENGTH_SHORT).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startRendering() {
        if (rendererStarted || worker == null || preview == null || !preview.getHolder().getSurface().isValid()) return;
        Parcel data = Parcel.obtain(), result = Parcel.obtain();
        try {
            data.writeInterfaceToken(PreviewMirrorWorker.DESCRIPTOR);
            data.writeTypedObject(preview.getHolder().getSurface(), 0);
            data.writeTypedObject(preview.getSurfaceControl(), 0);
            data.writeInt(displayId);
            data.writeBoolean(movie);
            worker.transact(PreviewMirrorWorker.START, data, result, 0);
            result.readException();
            rendererStarted = true;
        } catch (Exception failure) {
            android.util.Log.e("XFlipPreviewMirror", "render failed", failure);
            stopSelf();
        } finally { data.recycle(); result.recycle(); }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { startRendering(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopRendering(); }

    @Override protected void dump(java.io.FileDescriptor descriptor, java.io.PrintWriter writer, String[] args) {
        writer.println("preview_mirror=" + readWorkerStatus());
        writer.println("display_id=" + displayId + ", video=" + movie);
    }

    private String readWorkerStatus() {
        String status = "not_connected";
        if (worker != null) {
            Parcel data = Parcel.obtain(), result = Parcel.obtain();
            try {
                data.writeInterfaceToken(PreviewMirrorWorker.DESCRIPTOR);
                worker.transact(PreviewMirrorWorker.STATUS, data, result, 0);
                result.readException();
                status = result.readString();
            } catch (Exception failure) { status = failure.toString(); }
            finally { data.recycle(); result.recycle(); }
        }
        return status;
    }

    private void pauseWorker(boolean value) {
        if (worker == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(PreviewMirrorWorker.DESCRIPTOR);
            data.writeBoolean(value);
            worker.transact(PreviewMirrorWorker.PAUSE, data, reply, 0);
            reply.readException();
        } catch (Exception error) { android.util.Log.w("XFlipPreviewMirror", "pause failed", error); }
        finally { data.recycle(); reply.recycle(); }
    }

    private void openSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        pauseWorker(true);
        controls.detach();
        android.content.SharedPreferences preferences = getSharedPreferences("system_capture", MODE_PRIVATE);
        String[] keys = {"watermark_enabled", "auto_stabilize", "burst_enabled", "auto_levels", "detail_enhance", "hdr_tone"};
        boolean[] values = {preferences.getBoolean(keys[0], false), preferences.getBoolean(keys[1], false),
                preferences.getBoolean(keys[2], false),preferences.getBoolean(keys[3],true),
                preferences.getBoolean(keys[4],true),preferences.getBoolean(keys[5],false)};
        Context themed = new android.view.ContextThemeWrapper(screenContext, android.R.style.Theme_Material_Dialog_Alert);
        settingsDialog = new android.app.AlertDialog.Builder(themed)
                .setTitle("相机设置")
                .setMultiChoiceItems(new String[]{"照片文字水印：机型与时间", "录后平移防抖（实验）", "连续三张合成：每张需确认", "自动色阶：去除两端空白", "文字与纹理增强", "JPEG HDR色调（逆光增强）"}, values,
                        (dialog, index, checked) -> preferences.edit().putBoolean(keys[index], checked).apply())
                .setPositiveButton("完成", null).create();
        settingsDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        settingsDialog.setOnDismissListener(dialog -> {
            settingsDialog = null;
            if (!destroying) pauseWorker(false);
        });
        settingsDialog.show();
        int width = Math.min(windows.getCurrentWindowMetrics().getBounds().width() - 24,
                Math.round(420 * screenContext.getResources().getDisplayMetrics().density));
        settingsDialog.getWindow().setLayout(width,windows.getCurrentWindowMetrics().getBounds().height()-24);
    }

    @Override public void onDestroy() {
        destroying = true;
        main.removeCallbacks(controlsPoll);
        if (settingsDialog != null) settingsDialog.dismiss();
        if (controls != null) {
            try { controls.detach(); } catch (RuntimeException error) {
                android.util.Log.w("XFlipPreviewMirror", "controls cleanup failed", error);
            }
        }
        active = null;
        stopRendering();
        try {
            if (args != null && Shizuku.pingBinder()) Shizuku.unbindUserService(args, connection, true);
        } catch (RuntimeException failure) {
            android.util.Log.w("XFlipPreviewMirror", "service already disconnected", failure);
        }
        if (preview != null && preview.isAttachedToWindow() && windows != null) windows.removeView(preview);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
        if (afterStopped != null) main.post(afterStopped);
    }

    private void stopRendering() {
        if (worker != null) {
            Parcel data = Parcel.obtain(), result = Parcel.obtain();
            try {
                data.writeInterfaceToken(PreviewMirrorWorker.DESCRIPTOR);
                worker.transact(PreviewMirrorWorker.STOP, data, result, 0);
                result.readException();
            } catch (Exception ignored) { }
            finally { data.recycle(); result.recycle(); }
        }
        rendererStarted = false;
    }
}
