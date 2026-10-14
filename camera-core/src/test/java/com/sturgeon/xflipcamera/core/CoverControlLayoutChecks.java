package com.sturgeon.xflipcamera.core;

/** Touch-region invariants, including portrait, density, insets and too-small windows. */
public final class CoverControlLayoutChecks {
    private static int assertions;

    private CoverControlLayoutChecks() { }

    public static void main(String[] args) {
        System.out.println("PASS " + run() + " cover-control layout assertions");
    }

    public static int run() {
        assertions = 0;
        CoverControlLayout device = CoverControlLayout.create(682, 422, 1, 0, 0, 0, 0);
        check(device.settings.left == 8 && device.settings.top == 8, "Settings stay at upper left");
        check(device.photo.left == 610 && device.photo.top == 8, "Photo stays at upper right");
        check(device.video.left == 610 && device.video.top == 366, "Video stays at lower right");
        for (CoverControlLayout.Box button : buttons(device)) {
            check(!(630 >= button.left && 630 < button.left + button.width
                    && 211 >= button.top && 211 < button.top + button.height),
                    "Representative native shutter point has no added control window");
        }
        int[][] frames = {{682, 422}, {422, 682}, {320, 240}, {240, 320}, {200, 180}};
        int[][] margins = {{0, 0, 0, 0}, {8, 12, 14, 8}, {0, 20, 0, 0}};
        for (int[] frame : frames) {
            for (float density : new float[]{0.75f, 1, 1.25f, 2, 3}) {
                for (int[] inset : margins) {
                    verify(Math.round(frame[0] * density), Math.round(frame[1] * density), density,
                            Math.round(inset[0] * density), Math.round(inset[1] * density),
                            Math.round(inset[2] * density), Math.round(inset[3] * density));
                }
            }
        }
        verify(152, 160, 1, 0, 0, 0, 0);
        verify(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 10, 20, 30, 40);
        fails(() -> CoverControlLayout.create(151, 160, 1, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(152, 159, 1, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(152, 160, 1, 1, 0, 0, 0));
        fails(() -> CoverControlLayout.create(152, 160, 1, 0, 1, 0, 0));
        fails(() -> CoverControlLayout.create(0, 422, 1, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(682, -1, 1, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(682, 422, 1, -1, 0, 0, 0));
        fails(() -> CoverControlLayout.create(682, 422, 1, 0, 0, Integer.MAX_VALUE, 0));
        fails(() -> CoverControlLayout.create(682, 422, 0, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(682, 422, Float.NaN, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(682, 422, Float.POSITIVE_INFINITY, 0, 0, 0, 0));
        fails(() -> CoverControlLayout.create(682, 422, Float.MAX_VALUE, 0, 0, 0, 0));
        return assertions;
    }

    private static void verify(int width, int height, float density,
            int left, int top, int right, int bottom) {
        CoverControlLayout layout = CoverControlLayout.create(width, height, density, left, top, right, bottom);
        CoverControlLayout.Box[] buttons = buttons(layout);
        int centerY = top + (height - top - bottom) / 2;
        for (CoverControlLayout.Box button : buttons) {
            check(button.width >= Math.round(64 * density)
                    && button.height >= Math.round(48 * density), "Usable touch target survives density");
            check(button.left >= left && button.top >= top, "No status bar or left cutout overlap");
            check((long) button.left + button.width <= (long) width - right
                    && (long) button.top + button.height <= (long) height - bottom,
                    "No bottom/right inset or offscreen control");
            check(centerY < button.top || centerY >= (long) button.top + button.height,
                    "No control anywhere along the native middle shutter row");
            check(!intersects(button, layout.shutterBand), "Whole central touch band remains clear");
        }
        for (int i = 0; i < buttons.length; i++) {
            for (int j = i + 1; j < buttons.length; j++) {
                check(!intersects(buttons[i], buttons[j]), "Each control owns a separate nonoverlapping window");
            }
        }
        check(layout.settings.left < layout.photo.left && layout.photo.left == layout.video.left,
                "Physical left/right arrangement survives orientation and asymmetric insets");
        check(layout.photo.top < centerY && layout.video.top > centerY,
                "Mode buttons stay on opposite sides of the shutter");
    }

    private static CoverControlLayout.Box[] buttons(CoverControlLayout layout) {
        return new CoverControlLayout.Box[]{layout.settings, layout.photo, layout.video};
    }

    private static boolean intersects(CoverControlLayout.Box a, CoverControlLayout.Box b) {
        return a.left < (long) b.left + b.width && b.left < (long) a.left + a.width
                && a.top < (long) b.top + b.height && b.top < (long) a.top + a.height;
    }

    private static void fails(Runnable action) {
        boolean failed = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        check(failed, "Reject unsafe metrics instead of covering the shutter or shrinking buttons");
    }

    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) throw new AssertionError(message);
    }
}
