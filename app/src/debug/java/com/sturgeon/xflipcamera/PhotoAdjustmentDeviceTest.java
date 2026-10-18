package com.sturgeon.xflipcamera;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

/** Synthetic-only production editor checks, including an independent untiled NLM comparison. */
public final class PhotoAdjustmentDeviceTest extends Instrumentation {
    private String scenario;
    private int tile,threads;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);scenario=arguments.getString("scenario","manual");
        tile=Integer.parseInt(arguments.getString("nlm_tile","0"));threads=Integer.parseInt(arguments.getString("nlm_threads","0"));start();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(!List.of("manual","color","denoise","rotated","identity","cancel","full").contains(scenario))throw new IllegalArgumentException("Unknown synthetic case");
            if ((tile==0)!=(threads==0) || tile!=0 && (!List.of(512,768,1024).contains(tile) || !List.of(2,4).contains(threads)))throw new IllegalArgumentException("Unsupported debug schedule");
            getTargetContext().startActivity(new Intent(getTargetContext(),SystemCaptureActivity.class).putExtra("manual_entry",true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            File directory=new File(getTargetContext().getFilesDir(),"tone-fixtures");
            String fixture=switch(scenario) {case "manual","identity"->"levels";case "denoise","cancel"->"nlm";case "full"->"nlm-full";default->scenario;};
            File file=new File(directory,fixture+".jpg");byte[] before=hash(file);Uri source=Uri.fromFile(file);
            Bitmap baseline=PhotoAdjustmentProcessor.decode(getTargetContext().getContentResolver(),source,0);
            try(OutputStream output=Files.newOutputStream(new File(directory,scenario+"-edit-baseline.jpg").toPath())) {
                if(!baseline.compress(Bitmap.CompressFormat.JPEG,97,output))throw new IllegalStateException("Baseline encode");
            } finally { baseline.recycle(); }
            boolean nlm=List.of("denoise","cancel","full").contains(scenario);
            PhotoAdjustmentProcessor.Options settings=switch(scenario) {
                case "manual"->new PhotoAdjustmentProcessor.Options(40,215,false,false,false);
                case "color"->new PhotoAdjustmentProcessor.Options(32,224,false,false,false);
                case "rotated"->new PhotoAdjustmentProcessor.Options(90,149,false,false,false);
                default->new PhotoAdjustmentProcessor.Options(0,255,nlm,false,false);
            };
            AtomicBoolean cancelled=new AtomicBoolean();long started=System.nanoTime();
            try {
                if(scenario.equals("denoise")) {
                    Bitmap tiled=PhotoAdjustmentProcessor.decode(getTargetContext().getContentResolver(),source,0);
                    try {
                        PhotoAdjustmentProcessor.apply(tiled,settings,()->false,message->{});
                        int maxError=compareUntiled(tiled,source);result.putInt("tile_max_channel_error",maxError);
                        if(maxError>1)throw new IllegalStateException("NLM tile seam/reference mismatch");
                    } finally {tiled.recycle();}
                }
                long saveStarted=System.nanoTime();
                java.util.function.Consumer<String> progress=message->{if(scenario.equals("cancel"))cancelled.set(true);};
                Uri saved=tile==0?PhotoAdjustmentProcessor.save(getTargetContext().getContentResolver(),source,settings,cancelled::get,progress)
                        :PhotoAdjustmentProcessor.save(getTargetContext().getContentResolver(),source,settings,cancelled::get,progress,tile,threads);
                result.putLong("save_ms",(System.nanoTime()-saveStarted)/1_000_000);
                if(settings.denoise())result.putInt("opencv_threads",org.opencv.core.Core.getNumThreads());
                if(scenario.equals("cancel"))throw new IllegalStateException("Cancelled output published");
                try(InputStream input=getTargetContext().getContentResolver().openInputStream(saved)) {
                    if(input==null)throw new IllegalStateException("Missing output");
                    Files.copy(input,new File(directory,scenario+"-edit-result.jpg").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {if(getTargetContext().getContentResolver().delete(saved,null,null)!=1)throw new IllegalStateException("Synthetic cleanup");}
            } catch(java.io.InterruptedIOException expected) {
                if(!scenario.equals("cancel"))throw expected;result.putBoolean("cancelled",true);
            }
            result.putLong("processing_ms",(System.nanoTime()-started)/1_000_000);
            if(!Arrays.equals(before,hash(file)))throw new IllegalStateException("Original modified");result.putBoolean("original_preserved",true);
        } catch(Exception failure) {result.putString("error",failure.toString());}
        finish(result.containsKey("error")?0:-1,result);
    }
    private int compareUntiled(Bitmap tiled,Uri source)throws Exception {
        if(!OpenCVLoader.initLocal())throw new IllegalStateException("OpenCV initialization");
        Bitmap reference=PhotoAdjustmentProcessor.decode(getTargetContext().getContentResolver(),source,0);
        Mat rgba=new Mat(),bgr=new Mat(),filtered=new Mat();
        try {
            Utils.bitmapToMat(reference,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            Photo.fastNlMeansDenoising(bgr,filtered,5f,7,21);
            Imgproc.cvtColor(filtered,rgba,Imgproc.COLOR_BGR2RGBA);Utils.matToBitmap(rgba,reference);
            int width=tiled.getWidth(),maximum=0;int[] a=new int[width],b=new int[width];
            for(int y=0;y<tiled.getHeight();y++) {
                tiled.getPixels(a,0,width,0,y,width,1);reference.getPixels(b,0,width,0,y,width,1);
                for(int x=0;x<width;x++)for(int shift=0;shift<=16;shift+=8)maximum=Math.max(maximum,Math.abs(((a[x]>>>shift)&255)-((b[x]>>>shift)&255)));
            }
            return maximum;
        } finally {reference.recycle();rgba.release();bgr.release();filtered.release();}
    }
    private static byte[] hash(File file)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(file.toPath())) {
            byte[] bytes=new byte[32768];int count;while((count=input.read(bytes))!=-1)digest.update(bytes,0,count);
        }return digest.digest();
    }
}
