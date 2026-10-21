package com.sturgeon.xflipcamera.core;

import java.util.Arrays;

/** Dependency-free, executable raster tests shared by local checks and Gradle CI. */
public final class CoreChecks {
    private static int assertions;

    public static void main(String[] args) {
        asymmetricRasterFixtures();
        allRasterCoordinatesAreBijectiveAndInvertible();
        reflectionClassification();
        previewAndPhotoAreIndependent();
        staleCaptureCallbacksCannotUnlockNewCaptures();
        blackPixelEvidenceDoesNotAcceptEmptyOrNearBlack();
        jpegSamplingIsBoundedBeforeDecode();
        assertions += PendingMediaWriteChecks.run();
        assertions += PhotoInspectionStateChecks.run();
        assertions += CoverControlLayoutChecks.run();
        assertions += VideoMotionPlanChecks.run();
        assertions += PreviewPhaseStateChecks.run();
        assertions += MultiFrameAccumulatorChecks.run();
        assertions += PhotoToneCurveChecks.run();
        assertions += PhotoToneRowChecks.run();
        assertions += InputLevelsChecks.run();
        assertions += ExposureBracketChecks.run();
        assertions += BoundedDigestChecks.run();
        assertions += UnmodifiedRowWindowChecks.run();
        assertions += ProcessingTimingsChecks.run();
        assertions += MediaPreviewRestoreChecks.run();
        invalidInputsFailExplicitly();
        System.out.println("PASS " + assertions + " assertions: EXIF 1..8, raster bounds, "
                + "inverse transforms, mirror preferences, capture tokens, RGB evidence, JPEG budgets, media writes, inspection state, video motion, cover controls, tone rows, exposure metadata, bounded streams, unmodified row windows, processing timings, media preview restore");
    }

