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
        Looper.prepareMainLooper();
        PreviewMirrorWorker worker = new PreviewMirrorWorker();
        Intent attach = new Intent("com.sturgeon.xflipcamera.ATTACH_PREVIEW_WORKER")
                .setComponent(new ComponentName(args[0], PreviewWorkerReceiver.class.getName()));
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
        System.out.println("preview_worker_started uid=" + Process.myUid());
        Looper.loop();
    }
}
