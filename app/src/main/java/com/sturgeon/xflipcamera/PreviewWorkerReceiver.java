package com.sturgeon.xflipcamera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/** Accepts only an explicitly shared, real shell/root identity, protected by DUMP permission. */
public final class PreviewWorkerReceiver extends BroadcastReceiver {
    private static IBinder worker;

    static IBinder availableWorker() {
        return worker != null && worker.pingBinder() ? worker : null;
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT < 34) return;
        int uid = getSentFromUid();
        if (uid != 2000 && uid != 0) return;
        if (intent.getExtras() == null) return;
        IBinder candidate = intent.getExtras().getBinder("worker");
        try {
            if (candidate == null || !PreviewMirrorWorker.DESCRIPTOR.equals(candidate.getInterfaceDescriptor())) return;
            worker = candidate;
            PreviewMirrorService.attachExternalWorker(candidate);
            android.util.Log.i("XFlipPreviewMirror", "shell worker attached, uid=" + uid);
        } catch (android.os.RemoteException failure) {
            android.util.Log.w("XFlipPreviewMirror", "worker already gone", failure);
        }
    }
}
