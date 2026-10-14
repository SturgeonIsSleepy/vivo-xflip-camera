package com.sturgeon.xflipcamera.core;

/** Main-thread state for one read-only inspection page; tokens belong to this instance. */
public final class PhotoInspectionState {
    private String selectedSource;
    private String report;
    private long nextToken = 1;
    private long activeToken;

    /** Only these two small strings are restored; no bitmap or URI permission is retained. */
    public PhotoInspectionState(String selectedSource, String report) {
        if (selectedSource == null && report != null) {
            throw new IllegalArgumentException("A report must belong to a selected source");
        }
        if (selectedSource != null) requireSource(selectedSource);
        this.selectedSource = selectedSource;
        this.report = report;
    }

    public String selectedSource() {
        return selectedSource;
    }

    public String report() {
        return report;
    }

    public long begin(String source) {
        requireSource(source);
        selectedSource = source;
        report = null;
        activeToken = nextToken++;
        return activeToken;
    }

    /** Stale, duplicate and destroyed-page callbacks cannot replace the current report. */
    public boolean complete(long token, String completedReport) {
        if (token == 0 || token != activeToken) return false;
        if (completedReport == null) throw new IllegalArgumentException("Missing report");
        report = completedReport;
        activeToken = 0;
        return true;
    }

    public void cancel() {
        activeToken = 0;
    }

    private static void requireSource(String source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Missing selected source");
        }
    }
}
