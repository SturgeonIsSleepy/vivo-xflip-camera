package com.sturgeon.xflipcamera.core;

/** Selects the only media source that is safe to reload after Activity recreation. */
public final class MediaPreviewRestore {
    private MediaPreviewRestore() {}

    /**
     * While the OEM capture is still open, no preview may be read. Once its result is being
     * processed, the private capture file is the source. Published or edited media uses the
     * MediaStore output and must not depend on the private capture file still being present.
     */
    public static <T> T source(boolean pending, boolean reading, T captureFile, T publishedOutput) {
        if (pending) return reading ? captureFile : null;
        return publishedOutput;
    }
}
