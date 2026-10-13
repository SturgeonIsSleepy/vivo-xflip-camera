package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.exifinterface.media.ExifInterface;

import com.sturgeon.xflipcamera.core.PendingMediaWrite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Creates an edited copy; the source in MediaStore remains intact. Run off the UI thread. */
public final class PhotoPostProcessor {
    private PhotoPostProcessor() { }

    public static Uri flip(ContentResolver resolver, Uri original) throws IOException {
        Bitmap source = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, original),
                (decoder, info, data) -> {
                    if ((long) info.getSize().getWidth() * info.getSize().getHeight() > 25000000L) {
                        throw new IllegalArgumentException("Photo exceeds processing budget");
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
        Matrix matrix = new Matrix();
        matrix.setScale(-1, 1);
        Bitmap flipped = null;
        try {
            flipped = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, false);
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "XFlipEdited_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/XFlipCamera");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            final Bitmap encoded = flipped;
            return PendingMediaWrite.execute(new PendingMediaWrite.Storage<Uri>() {
                @Override public Uri insertPending() {
                    return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                }

                @Override public void writeAndClose(Uri destination) throws IOException {
                    try (OutputStream target = resolver.openOutputStream(destination, "w")) {
                        if (target == null || !encoded.compress(Bitmap.CompressFormat.JPEG, 95, target)) {
                            throw new IOException("JPEG encode failed");
                        }
                    }
                    try (InputStream input = resolver.openInputStream(original);
                            ParcelFileDescriptor target = resolver.openFileDescriptor(destination, "rw")) {
                        if (input == null || target == null) throw new IOException("Metadata stream unavailable");
                        ExifInterface before = new ExifInterface(input);
                        ExifInterface after = new ExifInterface(target.getFileDescriptor());
                        for (String tag : new String[]{ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                                ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_EXPOSURE_TIME,
                                ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                                ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH,
                                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                                ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                                ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP}) {
                            after.setAttribute(tag, before.getAttribute(tag));
                        }
                        after.setAttribute(ExifInterface.TAG_ORIENTATION, "1");
                        after.saveAttributes();
                    }
                }

                @Override public int publish(Uri destination) {
                    ContentValues published = new ContentValues();
                    published.put(MediaStore.Images.Media.IS_PENDING, 0);
                    return resolver.update(destination, published, null, null);
                }

                @Override public void deletePending(Uri destination) {
                    resolver.delete(destination, null, null);
                }
            });
        } finally {
            if (flipped != null && flipped != source) flipped.recycle();
            source.recycle();
        }
    }
}
