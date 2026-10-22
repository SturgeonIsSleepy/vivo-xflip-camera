package com.sturgeon.xflipcamera.core;

/** Deterministic state restoration checks without Android or media dependencies. */
final class MediaPreviewRestoreChecks {
    private MediaPreviewRestoreChecks() {}

    static int run() {
        int checks = 0;
        Object capture = new Object();
        Object output = new Object();

        check(MediaPreviewRestore.source(true, false, capture, output) == null,
                "An in-flight OEM camera must not be read before its result");
        checks++;
        check(MediaPreviewRestore.source(true, true, capture, output) == capture,
                "A returned pending capture reloads its private capture file");
        checks++;
        check(MediaPreviewRestore.source(true, true, null, output) == null,
                "A pending result cannot fall back to a half-published output");
        checks++;
        check(MediaPreviewRestore.source(true, true, capture, null) == null,
                "A pending result without its MediaStore destination cannot be processed");
        checks++;
        check(MediaPreviewRestore.source(false, true, null, output) == output,
                "An output-only read survives Activity recreation without a capture file");
        checks++;
        check(MediaPreviewRestore.source(false, false, capture, output) == output,
                "A displayed published preview reloads from its output, not stale cache");
        checks++;
        check(MediaPreviewRestore.source(false, true, capture, null) == null,
                "Missing published output is reported instead of reading stale cache");
        checks++;
        return checks;
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
