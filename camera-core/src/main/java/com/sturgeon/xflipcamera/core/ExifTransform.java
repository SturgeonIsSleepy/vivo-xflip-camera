package com.sturgeon.xflipcamera.core;

/**
 * EXIF orientation maps from encoded raster coordinates to displayed coordinates.
 * This class is an inspection/test oracle, not an additional CameraX rotation.
 */
public final class ExifTransform {
    private ExifTransform() {}

    public static boolean isMirrored(int orientation) {
        validate(orientation);
        return orientation == 2 || orientation == 4
                || orientation == 5 || orientation == 7;
    }

    public static int outputWidth(int width, int height, int orientation) {
        validateDimensions(width, height);
        validate(orientation);
        return orientation >= 5 ? height : width;
    }

    public static int outputHeight(int width, int height, int orientation) {
        validateDimensions(width, height);
        validate(orientation);
        return orientation >= 5 ? width : height;
    }

    public static Point map(int x, int y, int width, int height, int orientation) {
        validateDimensions(width, height);
        validate(orientation);
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IllegalArgumentException("Pixel is outside the encoded raster");
        }
        switch (orientation) {
            case 1: return new Point(x, y);
            case 2: return new Point(width - 1 - x, y);
            case 3: return new Point(width - 1 - x, height - 1 - y);
            case 4: return new Point(x, height - 1 - y);
            case 5: return new Point(y, x);
            case 6: return new Point(height - 1 - y, x);
            case 7: return new Point(height - 1 - y, width - 1 - x);
            case 8: return new Point(y, width - 1 - x);
            default: throw new AssertionError("Validated orientation");
        }
    }

    private static void validate(int orientation) {
        if (orientation < 1 || orientation > 8) {
            throw new IllegalArgumentException("EXIF orientation must be 1..8");
        }
    }

    private static void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Raster dimensions must be positive");
        }
    }

    public static final class Point {
        public final int x;
        public final int y;

        private Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
