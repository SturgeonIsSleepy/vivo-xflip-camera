package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PreviewMirrorControlsTest {
    @Test public void narrowPortraitControlSamplesStayInsideBitmap()throws Exception {
        for(int[] size:new int[][]{{1,1},{1,170},{7,170},{26,170},{89,105}}) {
            Bitmap bitmap=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.ARGB_8888);
            try {
                bitmap.eraseColor(Color.BLACK);assertFalse(review(bitmap));
                bitmap.eraseColor(Color.WHITE);assertTrue(review(bitmap));
            } finally {bitmap.recycle();}
        }
    }
    @Test public void landscapeControlDecisionIsPreserved()throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(45,105,Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(Color.BLACK);assertFalse(review(bitmap));
            bitmap.eraseColor(Color.WHITE);assertTrue(review(bitmap));
        } finally {bitmap.recycle();}
    }
    private static boolean review(Bitmap bitmap)throws Exception {
        Method method=PreviewMirrorWorker.class.getDeclaredMethod("reviewControls",Bitmap.class);
        method.setAccessible(true);return (Boolean)method.invoke(null,bitmap);
    }
}
