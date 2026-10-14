package com.sturgeon.xflipcamera.core;

/** Three separate touch rectangles; the middle shutter band always remains uncovered. */
public final class CoverControlLayout {
    public final Box settings;
    public final Box photo;
    public final Box video;
    public final Box shutterBand;

    private CoverControlLayout(Box settings, Box photo, Box video, Box shutterBand) {
        this.settings = settings;
        this.photo = photo;
        this.video = video;
        this.shutterBand = shutterBand;
    }

    /** Coordinates are relative to the current window metrics, not a fixed device display. */
    public static CoverControlLayout create(int width, int height, float density,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (width <= 0 || height <= 0 || !Float.isFinite(density) || density <= 0
                || insetLeft < 0 || insetTop < 0 || insetRight < 0 || insetBottom < 0) {
            throw new IllegalArgumentException("Invalid window metrics");
        }
        int buttonWidth = pixels(64, density);
        int buttonHeight = pixels(48, density);
        int margin = pixels(8, density);
        long safeWidth = (long) width - insetLeft - insetRight;
        long safeHeight = (long) height - insetTop - insetBottom;
        if (safeWidth < 2L * buttonWidth + 3L * margin
                || safeHeight < 3L * buttonHeight + 2L * margin) {
            throw new IllegalArgumentException("Window too small for separate controls and shutter");
        }
        int left = insetLeft + margin;
        int right = width - insetRight - margin - buttonWidth;
        int top = insetTop + margin;
        int bottom = height - insetBottom - margin - buttonHeight;
        return new CoverControlLayout(new Box(left, top, buttonWidth, buttonHeight),
                new Box(right, top, buttonWidth, buttonHeight),
                new Box(right, bottom, buttonWidth, buttonHeight),
                new Box(insetLeft, insetTop + (int) ((safeHeight - buttonHeight) / 2),
                        (int) safeWidth, buttonHeight));
    }

    private static int pixels(int dp, float density) {
        long value = Math.round(dp * (double) density);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("Density too large");
        return (int) Math.max(1, value);
    }

    public static final class Box {
        public final int left;
        public final int top;
        public final int width;
        public final int height;

        private Box(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }
}