    private static void asymmetricRasterFixtures() {
        // Encoded rows: ABC / DEF. Expected displayed rows are fixed EXIF fixtures.
        String[] expected = {
                "ABCDEF", "CBAFED", "FEDCBA", "DEFABC",
                "ADBECF", "DAEBFC", "FCEBDA", "CFBEAD"
        };
        for (int orientation = 1; orientation <= 8; orientation++) {
            char[] output = new char[6];
            int width = ExifTransform.outputWidth(3, 2, orientation);
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 3; x++) {
                    ExifTransform.Point point = ExifTransform.map(x, y, 3, 2, orientation);
                    output[point.y * width + point.x] = "ABCDEF".charAt(y * 3 + x);
                }
            }
            check(new String(output).equals(expected[orientation - 1]),
                    "Asymmetric EXIF fixture " + orientation);
        }
    }

    private static void allRasterCoordinatesAreBijectiveAndInvertible() {
        int[] inverse = {0, 1, 2, 3, 4, 5, 8, 7, 6};
        for (int width = 1; width <= 7; width++) {
            for (int height = 1; height <= 5; height++) {
                for (int orientation = 1; orientation <= 8; orientation++) {
                    int outWidth = ExifTransform.outputWidth(width, height, orientation);
                    int outHeight = ExifTransform.outputHeight(width, height, orientation);
                    boolean[] visited = new boolean[outWidth * outHeight];
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            ExifTransform.Point point =
                                    ExifTransform.map(x, y, width, height, orientation);
                            check(point.x >= 0 && point.x < outWidth
                                            && point.y >= 0 && point.y < outHeight,
                                    "Output coordinate bounds");
                            int index = point.y * outWidth + point.x;
                            check(!visited[index], "Each pixel has a unique output");
                            visited[index] = true;
                            ExifTransform.Point restored = ExifTransform.map(
                                    point.x, point.y, outWidth, outHeight, inverse[orientation]);
                            check(restored.x == x && restored.y == y, "Inverse round trip");
                        }
                    }
                    for (boolean present : visited) {
                        check(present, "Output raster contains every input pixel");
                    }
                }
            }
        }
    }

    private static void reflectionClassification() {
        for (int orientation = 1; orientation <= 8; orientation++) {
            ExifTransform.Point origin = ExifTransform.map(0, 0, 3, 3, orientation);
            ExifTransform.Point horizontal = ExifTransform.map(1, 0, 3, 3, orientation);
            ExifTransform.Point vertical = ExifTransform.map(0, 1, 3, 3, orientation);
            int determinant = (horizontal.x - origin.x) * (vertical.y - origin.y)
                    - (horizontal.y - origin.y) * (vertical.x - origin.x);
            check(ExifTransform.isMirrored(orientation) == (determinant < 0),
                    "Reflection agrees with raster handedness");
        }
    }

    private static void previewAndPhotoAreIndependent() {
        MirrorSettings defaults = MirrorSettings.defaults();
        check(!defaults.previewMirrored && !defaults.photoMirrored,
                "Text remains readable by default");
        for (boolean preview : Arrays.asList(false, true)) {
            for (boolean photo : Arrays.asList(false, true)) {
                MirrorSettings original = new MirrorSettings(preview, photo);
                check(original.withPreview(!preview).photoMirrored == photo,
                        "Preview change cannot flip the saved photo");
                check(original.withPhoto(!photo).previewMirrored == preview,
                        "Photo change cannot flip the preview");
                check(original.previewMirrored == preview && original.photoMirrored == photo,
                        "An in-flight capture snapshot remains immutable");
            }
        }
    }

    private static void staleCaptureCallbacksCannotUnlockNewCaptures() {
        CaptureGate gate = new CaptureGate();
        check(!gate.isBusy(), "Starts idle");
        long oldCapture = gate.tryStart();
        check(oldCapture != 0 && gate.isBusy(), "Capture owns gate");
        check(gate.tryStart() == 0, "Duplicate shutter cannot start a second capture");
        gate.cancel(); // Activity stops or switches display.
        check(!gate.isBusy(), "Stopping the activity releases the UI");
        long newCapture = gate.tryStart();
        check(newCapture != 0 && newCapture != oldCapture, "Resumed capture has a new token");
        check(!gate.finish(oldCapture), "Old success or error callback is rejected");
        check(gate.isBusy(), "Rejected callback cannot unlock the new capture");
        check(!gate.finish(0), "Invalid callback cannot unlock the capture");
        check(gate.finish(newCapture) && !gate.isBusy(), "Current callback unlocks capture");
        check(!gate.finish(newCapture), "Duplicate callback is rejected");
    }

    private static void blackPixelEvidenceDoesNotAcceptEmptyOrNearBlack() {
        RgbFrameStats black = RgbFrameStats.fromArgb(new int[]{0xff000000, 0xff000000});
        check(black.allBlack() && black.nonBlackPixels == 0 && black.pixels == 2,
                "Nonempty RGB-zero raster is black evidence");
        check(black.redMin == 0 && black.redMax == 0 && black.greenMax == 0
                && black.blueMax == 0, "All channels are inspected");
        for (int faint : new int[]{0xff010000, 0xff000100, 0xff000001}) {
            RgbFrameStats stats = RgbFrameStats.fromArgb(new int[]{0xff000000, faint});
            check(!stats.allBlack() && stats.nonBlackPixels == 1,
                    "A single faint pixel in any channel is not an all-black raster");
        }
        RgbFrameStats colors = RgbFrameStats.fromArgb(new int[]{0xff102030, 0xffabcdef});
        check(colors.redMin == 16 && colors.redMax == 171 && colors.greenMin == 32
                && colors.greenMax == 205 && colors.blueMin == 48 && colors.blueMax == 239,
                "Ranges retain per-channel evidence");
        fails(() -> RgbFrameStats.fromArgb(null));
        fails(() -> RgbFrameStats.fromArgb(new int[0]));
    }

    private static void jpegSamplingIsBoundedBeforeDecode() {
        check(JpegSamplePlan.forDimensions(4096, 2534).inSampleSize == 8,
                "Device JPEG fits a 512-edge diagnostic budget");
        check(JpegSamplePlan.forDimensions(512, 512).inSampleSize == 1,
                "A small JPEG needs no downsampling");
        check(JpegSamplePlan.forDimensions(513, 1).inSampleSize == 2,
                "Rounding cannot exceed the decode budget");
        for (int[] dimensions : new int[][]{{1, 100_000_000}, {10_000, 10_000},
                {1, 1}, {65535, 1}, {2534, 4096}}) {
            int sample = JpegSamplePlan.forDimensions(dimensions[0], dimensions[1]).inSampleSize;
            check(sample > 0 && (sample & (sample - 1)) == 0, "JPEG sampling uses powers of two");
            check(((long) dimensions[0] + sample - 1) / sample <= 512
                            && ((long) dimensions[1] + sample - 1) / sample <= 512,
                    "Ceiling dimensions fit the bitmap and pixel-array budget");
        }
        fails(() -> JpegSamplePlan.forDimensions(0, 1));
        fails(() -> JpegSamplePlan.forDimensions(1, -1));
        fails(() -> JpegSamplePlan.forDimensions(10_001, 10_000));
        fails(() -> JpegSamplePlan.forDimensions(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    private static void invalidInputsFailExplicitly() {
        fails(() -> ExifTransform.isMirrored(0));
        fails(() -> ExifTransform.isMirrored(9));
        fails(() -> ExifTransform.outputWidth(0, 2, 1));
        fails(() -> ExifTransform.outputHeight(3, -1, 1));
        fails(() -> ExifTransform.map(-1, 0, 3, 2, 1));
        fails(() -> ExifTransform.map(3, 0, 3, 2, 1));
        fails(() -> ExifTransform.map(0, 2, 3, 2, 1));
    }

    private static void fails(Runnable action) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "Invalid EXIF or raster input must be rejected");
    }

    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) {
            throw new AssertionError(message);
        }
    }
}
