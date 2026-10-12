package com.sturgeon.xflipcamera.core;

/** Main-thread capture tokens prevent stale callbacks from unlocking a newer capture. */
public final class CaptureGate {
    private long nextToken = 1;
    private long activeToken;

    public boolean isBusy() {
        return activeToken != 0;
    }

    /** Returns zero when another capture already owns the gate. */
    public long tryStart() {
        if (isBusy()) return 0;
        activeToken = nextToken++;
        return activeToken;
    }

    public boolean finish(long token) {
        if (token == 0 || token != activeToken) return false;
        activeToken = 0;
        return true;
    }

    public void cancel() {
        activeToken = 0;
    }
}
