package com.sturgeon.xflipcamera.core;

import java.io.IOException;
import java.util.Objects;

/** One newly owned output: finish bytes/metadata before publishing; best-effort rollback on failure. */
public final class PendingMediaWrite {
    private PendingMediaWrite() { }

    @FunctionalInterface
    public interface Cleanup {
        void run() throws IOException;
    }

    public interface Storage<T> {
        /** Returns a newly created pending item, never a previously saved/source item. */
        T insertPending() throws IOException;
        /** Must close bytes and metadata before returning successfully. */
        void writeAndClose(T output) throws IOException;
        /** Returns the number of rows whose pending flag was cleared. */
        int publish(T output) throws IOException;
        /** Deletes only this output; zero rows is allowed if it already disappeared. */
        void deletePending(T output) throws IOException;
    }

    public static <T> T execute(Storage<T> storage) throws IOException {
        Objects.requireNonNull(storage, "storage");
        T output = storage.insertPending();
        if (output == null) throw new IOException("No pending media item was created");
        try {
            storage.writeAndClose(output);
            int rows = storage.publish(output);
            if (rows != 1) throw new IOException("Media publish expected one row, updated " + rows);
            return output;
        } catch (IOException | RuntimeException | Error failure) {
            cleanupPreserving(failure, () -> storage.deletePending(output));
            throw failure;
        }
    }

    /** Runs one best-effort cleanup without replacing the failure that made cleanup necessary. */
    public static void cleanupPreserving(Throwable failure, Cleanup cleanup) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(cleanup, "cleanup");
        try {
            cleanup.run();
        } catch (IOException | RuntimeException | Error cleanupFailure) {
            // A broken provider may reuse the same exception instance; do not self-suppress.
            if (cleanupFailure != failure) {
                try { failure.addSuppressed(cleanupFailure); }
                catch (OutOfMemoryError unavailable) { /* Diagnostics cannot replace the original failure. */ }
            }
        }
    }
}
