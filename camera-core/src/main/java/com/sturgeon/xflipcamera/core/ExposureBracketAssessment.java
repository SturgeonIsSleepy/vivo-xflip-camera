package com.sturgeon.xflipcamera.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative metadata-only screening, never proof of acquisition, registration or HDR quality. */
public final class ExposureBracketAssessment {
    public static final double MIN_SIDE_EV = 1.0;
    public static final long MAX_PIXELS = 13_000_000L;
    private static final double LOG_TWO = Math.log(2);
    private static final double LENS_LOG_TOLERANCE = Math.log(1.01);
    private ExposureBracketAssessment() { }

    public enum Status {
        METADATA_CANDIDATE_ONLY, INPUT_COUNT, MISSING_OR_INVALID_METADATA, DUPLICATE_FILE,
        INCONSISTENT_GEOMETRY, CAMERA_OR_LENS_CHANGED, FLASH_OR_UNKNOWN,
        INSUFFICIENT_CAPTURE_SPAN, INSUFFICIENT_GAIN_SPAN
    }

    /** Source hashes and camera identity stay internal; even toString does not expose them. */
    public static final class Frame {
        private final int width, height, orientation, flash;
        private final double seconds, fNumber, iso, focalLength;
        private final String cameraIdentity, digest;
        public Frame(int width, int height, int orientation, double seconds, double fNumber,
                double iso, double focalLength, int flash, String cameraIdentity, String digest) {
            this.width = width; this.height = height; this.orientation = orientation; this.seconds = seconds;
            this.fNumber = fNumber; this.iso = iso; this.focalLength = focalLength; this.flash = flash;
            this.cameraIdentity = cameraIdentity; this.digest = digest;
        }
        @Override public String toString() { return "ExposureFrame[private source metadata]"; }
        private double captureEv() { return (Math.log(seconds) - 2 * Math.log(fNumber)) / LOG_TWO; }
        private double gainEv() { return captureEv() + Math.log(iso) / LOG_TWO; }
    }

    /** Indices refer only to input order. No URI, filename, fingerprint, camera identity or GPS is returned. */
    public record Result(Status status, List<Integer> sourceOrder, int referenceIndex,
            double lowerCaptureEv, double upperCaptureEv, double lowerGainEv, double upperGainEv) {
        public Result { sourceOrder = List.copyOf(sourceOrder); }
    }

    public static Result rejected(Status status) {
        if (status == null || status == Status.METADATA_CANDIDATE_ONLY) throw new IllegalArgumentException("Rejection status required");
        return new Result(status, List.of(), -1, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public static Result assess(List<Frame> frames) {
        if (frames == null || frames.size() != 3) return rejected(Status.INPUT_COUNT);
        for (Frame frame : frames) {
            if (frame == null || frame.width <= 0 || frame.height <= 0
                    || (long) frame.width * frame.height > MAX_PIXELS
                    || frame.orientation < 1 || frame.orientation > 8 || !positive(frame.seconds)
                    || !positive(frame.fNumber) || !positive(frame.iso) || !positive(frame.focalLength)
                    || frame.cameraIdentity == null || frame.cameraIdentity.isBlank() || !digest(frame.digest)) {
                return rejected(Status.MISSING_OR_INVALID_METADATA);
            }
        }
        Frame first = frames.get(0);
        for (int i = 0; i < 3; i++) {
            Frame frame = frames.get(i);
            for (int j = 0; j < i; j++) if (frame.digest.equalsIgnoreCase(frames.get(j).digest)) {
                return rejected(Status.DUPLICATE_FILE);
            }
            if (frame.flash < 0 || (frame.flash & 1) != 0) return rejected(Status.FLASH_OR_UNKNOWN);
            if (ExifTransform.outputWidth(frame.width, frame.height, frame.orientation)
                    != ExifTransform.outputWidth(first.width, first.height, first.orientation)
                    || ExifTransform.outputHeight(frame.width, frame.height, frame.orientation)
                    != ExifTransform.outputHeight(first.width, first.height, first.orientation)) {
                return rejected(Status.INCONSISTENT_GEOMETRY);
            }
            if (!frame.cameraIdentity.equals(first.cameraIdentity)) {
                return rejected(Status.CAMERA_OR_LENS_CHANGED);
            }
            // Pairwise tolerance, not distance from the first item: input order cannot relax it.
            for (int j = 0; j < i; j++) {
                Frame other = frames.get(j);
                if (Math.abs(Math.log(frame.fNumber) - Math.log(other.fNumber)) > LENS_LOG_TOLERANCE
                        || Math.abs(Math.log(frame.focalLength) - Math.log(other.focalLength)) > LENS_LOG_TOLERANCE) {
                    return rejected(Status.CAMERA_OR_LENS_CHANGED);
                }
            }
        }
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        order.sort(Comparator.comparingDouble(index -> frames.get(index).captureEv()));
        Frame shortFrame = frames.get(order.get(0)), middle = frames.get(order.get(1)), longFrame = frames.get(order.get(2));
        double captureLow = middle.captureEv() - shortFrame.captureEv(), captureHigh = longFrame.captureEv() - middle.captureEv();
        if (!wide(captureLow) || !wide(captureHigh)) return rejected(Status.INSUFFICIENT_CAPTURE_SPAN);
        double gainLow = middle.gainEv() - shortFrame.gainEv(), gainHigh = longFrame.gainEv() - middle.gainEv();
        if (!wide(gainLow) || !wide(gainHigh)) return rejected(Status.INSUFFICIENT_GAIN_SPAN);
        return new Result(Status.METADATA_CANDIDATE_ONLY, order, order.get(1), captureLow, captureHigh, gainLow, gainHigh);
    }

    private static boolean wide(double value) { return value + 1e-9 >= MIN_SIDE_EV; }
    private static boolean positive(double value) { return Double.isFinite(value) && value > 0; }
    private static boolean digest(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F')) return false;
        }
        return true;
    }
}
