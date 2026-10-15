package com.sturgeon.xflipcamera;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;

import com.sturgeon.xflipcamera.core.CoverControlLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Native side controls. The owner supplies navigation and must detach outside live preview. */
public final class CoverControls {
    public interface Callbacks {
        void onMode(boolean video);
        void onSettings();
    }

    private final Context context;
    private final WindowManager windows;
    private final Callbacks callbacks;
    private final Button settings;
    private final Button photo;
    private final Button video;
    private final List<Button> added = new ArrayList<>();
    private boolean movie;
    private int photoProgress;
    private boolean enabled = true;
    private boolean attached;
    private boolean registered;
    private final ComponentCallbacks configuration = new ComponentCallbacks() {
        @Override public void onConfigurationChanged(Configuration changed) {
            if (!attached) return;
            try {
                relayout();
            } catch (RuntimeException failure) {
                Log.w("XFlipCoverControls", "Controls detached after window change", removeWindows(failure));
            }
        }
        @Override public void onLowMemory() { }
    };

    /** Pass an Activity or a context explicitly associated with the intended Display. */
    public CoverControls(Context displayContext, Callbacks callbacks) {
        requireMainThread();
        Objects.requireNonNull(displayContext, "displayContext");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        context = displayContext.createWindowContext(displayContext.getDisplay(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        windows = Objects.requireNonNull(context.getSystemService(WindowManager.class), "WindowManager");
        settings = button("设置");
        photo = button("拍照");
        video = button("录像");
        settings.setOnClickListener(view -> {
            if (attached && enabled) this.callbacks.onSettings();
        });
        photo.setOnClickListener(view -> requestMode(false));
        video.setOnClickListener(view -> requestMode(true));
        refresh();
    }

    /** Idempotent. Missing authorization/space throws; partial additions are rolled back. */
    public void attach() {
        requireMainThread();
        if (attached) return;
        if (!added.isEmpty() || registered) {
            throw new IllegalStateException("Previous window cleanup must finish before attaching");
        }
        if (!Settings.canDrawOverlays(context)) throw new SecurityException("Overlay permission required");
        try {
            WindowMetrics metrics = windows.getCurrentWindowMetrics();
            CoverControlLayout layout = layout(metrics);
            add(settings, metrics, layout.settings, "XFlip cover settings");
            add(photo, metrics, layout.photo, "XFlip cover photo");
            add(video, metrics, layout.video, "XFlip cover video");
            context.registerComponentCallbacks(configuration);
            registered = true;
            attached = true;
        } catch (RuntimeException failure) {
            throw removeWindows(failure);
        }
    }

    /** All three windows are attempted even if one removal fails; detach can be retried. */
    public void detach() {
        requireMainThread();
        RuntimeException failure = removeWindows(null);
        if (failure != null) throw failure;
    }

    /** Commit only an actually accepted mode; clicks themselves never pretend navigation succeeded. */
    public void setMode(boolean videoMode) {
        requireMainThread();
        movie = videoMode;
        refresh();
    }

    public void setEnabled(boolean value) {
        requireMainThread();
        enabled = value;
        refresh();
    }

    public void setPhotoProgress(int value) {
        requireMainThread();
        if (photoProgress == value) return;
        photoProgress = value;
        refresh();
    }

    public boolean isAttached() {
        requireMainThread();
        return attached;
    }

    private void requestMode(boolean requestedMovie) {
        if (attached && enabled && requestedMovie != movie) callbacks.onMode(requestedMovie);
    }

    private Button button(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setTextColor(Color.WHITE);
        button.setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        return button;
    }

    private void refresh() {
        for (Button button : new Button[]{settings, photo, video}) {
            button.setEnabled(enabled);
            button.setAlpha(enabled ? 1f : 0.55f);
        }
        mark(photo, !movie, photoProgress > 0 ? "拍照 " + photoProgress + "/3" : "拍照");
        mark(video, movie, "录像");
        settings.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(42, 42, 42)));
        settings.setContentDescription("相机设置");
    }

    private void mark(Button button, boolean selected, String label) {
        button.setSelected(selected);
        button.setText(label);
        button.setBackgroundTintList(ColorStateList.valueOf(selected
                ? Color.rgb(36, 92, 145) : Color.rgb(42, 42, 42)));
        button.setContentDescription(label + (selected ? "，当前模式" : "，切换模式"));
        button.setStateDescription(selected ? "已选中" : "未选中");
    }

    private CoverControlLayout layout(WindowMetrics metrics) {
        Rect bounds = metrics.getBounds();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        return CoverControlLayout.create(bounds.width(), bounds.height(),
                context.getResources().getDisplayMetrics().density,
                insets.left, insets.top, insets.right, insets.bottom);
    }

    private WindowManager.LayoutParams parameters(WindowMetrics metrics,
            CoverControlLayout.Box box, CharSequence title) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(box.width, box.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = metrics.getBounds().left + box.left;
        params.y = metrics.getBounds().top + box.top;
        params.setFitInsetsTypes(0); // Geometry already excludes bars and cutouts once.
        params.setTitle(title);
        return params;
    }

    private void add(Button button, WindowMetrics metrics, CoverControlLayout.Box box, String title) {
        windows.addView(button, parameters(metrics, box, title));
        added.add(button);
    }

    private void relayout() {
        WindowMetrics metrics = windows.getCurrentWindowMetrics();
        CoverControlLayout layout = layout(metrics);
        windows.updateViewLayout(settings, parameters(metrics, layout.settings, "XFlip cover settings"));
        windows.updateViewLayout(photo, parameters(metrics, layout.photo, "XFlip cover photo"));
        windows.updateViewLayout(video, parameters(metrics, layout.video, "XFlip cover video"));
    }

    private RuntimeException removeWindows(RuntimeException first) {
        attached = false;
        if (registered) {
            try {
                context.unregisterComponentCallbacks(configuration);
                registered = false;
            } catch (RuntimeException failure) {
                first = combine(first, failure);
            }
        }
        for (int index = added.size() - 1; index >= 0; index--) {
            View view = added.get(index);
            try {
                windows.removeViewImmediate(view);
                added.remove(index);
            } catch (RuntimeException failure) {
                if (failure instanceof IllegalArgumentException && view.getParent() == null) {
                    added.remove(index); // WindowManager already removed this view.
                } else first = combine(first, failure);
            }
        }
        return first;
    }

    private static RuntimeException combine(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("CoverControls must be used on the main thread");
        }
    }
}
