package com.sturgeon.xflipcamera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/** Accepts only an explicitly shared, real shell/root identity, protected by DUMP permission. */
public final class PreviewWorkerReceiver extends BroadcastReceiver {
    private static IBinder worker;
    private static boolean binding;
    private static long lastRequest;

    /** A request contains no binder or command; the shell replies only to its fixed DUMP-protected receiver. */
    static void requestWorker(Context context) {
        if (availableWorker() != null) { SystemCaptureActivity.workerReady(); return; }
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastRequest < 1000) return;
        lastRequest = now;
        try {
            java.io.File directory = context.getExternalFilesDir(null);
            if (directory == null) throw new java.io.IOException("External app directory unavailable");
            java.nio.file.Files.write(new java.io.File(directory, "preview-worker-request").toPath(),
                    java.util.UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            android.util.Log.w("XFlipPreviewMirror", "reconnect request failed", failure);
        }
        try {
            if (!binding && rikka.shizuku.Shizuku.pingBinder()
                    && rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                binding = true;
                rikka.shizuku.Shizuku.bindUserService(new rikka.shizuku.Shizuku.UserServiceArgs(
                        new android.content.ComponentName(context, PreviewMirrorWorker.class))
                        .tag("cover-preview").processNameSuffix("preview")
                        .version(BuildConfig.VERSION_CODE).daemon(false), new android.content.ServiceConnection() {
                    private IBinder connected;
                    @Override public void onServiceConnected(android.content.ComponentName name, IBinder service) {
                        connected = service;
                        binding = false;
                        acceptWorker(service);
                    }
                    @Override public void onServiceDisconnected(android.content.ComponentName name) {
                        binding = false;
                        if (worker == connected) worker = null;
                    }
                });
            }
        } catch (RuntimeException failure) {
            binding = false;
            android.util.Log.w("XFlipPreviewMirror", "Shizuku reconnect unavailable", failure);
        }
    }

    private static void acceptWorker(IBinder candidate) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> acceptWorker(candidate));
            return;
        }
        try {
            if (candidate == null || !PreviewMirrorWorker.DESCRIPTOR.equals(candidate.getInterfaceDescriptor())) return;
            worker = candidate;
            PreviewMirrorService.attachExternalWorker(candidate);
            SystemCaptureActivity.workerReady();
            android.util.Log.i("XFlipPreviewMirror", "preview worker attached");
        } catch (android.os.RemoteException failure) {
            android.util.Log.w("XFlipPreviewMirror", "worker already gone", failure);
        }
    }

    static IBinder availableWorker() {
        return worker != null && worker.pingBinder() ? worker : null;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT < 34) return;
        int uid = getSentFromUid();
        if (uid != 2000 && uid != 0) return;
        if (intent.getExtras() == null) return;
        IBinder candidate = intent.getExtras().getBinder("worker");
        acceptWorker(candidate);
    }
}
