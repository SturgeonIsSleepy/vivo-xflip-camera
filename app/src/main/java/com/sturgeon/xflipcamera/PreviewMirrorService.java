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
            active.worker = service;
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
        writer.println("preview_mirror=" + status);
        writer.println("display_id=" + displayId + ", video=" + movie);
    }

    @Override public void onDestroy() {
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
