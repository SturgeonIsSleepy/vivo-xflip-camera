package com.sturgeon.xflipcamera;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opencv.core.Core;

/** One APK, fixed synthetic input, forward/reverse order; no user-facing tuning options. */
public final class NlmSchedulingDeviceTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            getTargetContext().startActivity(new Intent(getTargetContext(),SystemCaptureActivity.class)
                    .putExtra("manual_entry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            File folder=new File(getTargetContext().getFilesDir(),"tone-fixtures");
            Bitmap warm=PhotoAdjustmentProcessor.decode(getTargetContext().getContentResolver(),Uri.fromFile(new File(folder,"nlm.jpg")),0);
            try { PhotoAdjustmentProcessor.denoise(warm,()->false,message->{},512,2); }
            finally { warm.recycle(); }
            Uri source=Uri.fromFile(new File(folder,"nlm-full.jpg"));
            int[][] configs={{512,2},{512,4},{768,4},{1024,2},{1024,4}};
            JSONArray cases=new JSONArray();String reference=null;
            PowerManager power=getTargetContext().getSystemService(PowerManager.class);
            for(int round=0;round<2;round++) for(int index=0;index<configs.length;index++) {
                int selected=round==0?index:configs.length-1-index;
                int tile=configs[selected][0],threads=configs[selected][1];
                Bitmap bitmap=PhotoAdjustmentProcessor.decode(getTargetContext().getContentResolver(),source,0);
                try {
                    int before=power.getCurrentThermalStatus();long started=System.nanoTime();
                    PhotoAdjustmentProcessor.denoise(bitmap,()->false,message->{},tile,threads);
                    long elapsed=(System.nanoTime()-started)/1_000_000;int after=power.getCurrentThermalStatus();
                    String digest=hash(bitmap);
                    if(reference==null)reference=digest;
                    if(!reference.equals(digest))throw new IllegalStateException("NLM pixels changed for "+tile+"/"+threads);
                    cases.put(new JSONObject().put("round",round).put("order",index).put("tile",tile)
                            .put("threads",threads).put("denoise_ms",elapsed).put("thermal_before",before)
                            .put("thermal_after",after).put("opencv_threads",Core.getNumThreads()).put("raw_pixel_sha256",digest));
                } finally { bitmap.recycle(); }
            }
            result.putString("cases",cases.toString());result.putBoolean("all_pixels_equal",true);
        } catch(Exception failure) {result.putString("error",failure.toString());}
        finish(result.containsKey("error")?0:-1,result);
    }
    private static String hash(Bitmap bitmap)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");int width=bitmap.getWidth();
        int[] row=new int[width];ByteBuffer bytes=ByteBuffer.allocate(width*4);
        for(int y=0;y<bitmap.getHeight();y++) {
            bitmap.getPixels(row,0,width,0,y,width,1);bytes.clear();bytes.asIntBuffer().put(row);digest.update(bytes.array());
        }
        StringBuilder hex=new StringBuilder();
        for(byte value:digest.digest())hex.append(Character.forDigit((value>>>4)&15,16)).append(Character.forDigit(value&15,16));
        return hex.toString();
    }
}
