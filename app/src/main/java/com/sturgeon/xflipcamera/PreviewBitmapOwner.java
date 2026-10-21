package com.sturgeon.xflipcamera;

import android.graphics.Bitmap;
import android.os.Looper;
import android.widget.ImageView;
import java.util.Objects;

/** Main-thread ownership for the one preview Bitmap currently retained by an ImageView. */
final class PreviewBitmapOwner {
    private Bitmap current;

    void show(ImageView view, Bitmap next) {
        requireMainThread();
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(next, "next");
        if (next.isRecycled()) throw new IllegalArgumentException("Cannot display a recycled preview");
        Bitmap previous = current;
        current = next;
        view.setImageBitmap(next);
        recycleUnlessCurrent(previous);
    }

    /** Release a worker result that lost the activity/source ownership check. */
    void discard(Bitmap stale) {
        requireMainThread();
        recycleUnlessCurrent(stale);
    }

    void close(ImageView view) {
        requireMainThread();
        Objects.requireNonNull(view, "view");
        view.setImageDrawable(null);
        Bitmap previous = current;
        current = null;
        recycle(previous);
    }

    private void recycleUnlessCurrent(Bitmap candidate) {
        if (candidate != current) recycle(candidate);
    }

    private static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Preview Bitmap ownership belongs to the main thread");
        }
    }
}
