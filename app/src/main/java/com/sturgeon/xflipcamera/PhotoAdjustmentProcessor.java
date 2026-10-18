package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import com.sturgeon.xflipcamera.core.InputLevels;
import com.sturgeon.xflipcamera.core.UnmodifiedRowWindow;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

/** Explicit photo edits: denoise first, composite input levels second, existing tone/detail last. */
final class PhotoAdjustmentProcessor {
    record Options(int black, int white, boolean denoise, boolean detail, boolean hdr) {
        Options { new InputLevels(black,white); }
        boolean changed() { return black != 0 || white != 255 || denoise || detail || hdr; }
    }
    private PhotoAdjustmentProcessor() { }

    static Bitmap decode(ContentResolver resolver, Uri source, int previewSize) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver,source),(decoder,info,data)->{
            if (!"image/jpeg".equals(info.getMimeType()) || (long) info.getSize().getWidth()*info.getSize().getHeight()>13_000_000L) {
                throw new IllegalArgumentException("支持13MP以内JPEG");
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setMutableRequired(true);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            int width=info.getSize().getWidth(),height=info.getSize().getHeight();
            if (previewSize>0 && Math.max(width,height)>previewSize) {
                double ratio=previewSize/(double)Math.max(width,height);
                decoder.setTargetSize(Math.max(1,(int)Math.round(width*ratio)),Math.max(1,(int)Math.round(height*ratio)));
            }
        });
        if (Build.VERSION.SDK_INT>=34 && bitmap.hasGainmap()) {
            bitmap.recycle(); throw new IOException("暂不编辑HDR gain-map原图");
        }
        return bitmap;
    }

    static String apply(Bitmap bitmap, Options options, BooleanSupplier cancelled, Consumer<String> progress) throws IOException {
        return apply(bitmap,options,cancelled,progress,768,4);
    }

    private static String apply(Bitmap bitmap, Options options, BooleanSupplier cancelled, Consumer<String> progress,
            int tileSize, int threads) throws IOException {
        check(cancelled);
        if (options.denoise()) denoise(bitmap,cancelled,progress,tileSize,threads);
        InputLevels levels=new InputLevels(options.black(),options.white());
        if (options.black()!=0 || options.white()!=255) {
            int width=bitmap.getWidth(); int[] row=new int[width];
            for(int y=0;y<bitmap.getHeight();y++) {
                check(cancelled); bitmap.getPixels(row,0,width,0,y,width,1);
                levels.apply(row); bitmap.setPixels(row,0,width,0,y,width,1);
            }
        }
        String tone=PhotoEnhancer.apply(bitmap,new PhotoEnhancer.Options(false,options.detail(),options.hdr()),cancelled,progress);
        return "input_levels="+options.black()+".."+options.white()+"; nlm="+options.denoise()+"; "+tone;
    }

    static Uri save(ContentResolver resolver, Uri original, Options options, BooleanSupplier cancelled,
            Consumer<String> progress) throws IOException {
        return save(resolver,original,options,cancelled,progress,768,4);
    }

    /** Same production transaction, with scheduling overrides used only by debug device checks. */
    static Uri save(ContentResolver resolver, Uri original, Options options, BooleanSupplier cancelled,
            Consumer<String> progress, int tileSize, int threads) throws IOException {
        check(cancelled); Bitmap bitmap=decode(resolver,original,0);
        try {
            String description=apply(bitmap,options,cancelled,progress,tileSize,threads);
            progress.accept("正在另存完整尺寸，原图保留");
            return JpegPhotoWriter.write(resolver,original,bitmap,"XFlipAdjusted_",description,cancelled);
        } finally { bitmap.recycle(); }
    }

    /** Internal entry for the debug-only, same-APK scheduling comparison. Filtering parameters stay fixed. */
    static void denoise(Bitmap bitmap, BooleanSupplier cancelled, Consumer<String> progress,
            int tileSize, int threads) throws IOException {
        if(!OpenCVLoader.initLocal()) throw new IOException("OpenCV初始化失败");
        Core.setNumThreads(threads);
        int width=bitmap.getWidth(),height=bitmap.getHeight(),halo=16;
        // Snapshot whole bands before writing. Carry both overlapping halos forward, including
        // rows already overwritten by the preceding band, without a second full-size Bitmap.
        UnmodifiedRowWindow source=new UnmodifiedRowWindow(width,height,tileSize,halo,
                (pixels,offset,first,count)->bitmap.getPixels(pixels,offset,width,0,first,width,count));
        Mat rgba=new Mat(),bgr=new Mat(),filtered=new Mat();
        try {
            while(true) {
                check(cancelled);
                if(!source.nextBand())break;
                int top=source.bandTop();
                for(int left=0;left<width;left+=tileSize) {
                    check(cancelled);
                    progress.accept("NLM降噪 "+(100L*(top* (long)width+left)/(width*(long)height))+"%");
                    check(cancelled);
                    int x=Math.max(0,left-halo),y=Math.max(0,top-halo);
                    int right=Math.min(width,left+tileSize+halo),bottom=Math.min(height,top+tileSize+halo);
                    int w=right-x,h=bottom-y,copyWidth=Math.min(tileSize,width-left),copyHeight=Math.min(tileSize,height-top);
                    int[] pixels=new int[w*h]; source.copyPixels(pixels,0,w,x,y,w,h);
                    Bitmap tile=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    try {
                        tile.setPixels(pixels,0,w,0,0,w,h); Utils.bitmapToMat(tile,rgba);
                        Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
                        // Keep the original 8-bit RGB domain; a Lab round trip can band smooth gradients.
                        Photo.fastNlMeansDenoising(bgr,filtered,5f,7,21);
                        Imgproc.cvtColor(filtered,rgba,Imgproc.COLOR_BGR2RGBA); Utils.matToBitmap(rgba,tile);
                        tile.getPixels(pixels,0,copyWidth,left-x,top-y,copyWidth,copyHeight);
                        check(cancelled);
                        bitmap.setPixels(pixels,0,copyWidth,left,top,copyWidth,copyHeight);
                    } finally { tile.recycle(); }
                }
            }
        } finally { rgba.release();bgr.release();filtered.release(); }
        check(cancelled);
    }

    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if(cancelled.getAsBoolean()) throw new InterruptedIOException("已取消调整，原图保留");
    }
}
