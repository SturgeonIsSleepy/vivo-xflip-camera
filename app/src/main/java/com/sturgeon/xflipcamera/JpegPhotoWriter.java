package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;
import com.sturgeon.xflipcamera.core.PendingMediaWrite;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Publishes only a newly owned processed JPEG; original metadata is read-only. */
final class JpegPhotoWriter {
    private JpegPhotoWriter() { }
    public static Uri write(ContentResolver resolver, Uri reference, Bitmap bitmap, String prefix, String description,
            BooleanSupplier cancelled) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,prefix + UUID.randomUUID() + ".jpg");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/XFlipCamera");
        values.put(MediaStore.Images.Media.IS_PENDING,1);
        return PendingMediaWrite.execute(new PendingMediaWrite.Storage<Uri>() {
            @Override public Uri insertPending() { return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values); }
            @Override public void writeAndClose(Uri destination) throws IOException {
                check(cancelled);
                try (OutputStream stream = resolver.openOutputStream(destination,"w")) {
                    if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG,97,stream)) throw new IOException("合成 JPEG 保存失败");
                }
                try (InputStream stream = resolver.openInputStream(reference); ParcelFileDescriptor file = resolver.openFileDescriptor(destination,"rw")) {
                    if (stream == null || file == null) throw new IOException("合成元数据不可写");
                    ExifInterface before = new ExifInterface(stream), after = new ExifInterface(file.getFileDescriptor());
                    for (String tag : new String[]{ExifInterface.TAG_MAKE,ExifInterface.TAG_MODEL,ExifInterface.TAG_DATETIME_ORIGINAL,
                            ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_F_NUMBER,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                            ExifInterface.TAG_FOCAL_LENGTH,ExifInterface.TAG_FLASH,ExifInterface.TAG_GPS_LATITUDE,
                            ExifInterface.TAG_GPS_LATITUDE_REF,ExifInterface.TAG_GPS_LONGITUDE,ExifInterface.TAG_GPS_LONGITUDE_REF,
                            ExifInterface.TAG_GPS_ALTITUDE,ExifInterface.TAG_GPS_ALTITUDE_REF,ExifInterface.TAG_GPS_DATESTAMP,
                            ExifInterface.TAG_GPS_TIMESTAMP}) after.setAttribute(tag,before.getAttribute(tag));
                    after.setAttribute(ExifInterface.TAG_ORIENTATION,"1");
                    after.setAttribute(ExifInterface.TAG_SOFTWARE,"XFlip Photo Processing (OpenCV 4.12)");
                    after.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION,description);
                    after.saveAttributes();
                }
            }
            @Override public int publish(Uri destination) throws IOException {
                check(cancelled); ContentValues done = new ContentValues();done.put(MediaStore.Images.Media.IS_PENDING,0);
                return resolver.update(destination,done,null,null);
            }
            @Override public void deletePending(Uri destination) { resolver.delete(destination,null,null); }
        });
    }
    private static void check(BooleanSupplier cancelled) throws java.io.InterruptedIOException {
        if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("已取消处理，原图保留");
    }
}
