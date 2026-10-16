package com.sturgeon.xflipcamera.core;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Fault-injected owned-media provider, including partial writes and missing publish rows. */
public final class PendingMediaWriteChecks {
    private static int assertions;

    public static int run() {
        assertions = 0;
        successfulWritesKeepPreviousItems();
        creationFailuresOwnNothing();
        writeAndPublishFailuresRollBackOnlyTheirOutput();
        cleanupFailurePreservesThePrimaryFailure();
        errorsRollBackAndRethrowTheSameInstance();
        cleanupErrorsCannotReplaceThePrimaryFailure();
        return assertions;
    }

    public static void main(String[] args) {
        int count;
        if (args.length == 0) count = run();
        else if (args.length == 1 && args[0].equals("write-errors")) {
            assertions = 0; errorsRollBackAndRethrowTheSameInstance(); count = assertions;
        } else if (args.length == 1 && args[0].equals("cleanup-errors")) {
            assertions = 0; cleanupErrorsCannotReplaceThePrimaryFailure(); count = assertions;
        } else throw new IllegalArgumentException("Use no arguments, write-errors or cleanup-errors");
        System.out.println("PASS " + count + " media-write assertions: publish rows, "
                + "partial writes, rollback ownership, primary and cleanup failures including Error");
    }

    private static void successfulWritesKeepPreviousItems() {
        Store store = new Store();
        String first = success(store), second = success(store);
        check(!first.equals(second), "Every write owns a different output");
        check(store.visible.contains(first) && store.visible.contains(second), "Both outputs published");
        check(store.pending.isEmpty() && store.deleteCalls == 0, "Success is never rolled back");
        preserved(store);
    }

    private static void creationFailuresOwnNothing() {
        Store failed = new Store();
        IOException creation = new IOException("insert");
        failed.insertFailure = creation;
        check(failure(failed) == creation, "Creation error stays primary");
        check(failed.writeCalls == 0 && failed.deleteCalls == 0, "No output exists to write or delete");
        preserved(failed);
        Store empty = new Store();
        empty.nullInsert = true;
        check(failure(empty) instanceof IOException, "Null insert cannot succeed");
        check(empty.publishCalls == 0 && empty.deleteCalls == 0, "No null-row cleanup");
        preserved(empty);
    }

    private static void writeAndPublishFailuresRollBackOnlyTheirOutput() {
        for (Exception problem : new Exception[]{new IOException("encode/close/EXIF"),
                new SecurityException("write permission")}) {
            Store store = new Store();
            store.writeFailure = problem;
            check(failure(store) == problem, "Write failure is preserved");
            check(store.partialBytesWritten, "Failure follows a partial write");
            check(store.publishCalls == 0, "Partial data never gets published");
            rolledBack(store);
        }
        for (int rows : new int[]{0, 2, -1}) {
            Store store = new Store();
            store.publishRows = rows;
            Throwable problem = failure(store);
            check(problem instanceof IOException && problem.getMessage().contains("updated " + rows),
                    "Unexpected publish rows cannot report success");
            check(store.writeCalls == 1 && store.publishCalls == 1, "Write closes before publish");
            rolledBack(store);
        }
        for (Exception problem : new Exception[]{new IOException("publish"),
                new IllegalStateException("provider unavailable")}) {
            Store store = new Store();
            store.publishFailure = problem;
            check(failure(store) == problem, "Publication failure is preserved");
            rolledBack(store);
        }
    }

    private static void cleanupFailurePreservesThePrimaryFailure() {
        for (Exception cleanup : new Exception[]{new IOException("delete"),
                new SecurityException("delete permission")}) {
            Store store = new Store();
            IOException primary = new IOException("write");
            store.writeFailure = primary;
            store.deleteFailure = cleanup;
            check(failure(store) == primary, "Cleanup cannot replace the original failure");
            check(primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == cleanup,
                    "Cleanup failure remains available for diagnostics");
            check(store.pending.contains(store.created), "Failed rollback is not claimed as deletion");
            preserved(store);
        }
        Store reused = new Store();
        IOException same = new IOException("provider reused error");
        reused.writeFailure = same;
        reused.deleteFailure = same;
        check(failure(reused) == same && same.getSuppressed().length == 0,
                "Same exception must not cause a self-suppression error");
        preserved(reused);
    }

