package com.sturgeon.xflipcamera.core;

/** Independent preview and saved-photo preferences; no implicit front-camera flip. */
public final class MirrorSettings {
    public final boolean previewMirrored;
    public final boolean photoMirrored;

    public MirrorSettings(boolean previewMirrored, boolean photoMirrored) {
        this.previewMirrored = previewMirrored;
        this.photoMirrored = photoMirrored;
    }

    public static MirrorSettings defaults() {
        return new MirrorSettings(false, false);
    }

    public MirrorSettings withPreview(boolean mirrored) {
        return new MirrorSettings(mirrored, photoMirrored);
    }

    public MirrorSettings withPhoto(boolean mirrored) {
        return new MirrorSettings(previewMirrored, mirrored);
    }
}
