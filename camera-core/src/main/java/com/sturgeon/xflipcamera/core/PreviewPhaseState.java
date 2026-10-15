package com.sturgeon.xflipcamera.core;

/** Missing controls preserve the last known direction; only a positive review signal reveals it. */
public final class PreviewPhaseState {
    public static final int WAITING = 0, PREVIEW = 1, RECORDING = 2, REVIEW = 3, SETTINGS = 4;
    private int phase;
    private int readySamples;

    public void sample(int controls, boolean review) {
        if (review) {
            phase = REVIEW;
            readySamples = 0;
        } else if (controls == SETTINGS) {
            phase = SETTINGS;
            readySamples = 0;
        } else if (controls == PREVIEW || controls == RECORDING) {
            if (phase == PREVIEW || phase == RECORDING || ++readySamples >= 2) {
                phase = controls;
                readySamples = 0;
            }
        } else readySamples = 0;
    }

    public int phase() { return (phase == REVIEW || phase == SETTINGS) && readySamples > 0 ? WAITING : phase; }
    public void reset() { phase = WAITING; readySamples = 0; }

    /** Filled stop squares differ from the circular record button even at small capture sizes. */
    public static boolean isRecordingShape(int hits, int width, int height) {
        return width > 0 && height > 0 && hits > width * (double) height * 0.86;
    }
}
