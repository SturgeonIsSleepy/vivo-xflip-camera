package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;

import com.sturgeon.xflipcamera.core.PendingMediaWrite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.UUID;

/** User text on an edited SDR JPEG copy; not a vendor watermark or Google imaging algorithm. */
public final class WatermarkProcessor {
    private static final long MAX_PIXELS = 20_000_000L;
    private static final String[] CAPTURE_TAGS = {
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP
    };

    private WatermarkProcessor() { }

    /** Original content URI is opened read-only. The caller owns lifecycle/cancellation and UI. */
    @WorkerThread
    public static Uri mark(ContentResolver resolver, Uri original, String caption) throws IOException {
        Objects.requireNonNull(resolver, "resolver");
        if (original == null || !ContentResolver.SCHEME_CONTENT.equals(original.getScheme())) {
            throw new IllegalArgumentException("A readable content URI is required");
        }
        String text = checkedCaption(caption);
        String[] metadata = new String[CAPTURE_TAGS.length];
        try (InputStream input = resolver.openInputStream(original)) {
            if (input == null) throw new IOException("Source metadata unavailable");
            ExifInterface before = new ExifInterface(input);
            for (int i = 0; i < metadata.length; i++) metadata[i] = before.getAttribute(CAPTURE_TAGS[i]);
        }
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, original),
                (decoder, info, source) -> {
                    int width = info.getSize().getWidth(), height = info.getSize().getHeight();
                    if (!"image/jpeg".equals(info.getMimeType()) || width < 128 || height < 128
                            || (long) width * height > MAX_PIXELS) {
                        throw new IllegalArgumentException("Choose a JPEG within the watermark pixel budget");
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMutableRequired(true);
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                });
        try {
            if (Build.VERSION.SDK_INT >= 34 && bitmap.hasGainmap()) {
                throw new IllegalArgumentException("HDR gain-map JPEG editing is not supported");
            }
            drawText(bitmap, text);
            ContentValues pending = new ContentValues();
            pending.put(MediaStore.Images.Media.DISPLAY_NAME, "XFlipText_" + UUID.randomUUID() + ".jpg");
            pending.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            pending.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XFlipCamera");
            pending.put(MediaStore.Images.Media.IS_PENDING, 1);
            return PendingMediaWrite.execute(new PendingMediaWrite.Storage<Uri>() {
                @Override public Uri insertPending() {
                    return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending);
                }

                @Override public void writeAndClose(Uri output) throws IOException {
                    try (OutputStream stream = resolver.openOutputStream(output, "w")) {
                        if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                            throw new IOException("Watermarked JPEG encoding failed");
                        }
                    }
                    try (ParcelFileDescriptor target = resolver.openFileDescriptor(output, "rw")) {
                        if (target == null) throw new IOException("Output metadata unavailable");
                        ExifInterface after = new ExifInterface(target.getFileDescriptor());
                        for (int i = 0; i < metadata.length; i++) {
                            if (metadata[i] != null) after.setAttribute(CAPTURE_TAGS[i], metadata[i]);
                        }
                        // ImageDecoder has applied the input EXIF transform to the pixels.
                        after.setAttribute(ExifInterface.TAG_ORIENTATION, "1");
                        after.setAttribute(ExifInterface.TAG_SOFTWARE, "XFlip Camera (user text copy)");
                        after.saveAttributes();
                    }
                }

                @Override public int publish(Uri output) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    return resolver.update(output, ready, null, null);
                }

                @Override public void deletePending(Uri output) {
                    resolver.delete(output, null, null);
                }
            });
        } finally {
            bitmap.recycle();
        }
    }

    private static String checkedCaption(String caption) {
        if (caption == null) throw new IllegalArgumentException("Missing caption");
        String text = caption.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 80
                || text.codePoints().anyMatch(point -> Character.isISOControl(point)
                        || Character.getType(point) == Character.FORMAT
                        || Character.getType(point) == Character.SURROGATE)) {
            throw new IllegalArgumentException("Use 1 to 80 plain single-line characters");
        }
        return text;
    }

    private static void drawText(Bitmap bitmap, String caption) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int padding = Math.max(6, Math.round(Math.min(width, height) * 0.015f));
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(Color.WHITE);
        text.setTextSize(Math.max(12, Math.min(width, height) * 0.035f));
        float available = width - 2f * padding;
        float measured = text.measureText(caption);
        if (measured > available) text.setTextSize(text.getTextSize() * available / measured);
        if (text.getTextSize() < 12) throw new IllegalArgumentException("Caption does not fit legibly");
        Paint.FontMetrics metrics = text.getFontMetrics();
        int footer = (int) Math.ceil(metrics.bottom - metrics.top) + 2 * padding;
        if (footer > height / 4) throw new IllegalArgumentException("Watermark would cover too much image");
        Paint background = new Paint();
        background.setColor(Color.BLACK);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawRect(0, height - footer, width, height, background);
        canvas.drawText(caption, padding, height - padding - metrics.bottom, text);
    }
}
