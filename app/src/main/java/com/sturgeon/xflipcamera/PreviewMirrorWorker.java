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
    private volatile boolean running;
    private volatile String status = "idle";
    private Thread renderThread;

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
            running = true;
            renderThread = new Thread(() -> render(target, exclude, displayId, movie), "cover-preview");
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
        running = false;
        if (renderThread != null) {
            renderThread.interrupt();
            try { renderThread.join(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            renderThread = null;
        }
    }

    @SuppressWarnings("unchecked")
    private void render(Surface target, SurfaceControl exclude, int displayId, boolean movie) {
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
            builderType.getMethod("setExcludeLayers", SurfaceControl[].class)
                    .invoke(builder, new Object[]{new SurfaceControl[]{exclude}});
            Object args = builderType.getMethod("build").invoke(builder);
            Method capture = Class.forName("android.window.ScreenCapture").getMethod("captureDisplay",
                    Class.forName("android.window.ScreenCapture$DisplayCaptureArgs"));
            Object tasks = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Method getTasks = Class.forName("android.app.IActivityTaskManager")
                    .getMethod("getTasks", int.class, boolean.class, boolean.class, int.class);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            while (running && target.isValid()) {
                List<ActivityManager.RunningTaskInfo> foreground = (List<ActivityManager.RunningTaskInfo>)
                        getTasks.invoke(tasks, 1, false, false, displayId);
                boolean cameraTop = !foreground.isEmpty() && foreground.get(0).topActivity != null
                        && "com.android.camera".equals(foreground.get(0).topActivity.getPackageName());
                if (!cameraTop) { clear(target); Thread.sleep(100); continue; }
                Object buffer = capture.invoke(null, args);
                if (buffer == null) throw new IllegalStateException("Display capture returned null");
                Bitmap hardware = (Bitmap) buffer.getClass().getMethod("asBitmap").invoke(buffer);
                Bitmap pixels = hardware.copy(Bitmap.Config.ARGB_8888, false);
                hardware.recycle();
                ((android.hardware.HardwareBuffer) buffer.getClass().getMethod("getHardwareBuffer").invoke(buffer)).close();
                try {
                    if (!previewControls(pixels, density, movie)) { clear(target); }
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
                        } finally { target.unlockCanvasAndPost(canvas); }
                        frames++;
                        status = "uid=" + Process.myUid() + ", mirrored_frames=" + frames;
                    }
                } finally { pixels.recycle(); }
                Thread.sleep(67);
            }
        } catch (Throwable failure) {
            status = "mirror_error=" + failure;
        } finally {
            running = false;
            try { clear(target); } catch (RuntimeException ignored) { }
            target.release();
            exclude.release();
        }
    }

    private static boolean previewControls(Bitmap frame, int density, boolean movie) {
        float dp = density / 160f;
        int cx = Math.round(frame.getWidth() - 72 * dp), cy = frame.getHeight() / 2;
        int hits = 0;
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            int radius = Math.round((movie ? 15 : 37) * dp);
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            if (x < 0 || y < 0 || x >= frame.getWidth() || y >= frame.getHeight()) return false;
            int color = frame.getPixel(x, y);
            boolean marker = movie ? Color.red(color) > 180 && Color.green(color) < 140
                    : Color.red(color) > 180 && Color.green(color) > 160 && Color.blue(color) < 140;
            if (marker) hits++;
        }
        return hits >= 5;
    }

    private static void clear(Surface surface) {
        if (!surface.isValid()) return;
        Canvas canvas = surface.lockHardwareCanvas();
        try { canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR); }
        finally { surface.unlockCanvasAndPost(canvas); }
    }

    /** Android 14 loader adapted from scrcpy v4.1 DisplayControl (Apache-2.0); see third-party notices. */
    @android.annotation.SuppressLint({"BlockedPrivateApi", "PrivateApi"})
    private static IBinder displayToken(long physicalId) throws Exception {
        Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
        ClassLoader loader = (ClassLoader) factory.getDeclaredMethod("createClassLoader", String.class,
                String.class, String.class, ClassLoader.class, int.class, boolean.class, String.class)
                .invoke(null, android.system.Os.getenv("SYSTEMSERVERCLASSPATH"), null, null,
                        ClassLoader.getSystemClassLoader(), 0, true, null);
        Class<?> control = loader.loadClass("com.android.server.display.DisplayControl");
        Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
        load.setAccessible(true);
        load.invoke(Runtime.getRuntime(), control, "android_servers");
        return (IBinder) control.getMethod("getPhysicalDisplayToken", long.class).invoke(null, physicalId);
    }
}
