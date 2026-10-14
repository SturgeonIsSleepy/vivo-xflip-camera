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
import com.sturgeon.xflipcamera.core.PreviewPhaseState;

/** Runs in a Shizuku user service, using the real shell display-capture permission. */
public final class PreviewMirrorWorker extends Binder {
    static final String DESCRIPTOR = "com.sturgeon.xflipcamera.PreviewMirror";
    static final int START = 1, STOP = 2, STATUS = 3, PAUSE = 4;
    private volatile long generation;
    private volatile String status = "idle";
    private Thread renderThread;
    private static Class<?> displayControl;
    private volatile boolean paused;
    private volatile long frameCount, startedNs, captureNanos;
    private volatile int phase, captureMisses, unknownControls;

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
            paused = false;
            frameCount = 0;
            startedNs = System.nanoTime();
            captureNanos = 0;
            phase = captureMisses = unknownControls = 0;
            renderThread = new Thread(() -> render(target, exclude, displayId, movie, session), "cover-preview");
            renderThread.start();
        } else if (code == STOP || code == 16777115) {
            stop();
            if (code == 16777115) System.exit(0);
        } else if (code == PAUSE) {
            paused = data.readBoolean();
        } else if (code == STATUS) {
            reply.writeNoException();
            double seconds = Math.max(0.001, (System.nanoTime() - startedNs) / 1000000000.0);
            reply.writeString(status + ", phase=" + phase + ", mirrored_frames=" + frameCount
                    + ", fps=" + Math.round(frameCount / seconds * 10) / 10.0
                    + ", capture_ms=" + Math.round(captureNanos / Math.max(1, frameCount) / 100000.0) / 10.0
                    + ", capture_misses=" + captureMisses + ", unknown_controls=" + unknownControls);
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
            int screenWidth = info.getClass().getField("logicalWidth").getInt(info);
            int screenHeight = info.getClass().getField("logicalHeight").getInt(info);
            int previewWidth = Math.round(screenHeight * (movie ? 9f / 16 : 3f / 4));
            int previewLeft = (screenWidth - previewWidth) / 2;
            int controlsLeft = previewLeft + previewWidth;
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
            Object controlsBuilder = builderType.getConstructor(IBinder.class).newInstance(token);
            builderType.getMethod("setUseIdentityTransform", boolean.class).invoke(controlsBuilder, true);
            builderType.getMethod("setSourceCrop", Rect.class).invoke(controlsBuilder,
                    new Rect(controlsLeft, 0, screenWidth, screenHeight));
            builderType.getMethod("setSize", int.class, int.class).invoke(controlsBuilder,
                    Math.max(1, (screenWidth - controlsLeft) / 4), Math.max(1, screenHeight / 4));
            builderType.getMethod("setExcludeLayers", SurfaceControl[].class)
                    .invoke(controlsBuilder, new Object[]{new SurfaceControl[]{exclude}});
            Object controlsArgs = builderType.getMethod("build").invoke(controlsBuilder);
            Method capture = Class.forName("android.window.ScreenCapture").getMethod("captureDisplay",
                    Class.forName("android.window.ScreenCapture$DisplayCaptureArgs"));
            Object tasks = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
            Method getTasks = Class.forName("android.app.IActivityTaskManager")
                    .getMethod("getTasks", int.class, boolean.class, boolean.class, int.class);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            Rect source = new Rect(previewLeft, 0, previewLeft + previewWidth, screenHeight);
            Rect destination = new Rect(0, 0, target.isValid() ? previewWidth : 0, screenHeight);
            int timerWidth = Math.min(previewWidth - 4, Math.round(screenHeight * 0.44f));
            Rect timer = new Rect(screenWidth / 2 - timerWidth / 2, Math.round(screenHeight * 0.06f),
                    screenWidth / 2 + timerWidth / 2, Math.round(screenHeight * 0.19f));
            Rect timerTarget = new Rect(timer.left - previewLeft, timer.top, timer.right - previewLeft, timer.bottom);
            PreviewPhaseState state = new PreviewPhaseState();
            long nextControlsNs = 0, missingSinceNs = 0;
            int lastDrawPhase = -1;
            Method asBitmap = null, hardwareBuffer = null;
            while (session == generation && !Thread.currentThread().isInterrupted() && target.isValid()) {
                long tickNs = System.nanoTime();
                if (paused) { Thread.sleep(33); continue; }
                List<ActivityManager.RunningTaskInfo> foreground = (List<ActivityManager.RunningTaskInfo>)
                        getTasks.invoke(tasks, 1, false, false, displayId);
                boolean cameraTop = !foreground.isEmpty() && foreground.get(0).topActivity != null
                        && "com.android.camera".equals(foreground.get(0).topActivity.getPackageName());
                if (!cameraTop) {
                    if (missingSinceNs == 0) missingSinceNs = tickNs;
                    if (tickNs - missingSinceNs > 250000000L) {
                        if (lastDrawPhase != -2) clear(target);
                        state.reset();
                        phase = 0;
                        lastDrawPhase = -2;
                    }
                    status = "waiting_camera";
                    Thread.sleep(33); continue;
                }
                missingSinceNs = 0;
                if (tickNs >= nextControlsNs) {
                    Object controlsBuffer = capture.invoke(null, controlsArgs);
                    if (controlsBuffer != null) {
                        if (asBitmap == null) {
                            asBitmap = controlsBuffer.getClass().getMethod("asBitmap");
                            hardwareBuffer = controlsBuffer.getClass().getMethod("getHardwareBuffer");
                        }
                        Bitmap controlsHardware = (Bitmap) asBitmap.invoke(controlsBuffer);
                        if (controlsHardware != null) {
                            Bitmap pixels = null;
                            try {
                                pixels = controlsHardware.copy(Bitmap.Config.ARGB_8888, false);
                                int controls = previewControls(pixels, movie);
                                if (controls == 0 && settingsControls(pixels)) controls = PreviewPhaseState.SETTINGS;
                                if (controls == 0) unknownControls++;
                                state.sample(controls, controls == 0 && reviewControls(pixels));
                                phase = state.phase();
                            } finally {
                                if (pixels != null) pixels.recycle();
                                controlsHardware.recycle();
                            }
                        }
                        android.hardware.HardwareBuffer buffer = (android.hardware.HardwareBuffer) hardwareBuffer.invoke(controlsBuffer);
                        if (buffer != null) buffer.close();
                    } else captureMisses++;
                    nextControlsNs = tickNs + 133333333L;
                }
                if (phase == PreviewPhaseState.WAITING || phase == PreviewPhaseState.REVIEW || phase == PreviewPhaseState.SETTINGS) {
                    if (phase != lastDrawPhase) {
                        if (phase != PreviewPhaseState.WAITING) clear(target);
                        else fillBlack(target);
                    }
                    lastDrawPhase = phase;
                    status = phase == PreviewPhaseState.REVIEW ? "review"
                            : phase == PreviewPhaseState.SETTINGS ? "native_settings" : "waiting_controls";
                    Thread.sleep(16); continue;
                }
                long captureStart = System.nanoTime();
                Object buffer = capture.invoke(null, args);
                captureNanos += System.nanoTime() - captureStart;
                if (buffer == null) { captureMisses++; Thread.sleep(16); continue; }
                Bitmap hardware = (Bitmap) asBitmap.invoke(buffer);
                try {
                    if (hardware != null && session == generation) {
                        Canvas canvas = target.lockHardwareCanvas();
                        try {
                            destination.set(0, 0, canvas.getWidth(), canvas.getHeight());
                            canvas.save();
                            canvas.scale(-1, 1, canvas.getWidth() / 2f, canvas.getHeight() / 2f);
                            canvas.drawBitmap(hardware, source, destination, paint);
                            canvas.restore();
                            if (phase == PreviewPhaseState.RECORDING) canvas.drawBitmap(hardware, timer, timerTarget, paint);
                        } finally { target.unlockCanvasAndPost(canvas); }
                        frameCount++;
                        lastDrawPhase = phase;
                        status = "gpu_preview, uid=" + Process.myUid() + ", density=" + density;
                    }
                } finally {
                    if (hardware != null) hardware.recycle();
                    android.hardware.HardwareBuffer handle = (android.hardware.HardwareBuffer) hardwareBuffer.invoke(buffer);
                    if (handle != null) handle.close();
                }
                long remaining = 33333333L - (System.nanoTime() - tickNs);
                if (remaining > 0) Thread.sleep(remaining / 1000000L, (int) (remaining % 1000000L));
            }
        } catch (Throwable failure) {
            Throwable cause = failure;
            while (cause instanceof java.lang.reflect.InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            status = "mirror_error=" + cause;
        } finally {
            if (session == generation) try { clear(target); } catch (RuntimeException ignored) { }
            target.release();
            exclude.release();
        }
    }

    private static int previewControls(Bitmap frame, boolean movie) {
        int height = frame.getHeight();
        int right = 0;
        int minX = frame.getWidth(), minY = height, maxX = -1, maxY = -1, hits = 0;
        for (int y = height / 4; y < height * 3 / 4; y++) {
            for (int x = right; x < frame.getWidth(); x++) {
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
        if (hits < 8 || width <= 0 || markerHeight <= 0
                || width < markerHeight * 0.75f || width > markerHeight * 1.25f) return 0;
        return movie && markerHeight < height * 0.11f ? 2 : 1;
    }

    private static boolean reviewControls(Bitmap frame) {
        int cx = Math.round(frame.getWidth() * 0.60f), cy = frame.getHeight() / 2;
        int radius = Math.max(2, Math.round(frame.getHeight() * 0.065f));
        int white = 0, count = 0;
        for (int y = cy - radius; y <= cy + radius; y++) for (int x = cx - radius; x <= cx + radius; x++) {
            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > radius * radius) continue;
            int color = frame.getPixel(x, y);
            if (Color.red(color) > 200 && Color.green(color) > 200 && Color.blue(color) > 200) white++;
            count++;
        }
        return white > count * 0.65f;
    }

    private static boolean settingsControls(Bitmap frame) {
        int minX = frame.getWidth(), minY = frame.getHeight(), maxX = -1, maxY = -1, hits = 0;
        for (int y = 0; y < frame.getHeight() / 4; y++) for (int x = 0; x < frame.getWidth(); x++) {
            int color = frame.getPixel(x, y);
            if (Color.red(color) > 210 && Color.green(color) > 210 && Color.blue(color) > 210) {
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y); hits++;
            }
        }
        int width = maxX - minX + 1, height = maxY - minY + 1;
        return hits >= 10 && width >= 6 && height >= 6 && width > height * 0.8f && width < height * 1.25f;
    }

    private static void fillBlack(Surface surface) {
        Canvas canvas = surface.lockHardwareCanvas();
        try { canvas.drawColor(Color.BLACK); } finally { surface.unlockCanvasAndPost(canvas); }
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
