package com.sturgeon.xflipcamera;

import android.app.Instrumentation;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/** Debug-only synthetic checks of the production single-photo processing and JPEG publisher. */
public final class PhotoEnhancementDeviceTest extends Instrumentation {
    private String scenario;
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);scenario=arguments.getString("scenario","levels");start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(!List.of("levels","narrow","detail","hdr","flat","color","rotated","cancel").contains(scenario))throw new IllegalArgumentException("Unknown fixture");
            getTargetContext().startActivity(new Intent(getTargetContext(),SystemCaptureActivity.class).putExtra("manual_entry",true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            File directory=new File(getTargetContext().getFilesDir(),"tone-fixtures"),file=new File(directory,scenario+".jpg");
            byte[] before=hash(file);Uri source=Uri.fromFile(file);
            Bitmap baseline=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getTargetContext().getContentResolver(),source),(decoder,info,data)->{
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            });
            try(OutputStream out=Files.newOutputStream(new File(directory,scenario+"-baseline.jpg").toPath())){
                if(!baseline.compress(Bitmap.CompressFormat.JPEG,97,out))throw new IllegalStateException("Baseline encode failed");
            }finally{baseline.recycle();}
            PhotoEnhancer.Options options=switch(scenario){
                case "levels", "narrow" -> new PhotoEnhancer.Options(true,false,false);
                case "detail" -> new PhotoEnhancer.Options(false,true,false);
                case "hdr" -> new PhotoEnhancer.Options(false,false,true);
                case "rotated" -> new PhotoEnhancer.Options(true,true,false);
                default -> new PhotoEnhancer.Options(true,true,true);
            };
            long start=System.nanoTime();
            try {
                PhotoEnhancer.Result enhanced=PhotoEnhancer.enhance(getTargetContext().getContentResolver(),source,options,
                        ()->scenario.equals("cancel"),message->{});
                if(scenario.equals("cancel"))throw new IllegalStateException("Cancelled output published");
                try(InputStream input=getTargetContext().getContentResolver().openInputStream(enhanced.uri())){
                    if(input==null)throw new IllegalStateException("Missing output");
                    Files.copy(input,new File(directory,scenario+"-result.jpg").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                if(getTargetContext().getContentResolver().delete(enhanced.uri(),null,null)!=1)throw new IllegalStateException("Synthetic output cleanup failed");
                result.putString("description",enhanced.description());
            }catch(java.io.InterruptedIOException expected){if(!scenario.equals("cancel"))throw expected;result.putBoolean("cancelled",true);}
            if(!Arrays.equals(before,hash(file)))throw new IllegalStateException("Original changed");
            result.putBoolean("original_preserved",true);result.putLong("processing_ms",(System.nanoTime()-start)/1_000_000);
        }catch(Exception failure){result.putString("error",failure.toString());}
        finish(result.containsKey("error")?0:-1,result);
    }
    private static byte[] hash(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(file.toPath())){
            byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
        }return digest.digest();
    }
}
