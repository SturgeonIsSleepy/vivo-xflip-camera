package com.sturgeon.xflipcamera.core;

/** Selection/recreation scenarios without Android UI or a real content provider. */
public final class PhotoInspectionStateChecks {
    private static final String SHARED = "content://fixture/shared-a";
    private static final String PICKED = "content://fixture/picked-b";
    private static int assertions;

    private PhotoInspectionStateChecks() { }

    public static void main(String[] args) {
        System.out.println("PASS " + run() + " inspection-state assertions");
    }

    public static int run() {
        assertions = 0;
        completedPickerSelectionSurvivesRecreation();
        pendingSelectionResumesWithoutAnOldResult();
        oldAndRepeatedCompletionsAreRejected();
        failureReportsStayAttachedToTheirSource();
        invalidStateCannotInventAnInspection();
        return assertions;
    }

    private static void completedPickerSelectionSurvivesRecreation() {
        PhotoInspectionState page = new PhotoInspectionState(null, null);
        check(page.selectedSource() == null && page.report() == null, "Cold start has no evidence");
        long first = page.begin(SHARED);
        check(page.complete(first, "A: sampled RGB zero"), "Shared photo finishes");
        long second = page.begin(PICKED);
        check(page.report() == null, "Picking B immediately disables copying A's evidence");
        check(page.complete(second, "B: sampled RGB nonzero"), "Picker photo finishes");
        PhotoInspectionState restored = restore(page);
        check(PICKED.equals(restored.selectedSource()), "Restore B instead of launch photo A");
        check("B: sampled RGB nonzero".equals(restored.report()), "Completed B needs no new decode");
        check(!restored.complete(0, "invented result"), "Restoration is not an active read");
        page.cancel();
        check(!page.complete(second, "late A"), "Destroyed instance cannot alter saved evidence");
        check("B: sampled RGB nonzero".equals(restored.report()), "Restored result stays independent");
    }

    private static void pendingSelectionResumesWithoutAnOldResult() {
        PhotoInspectionState page = new PhotoInspectionState(null, null);
        long shared = page.begin(SHARED);
        page.complete(shared, "A result");
        long pending = page.begin(PICKED);
        PhotoInspectionState restored = restore(page);
        page.cancel();
        check(PICKED.equals(restored.selectedSource()), "In-flight selection survives recreation");
        check(restored.report() == null, "In-flight B cannot restore A's completed report");
        check(!page.complete(pending, "late B result"), "Destroyed reader is invalidated");
        long resumed = restored.begin(restored.selectedSource());
        check(restored.complete(resumed, "B resumed"), "Fresh instance can resume its own read");
        check("B resumed".equals(restored.report()), "Resumed report belongs to B");
    }

    private static void oldAndRepeatedCompletionsAreRejected() {
        PhotoInspectionState page = new PhotoInspectionState(null, null);
        long first = page.begin(SHARED);
        long second = page.begin(PICKED);
        check(first != 0 && second != first, "Every selection has a distinct active token");
        check(!page.complete(first, "old A"), "A cannot overwrite pending B");
        check(page.report() == null, "Rejected A leaves B pending");
        check(page.complete(second, "B result"), "Current read can complete");
        check(!page.complete(second, "duplicate B"), "Duplicate completion is rejected");
        check("B result".equals(page.report()), "Duplicate does not change displayed evidence");
        long sameUri = page.begin(PICKED);
        check(page.report() == null, "Rechecking the same URI clears its previous evidence");
        check(!page.complete(second, "stale same URI"), "URI equality cannot accept an old token");
        page.cancel();
        check(!page.complete(sameUri, "after exit"), "Exit rejects current completion");
    }

    private static void failureReportsStayAttachedToTheirSource() {
        PhotoInspectionState page = new PhotoInspectionState(PICKED, null);
        check(page.report() == null, "Saved URI alone is not a successful inspection");
        long read = page.begin(page.selectedSource());
        check(page.complete(read, "Inspection incomplete: SecurityException"),
                "Lost grant is represented as failure, not black evidence");
        PhotoInspectionState restored = restore(page);
        check(PICKED.equals(restored.selectedSource()), "Failure keeps the attempted source");
        check("Inspection incomplete: SecurityException".equals(restored.report()),
                "Restore preserves the explicit failure");
    }

    private static void invalidStateCannotInventAnInspection() {
        fails(() -> new PhotoInspectionState(null, "orphan report"));
        fails(() -> new PhotoInspectionState("", null));
        PhotoInspectionState page = new PhotoInspectionState(PICKED, "B result");
        fails(() -> page.begin(null));
        fails(() -> page.begin(""));
        check(PICKED.equals(page.selectedSource()) && "B result".equals(page.report()),
                "Rejected input leaves the valid selection intact");
        long token = page.begin(PICKED);
        fails(() -> page.complete(token, null));
        check(page.report() == null, "Missing output cannot become evidence");
        check(page.complete(token, "B retry"), "Invalid output did not consume the valid read");
    }

    private static PhotoInspectionState restore(PhotoInspectionState page) {
        return new PhotoInspectionState(page.selectedSource(), page.report());
    }

    private static void fails(Runnable action) {
        boolean failed = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        check(failed, "Invalid state must fail explicitly");
    }

    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) throw new AssertionError(message);
    }
}
