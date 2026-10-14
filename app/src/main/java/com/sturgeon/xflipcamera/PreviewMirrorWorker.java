package com.sturgeon.xflipcamera;

import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.List;

/** Runs in a Shizuku user service, using the real shell display-capture permission. */
public final class PreviewMirrorWorker extends Binder {
    static final String DESCRIPTOR = "com.sturgeon.xflipcamera.PreviewMirror";
    static final int START = 1, STOP = 2, STATUS = 3;
    private volatile long generation;
    private volatile String status = "idle";
    private Thread renderThread;
    private static Class<?> displayControl;

    public PreviewMirrorWorker() { attachInterface(null, DESCRIPTOR); }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws android.os.RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
        data.enforceInterface(DESCRIPTOR);
        if (code == START) {
            stop();
            Surface target = data.readTypedObject(Surface.CREATOR);
            SurfaceControl exclude = data.readTypedObject(SurfaceControl.CREATOR);
            int displayId = data.readInt();
            boolean movie = data.readBoolean();
            long session = ++generation;
            renderThread = new Thread(() -> render(target, exclude, displayId, movie, session), "cover-preview");
            renderThread.start();
        } else if (code == STOP || code == 16777115) {
            stop();
            if (code == 16777115) System.exit(0);
        } else if (code == STATUS) {
            reply.writeNoException();
            reply.writeString(status);
            return true;
        } else return super.onTransact(code, data, reply, flags);
        reply.writeNoException();
        return true;
    }

    private synchronized void stop() {
        generation++;
        if (renderThread != null) {
            renderThread.interrupt();
            try { renderThread.join(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            renderThread = null;
        }
    }

    @SuppressWarnings("unchecked")
    private void render(Surface target, SurfaceControl exclude, int displayId, boolean movie, long session) {
        long frames = 0;
        try {
            if (Process.myUid() != 2000 && Process.myUid() != 0) {
                throw new SecurityException("Preview capture requires the actual shell or root UID");
            }
            Class<?> services = Class.forName("android.os.ServiceManager");
            IBinder displayBinder = (IBinder) services.getMethod("getService", String.class).invoke(null, "display");
            Object displays = Class.forName("android.hardware.display.IDisplayManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, displayBinder);
            Object info = Class.forName("android.hardware.display.IDisplayManager")
                    .getMethod("getDisplayInfo", int.class).invoke(displays, displayId);
            Object address = info.getClass().getField("address").get(info);
            long physicalId = (long) address.getClass().getMethod("getPhysicalDisplayId").invoke(address);
            int density = info.getClass().getField("logicalDensityDpi").getInt(info);
            IBinder token = displayToken(physicalId);
            Class<?> builderType = Class.forName("android.window.ScreenCapture$DisplayCaptureArgs$Builder");
            Object builder = builderType.getConstructor(IBinder.class).newInstance(token);
            builderType.getMethod("setUseIdentityTransform", boolean.class).invoke(builder, true);
            builderType.getMethod("setSize", int.class, int.class).invoke(builder,
                    info.getClass().getField("logicalWidth").getInt(info),
                    info.getClass().getField("logicalHeight").getInt(info));
            builderType.getMethod("setExcludeLayers", SurfaceControl[].class)
                    .invoke(builder, new Object[]{new SurfaceControl[]{exclude}});
            Object args = builderType.getMethod("build").invoke(builder);
            Method capture = Class.forName("android.window.ScreenCapture").getMethod("captureDisplay",
                    Class.forName("android.window.ScreenCapture$DisplayCaptureArgs"));
            Object tasks = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Method getTasks = Class.forName("android.app.IActivityTaskManager")
                    .getMethod("getTasks", int.class, boolean.class, boolean.class, int.class);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            while (session == generation && !Thread.currentThread().isInterrupted() && target.isValid()) {
                List<ActivityManager.RunningTaskInfo> foreground = (List<ActivityManager.RunningTaskInfo>)
                        getTasks.invoke(tasks, 1, false, false, displayId);
                boolean cameraTop = !foreground.isEmpty() && foreground.get(0).topActivity != null
                        && "com.android.camera".equals(foreground.get(0).topActivity.getPackageName());
                if (!cameraTop) {
                    status = "waiting_camera, mirrored_frames=" + frames;
                    clear(target); Thread.sleep(100); continue;
                }
                Object buffer = capture.invoke(null, args);
                if (buffer == null) throw new IllegalStateException("Display capture returned null");
                Bitmap hardware = (Bitmap) buffer.getClass().getMethod("asBitmap").invoke(buffer);
                Bitmap pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                hardware.recycle();
                ((android.hardware.HardwareBuffer) buffer.getClass().getMethod("getHardwareBuffer").invoke(buffer)).close();
                try {
                    int controls = previewControls(pixels, movie);
                    if (controls == 0) {
                        status = "waiting_preview_controls, captured_px=" + pixels.getWidth() + "x"
                                + pixels.getHeight() + ", density=" + density + ", mirrored_frames=" + frames;
                        clear(target);
                    }
                    else {
                        int width = Math.round(pixels.getHeight() * (movie ? 9f / 16 : 3f / 4));
                        int left = (pixels.getWidth() - width) / 2;
                        Canvas canvas = target.lockHardwareCanvas();
                        try {
                            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                            canvas.save();
                            canvas.scale(-1, 1, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
                            canvas.drawBitmap(pixels, new Rect(left, 0, left + width, pixels.getHeight()),
                                    new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), paint);
                            canvas.restore();
                            if (controls == 2) {
                                int timerWidth = Math.min(width - 4, Math.round(pixels.getHeight() * 0.44f));
                                Rect timer = new Rect(pixels.getWidth() / 2 - timerWidth / 2,
                                        Math.round(pixels.getHeight() * 0.06f),
                                        pixels.getWidth() / 2 + timerWidth / 2,
                                        Math.round(pixels.getHeight() * 0.19f));
                                Rect timerTarget = new Rect(timer.left - left, timer.top,
                                        timer.right - left, timer.bottom);
                                canvas.drawBitmap(pixels, timer, timerTarget, paint);
                            }
                        } finally { target.unlockCanvasAndPost(canvas); }
                        frames++;
                        status = "uid=" + Process.myUid() + ", mirrored_frames=" + frames;
                    }
                } finally { pixels.recycle(); }
                Thread.sleep(67);
            }
        } catch (Throwable failure) {
            Throwable cause = failure;
            while (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            status = "mirror_error=" + cause;
        } finally {
            try { clear(target); } catch (RuntimeException ignored) { }
            target.release();
            exclude.release();
        }
    }

    private static int previewControls(Bitmap frame, boolean movie) {
        int height = frame.getHeight();
        int previewWidth = Math.round(height * (movie ? 9f / 16 : 3f / 4));
        int right = (frame.getWidth() + previewWidth) / 2;
        int minX = frame.getWidth(), minY = height, maxX = -1, maxY = -1, hits = 0;
        for (int y = height / 4; y < height * 3 / 4; y += 2) {
            for (int x = right; x < frame.getWidth(); x += 2) {
                int color = frame.getPixel(x, y);
                boolean marker = movie ? Color.red(color) > 180 && Color.green(color) < 140
                        && Color.blue(color) < 140 : Color.red(color) > 180
                        && Color.green(color) > 160 && Color.blue(color) < 140;
                if (marker) {
                    hits++;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        }
        int width = maxX - minX + 1, markerHeight = maxY - minY + 1;
        if (hits < 20 || width <= 0 || markerHeight <= 0
                || width < markerHeight * 0.75f || width > markerHeight * 1.25f) return 0;
        return movie && markerHeight < height / 10 ? 2 : 1;
    }

    private static void clear(Surface surface) {
        if (!surface.isValid()) return;
        Canvas canvas = surface.lockHardwareCanvas();
        try { canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); }
        finally { surface.unlockCanvasAndPost(canvas); }
    }

    /** Android 14 loader adapted from scrcpy v4.1 DisplayControl (Apache-2.0); see third-party notices. */
    @android.annotation.SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static synchronized IBinder displayToken(long physicalId) throws Exception {
        if (displayControl == null) {
            Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
            ClassLoader loader = (ClassLoader) factory.getDeclaredMethod("createClassLoader", String.class,
                    String.class, String.class, ClassLoader.class, int.class, boolean.class, String.class)
                    .invoke(null, android.system.Os.getenv("SYSTEMSERVERCLASSPATH"), null, null,
                            ClassLoader.getSystemClassLoader(), 0, true, null);
            Class<?> control = loader.loadClass("com.android.server.display.DisplayControl");
            Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            load.setAccessible(true);
            load.invoke(Runtime.getRuntime(), control, "android_servers");
            displayControl = control;
        }
        return (IBinder) displayControl.getMethod("getPhysicalDisplayToken", long.class).invoke(null, physicalId);
    }
}
