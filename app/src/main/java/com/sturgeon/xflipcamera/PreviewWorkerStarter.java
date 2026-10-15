package com.sturgeon.xflipcamera;

import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;

/** Wired setup fallback for OEMs which block the Shizuku manager's external provider. */
public final class PreviewWorkerStarter {
    @android.annotation.SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    public static void main(String[] args) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 34) throw new UnsupportedOperationException("Android 14 required");
        if (Process.myUid() != 2000 && Process.myUid() != 0) throw new SecurityException("Shell required");
        if (args.length != 1 || !args[0].matches("[A-Za-z0-9_.]+")) throw new IllegalArgumentException("Package required");
        Looper.prepareMainLooper();
        PreviewMirrorWorker worker = new PreviewMirrorWorker();
        java.io.File request = new java.io.File("/sdcard/Android/data/" + args[0] + "/files/preview-worker-request");
        android.os.Handler main = new android.os.Handler(Looper.getMainLooper());
        Runnable attach = () -> {
            try { share(args[0], worker); } catch (Exception error) {
                System.err.println("preview_worker_attach_failed=" + error.getClass().getSimpleName());
            }
        };
        attach.run();
        main.postDelayed(new Runnable() {
            private String previous = readRequest(request);
            @Override public void run() {
                String next = readRequest(request);
                if (!next.isEmpty() && !next.equals(previous)) { previous = next; attach.run(); }
                main.postDelayed(this, 500);
            }
        }, 500);
        System.out.println("preview_worker_started uid=" + Process.myUid() + ", reconnect=file_request");
        Looper.loop();
    }

    private static String readRequest(java.io.File file) {
        if (file.length() < 1 || file.length() > 128) return "";
        try { return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8).trim(); }
        catch (java.io.IOException error) { return ""; }
    }

    @android.annotation.SuppressLint({"PrivateApi", "BlockedPrivateApi"})
    @androidx.annotation.RequiresApi(34)
    private static void share(String packageName, PreviewMirrorWorker worker) throws Exception {
        Intent attach = new Intent("com.sturgeon.xflipcamera.ATTACH_PREVIEW_WORKER")
                .setComponent(new ComponentName(packageName, PreviewWorkerReceiver.class.getName()));
        Bundle extras = new Bundle();
        extras.putBinder("worker", worker);
        attach.putExtras(extras);
        Object manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class.forName("android.app.IActivityManager").getMethod("broadcastIntentWithFeature",
                Class.forName("android.app.IApplicationThread"), String.class, Intent.class, String.class,
                Class.forName("android.content.IIntentReceiver"), int.class, String.class, Bundle.class,
                String[].class, String[].class, String[].class, int.class, Bundle.class,
                boolean.class, boolean.class, int.class)
                .invoke(manager, null, null, attach, null, null, 0, null, null, null, null, null, -1,
                        BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(), false, false, 0);
    }
}
