package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import com.sturgeon.xflipcamera.core.ExifTransform;
import com.sturgeon.xflipcamera.core.JpegSamplePlan;
import com.sturgeon.xflipcamera.core.RgbFrameStats;

import java.io.IOException;
import java.io.InputStream;

/** Read-only, bounded inspection of one user-selected content URI. Run off the UI thread. */
public final class JpegPixelInspector {
    private JpegPixelInspector() { }

    public static String inspect(ContentResolver resolver, Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Choose a JPEG using a content URI");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = open(resolver, uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (!"image/jpeg".equals(bounds.outMimeType)) {
            throw new IOException("The selected file is not a decodable JPEG");
        }
        JpegSamplePlan plan = JpegSamplePlan.forDimensions(bounds.outWidth, bounds.outHeight);
        int orientation;
        try (InputStream stream = open(resolver, uri)) {
            orientation = new ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 0);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = plan.inSampleSize;
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = open(resolver, uri)) {
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) throw new IOException("JPEG pixel decode failed");
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            if (width > JpegSamplePlan.MAX_EDGE || height > JpegSamplePlan.MAX_EDGE) {
                throw new IOException("Decoder exceeded the diagnostic sample budget");
            }
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            RgbFrameStats stats = RgbFrameStats.fromArgb(pixels);
            return "encoded_px=" + bounds.outWidth + "x" + bounds.outHeight
                    + "\nsampled_px=" + width + "x" + height
                    + "\nin_sample_size=" + plan.inSampleSize
                    + "\nrgb_ranges=R[" + stats.redMin + "," + stats.redMax
                    + "] G[" + stats.greenMin + "," + stats.greenMax
                    + "] B[" + stats.blueMin + "," + stats.blueMax + "]"
                    + "\nnon_black_samples=" + stats.nonBlackPixels + "/" + stats.pixels
                    + "\nall_sampled_rgb_zero=" + stats.allBlack()
                    + "\nexif_orientation=" + orientation
                    + "\nexif_reflection=" + (orientation >= 1 && orientation <= 8
                            ? String.valueOf(ExifTransform.isMirrored(orientation)) : "unknown")
                    + "\n\n" + (stats.allBlack()
                            ? "采样像素全黑，请结合明亮场景对照与相机日志继续排查。"
                            : "采样检测到非零像素。")
                    + "\n此结果不证明全分辨率像素、有效场景或镜像方向验收通过；"
                    + "无法单独判定黑屏原因。";
        } finally {
            bitmap.recycle();
        }
    }

    private static InputStream open(ContentResolver resolver, Uri uri) throws IOException {
        InputStream stream = resolver.openInputStream(uri);
        if (stream == null) throw new IOException("Cannot read the selected JPEG");
        return stream;
    }
}
