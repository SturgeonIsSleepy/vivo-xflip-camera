package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import com.sturgeon.xflipcamera.core.PhotoToneCurve;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** SDR JPEG tone/detail processing. JPEG HDR tone is not exposure-bracket HDR or missing-data recovery. */
public final class PhotoEnhancer {
    public record Options(boolean autoLevels, boolean detail, boolean hdrTone) {
        public static final Options NONE = new Options(false,false,false);
        public boolean enabled() { return autoLevels || detail || hdrTone; }
    }
    public record Result(Uri uri, String description) { }
    private PhotoEnhancer() { }

    public static Result enhance(ContentResolver resolver, Uri source, Options options, BooleanSupplier cancelled,
            Consumer<String> progress) throws IOException {
        if (!options.enabled()) return new Result(source,"未启用照片增强");
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver,source),(decoder,info,data)->{
            if (!"image/jpeg".equals(info.getMimeType()) || (long) info.getSize().getWidth()*info.getSize().getHeight()>13_000_000L) {
                throw new IllegalArgumentException("照片增强支持13MP以内JPEG");
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setMutableRequired(true);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
        try {
            if (Build.VERSION.SDK_INT>=34 && bitmap.hasGainmap()) throw new IOException("暂不编辑HDR gain-map原图");
            String description = apply(bitmap,options,cancelled,progress);
            return new Result(JpegPhotoWriter.write(resolver,source,bitmap,"XFlipEnhanced_",description,cancelled),description);
        } finally { bitmap.recycle(); }
    }

    /** Mutates only the caller's new/decoded working bitmap before its first JPEG encode. */
    public static String apply(Bitmap bitmap, Options options, BooleanSupplier cancelled, Consumer<String> progress) throws IOException {
        if (!options.enabled()) return "tone=off";
        check(cancelled);
        if (!OpenCVLoader.initLocal()) throw new IOException("照片算法初始化失败");
        Core.setNumThreads(2);
        Mat rgba=new Mat(),gray=new Mat(),contrast=new Mat(),blur=new Mat(),hdrBase=new Mat();
        CLAHE clahe=null;
        try {
            progress.accept("正在分析直方图与照片细节，原图保留");
            Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);rgba.release();
            int width=bitmap.getWidth(),height=bitmap.getHeight();
            long[] histogram=new long[256];byte[] scan=new byte[width];
            for(int y=0;y<height;y++) { check(cancelled);gray.get(y,0,scan);for(byte value:scan)histogram[value&255]++; }
            PhotoToneCurve curve=new PhotoToneCurve(histogram,options.autoLevels(),estimateNoise(gray));
            if(curve.stretched) {
                Mat table=new Mat(1,256,CvType.CV_8U),mapped=new Mat();
                try {
                    byte[] values=new byte[256];for(int i=0;i<256;i++)values[i]=(byte)curve.at(i);
                    table.put(0,0,values);Core.LUT(gray,table,mapped);gray.release();gray=mapped;mapped=new Mat();
                } finally { table.release();mapped.release(); }
            }
            double noise=estimateNoise(gray);
            long[] adjusted=new long[256];for(int i=0;i<256;i++)adjusted[curve.at(i)]+=histogram[i];
            double compression=options.hdrTone()?PhotoToneCurve.hdrCompression(adjusted):1;
            if(options.detail()) {
                progress.accept("正在增强文字与纹理，控制噪点与光晕");
                clahe=Imgproc.createCLAHE(1.4,new Size(8,8));clahe.apply(gray,contrast);
                Imgproc.GaussianBlur(gray,blur,new Size(3,3),1.0);
            }
            if(compression<0.999) {
                progress.accept("正在进行JPEG HDR色调映射");
                Mat small=new Mat(),log=new Mat(),base=new Mat();
                try {
                    double ratio=Math.min(1,640.0/Math.max(width,height));
                    Imgproc.resize(gray,small,new Size(Math.round(width*ratio),Math.round(height*ratio)),0,0,Imgproc.INTER_AREA);
                    small.convertTo(log,CvType.CV_32F,1.0/255);Core.max(log,new Scalar(1.0/255),log);Core.log(log,log);
                    Imgproc.bilateralFilter(log,base,9,0.4,5);
                    Imgproc.resize(base,hdrBase,gray.size(),0,0,Imgproc.INTER_LINEAR);
                } finally { small.release();log.release();base.release(); }
            }
            int[] colors=new int[width];byte[] before=new byte[width],center=new byte[width],after=new byte[width];
            byte[] local=new byte[width],smooth=new byte[width];float[] baseRow=new float[width];
            for(int y=0;y<height;y++) {
                check(cancelled);bitmap.getPixels(colors,0,width,0,y,width,1);gray.get(y,0,center);
                if(options.detail()) {
                    gray.get(Math.max(0,y-1),0,before);gray.get(Math.min(height-1,y+1),0,after);
                    contrast.get(y,0,local);blur.get(y,0,smooth);
                }
                if(compression<0.999)hdrBase.get(y,0,baseRow);
                for(int x=0;x<width;x++) {
                    int level=center[x]&255;
                    int mapped=compression<0.999?PhotoToneCurve.hdrLuma(level,baseRow[x],compression,noise):level;
                    if(options.detail() && x>0 && x+1<width && Math.abs(level-(smooth[x]&255))>=Math.max(2,noise*1.8)) {
                        int lo=255,hi=0;
                        for(int xx=x-1;xx<=x+1;xx++) {
                            lo=Math.min(lo,Math.min(before[xx]&255,Math.min(center[xx]&255,after[xx]&255)));
                            hi=Math.max(hi,Math.max(before[xx]&255,Math.max(center[xx]&255,after[xx]&255)));
                        }
                        mapped+=PhotoToneCurve.detailDelta(level,smooth[x]&255,local[x]&255,noise,lo,hi);
                    }
                    colors[x]=PhotoToneCurve.withLuma(colors[x],Math.max(0,Math.min(255,mapped)));
                }
                bitmap.setPixels(colors,0,width,0,y,width,1);
            }
            return "levels="+curve.black+".."+curve.white+", expanded="+curve.stretched
                    +"; detail="+options.detail()+"; jpeg_hdr_tone="+compression+"; noise="+noise;
        } finally {
            rgba.release();gray.release();contrast.release();blur.release();hdrBase.release();
            if(clahe!=null){clahe.collectGarbage();clahe.clear();}
        }
    }

    private static double estimateNoise(Mat gray) {
        int width=gray.cols(),height=gray.rows();
        int step=Math.max(2,(int)Math.sqrt((double)width*height/65536));
        double[] samples=new double[Math.min(65536,Math.max(1,(width/2)*(height/2)))];
        byte[] a=new byte[width],b=new byte[width];int count=0;
        for(int y=0;y+1<height && count<samples.length;y+=step) {
            gray.get(y,0,a);gray.get(y+1,0,b);
            for(int x=0;x+1<width && count<samples.length;x+=step) {
                samples[count++]=Math.abs((a[x]&255)-(a[x+1]&255)-(b[x]&255)+(b[x+1]&255))/2.0;
            }
        }
        if(count==0)return 2;
        Arrays.sort(samples,0,count);return Math.max(2,Math.min(16,samples[count/2]/0.67448975));
    }
    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if(cancelled.getAsBoolean())throw new InterruptedIOException("已取消照片增强，原图保留");
    }
}