    private static void errorsRollBackAndRethrowTheSameInstance() {
        for (boolean atPublish : new boolean[]{false, true}) {
            for (Error problem : new Error[]{new OutOfMemoryError("Injected allocation failure, not real heap exhaustion"),
                    new LinkageError("Injected encoder linkage failure"), new AssertionError("Injected writer failure")}) {
                Store store = new Store();
                if (atPublish) store.publishFailure = problem; else store.writeFailure = problem;
                check(failure(store) == problem, "Error must propagate unchanged, never become success/IOException");
                check(store.partialBytesWritten, "Injected Error follows an owned partial write");
                check(store.publishCalls == (atPublish ? 1 : 0), "Failed encoding cannot publish");
                rolledBack(store);
            }
        }
        Store failedInsert = new Store();
        Error insertion = new OutOfMemoryError("Injected insertion failure before output exists");
        failedInsert.insertFailure = insertion;
        check(failure(failedInsert) == insertion, "Insertion Error remains primary");
        check(failedInsert.created == null && failedInsert.deleteCalls == 0,
                "No unknown output is deleted after insertion Error");
        preserved(failedInsert);
    }

    private static void cleanupErrorsCannotReplaceThePrimaryFailure() {
        for (Throwable primary : new Throwable[]{new IOException("write failure"), new SecurityException("write denial"),
                new OutOfMemoryError("Injected primary allocation failure")}) {
            Store store = new Store();
            Error cleanup = new AssertionError("Injected delete failure");
            store.writeFailure = primary; store.deleteFailure = cleanup;
            check(failure(store) == primary, "Cleanup Error cannot mask the primary failure");
            check(store.deleteCalls == 1 && store.pending.contains(store.created),
                    "Failed Error cleanup was attempted, not claimed successful");
            check(primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == cleanup,
                    "Cleanup Error is retained as suppressed diagnostic");
            preserved(store);
        }
        Store reused = new Store();
        Error same = new OutOfMemoryError("Injected provider reuses Error instance");
        reused.writeFailure = same; reused.deleteFailure = same;
        check(failure(reused) == same && same.getSuppressed().length == 0, "Error cannot self-suppress");
        preserved(reused);
    }

    private static void rolledBack(Store store) {
        check(store.deleteCalls == 1 && store.pending.isEmpty(), "New partial output is removed once");
        check(!store.visible.contains(store.created), "Failed output cannot be reported as visible");
        preserved(store);
    }

    private static void preserved(Store store) {
        check(store.visible.contains("source") && store.visible.contains("older-photo"),
                "Source and previously saved media remain untouched");
    }

    private static String success(Store store) {
        try { return PendingMediaWrite.execute(store); }
        catch (IOException failure) { throw new AssertionError("Unexpected write failure", failure); }
    }

    private static Throwable failure(Store store) {
        try { PendingMediaWrite.execute(store); }
        catch (IOException | RuntimeException | Error expected) { return expected; }
        throw new AssertionError("Failure was silently accepted");
    }

    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) throw new AssertionError(message);
    }

    private static final class Store implements PendingMediaWrite.Storage<String> {
        final Set<String> visible = new LinkedHashSet<>(Set.of("source", "older-photo"));
        final Set<String> pending = new LinkedHashSet<>();
        Throwable insertFailure, writeFailure, publishFailure, deleteFailure;
        boolean nullInsert, partialBytesWritten;
        int serial, writeCalls, publishCalls, deleteCalls, publishRows = 1;
        String created;

        @Override public String insertPending() throws IOException {
            raise(insertFailure);
            if (nullInsert) return null;
            created = "output-" + ++serial;
            pending.add(created);
            return created;
        }

        @Override public void writeAndClose(String output) throws IOException {
            check(pending.contains(output), "Write targets the newly owned row");
            writeCalls++;
            partialBytesWritten = true;
            raise(writeFailure);
        }

        @Override public int publish(String output) throws IOException {
            check(pending.contains(output) && partialBytesWritten, "Publish follows bytes/metadata");
            publishCalls++;
            raise(publishFailure);
            if (publishRows == 1) { pending.remove(output); visible.add(output); }
            return publishRows;
        }

        @Override public void deletePending(String output) throws IOException {
            check(output.equals(created), "Rollback cannot delete another media item");
            deleteCalls++;
            raise(deleteFailure);
            pending.remove(output);
        }

        private static void raise(Throwable problem) throws IOException {
            if (problem instanceof IOException) throw (IOException) problem;
            if (problem instanceof RuntimeException) throw (RuntimeException) problem;
            if (problem instanceof Error) throw (Error) problem;
        }
    }
}
