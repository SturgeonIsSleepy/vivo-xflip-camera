package com.sturgeon.xflipcamera;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import com.sturgeon.xflipcamera.core.UnmodifiedRowWindow;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Real native Bitmap getPixels/setPixels bridge; this does not execute ARM64 OpenCV NLM. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class BitmapRowWindowTest {
    @Test public void portraitNonMultipleBandsMatchFullBitmapSnapshot() throws IOException {
        verifyBitmap(1031,1035);
    }

    @Test public void landscapeNonMultipleBandsMatchFullBitmapSnapshot() throws IOException {
        verifyBitmap(1539,531);
    }

    @Test public void onePixelAndSubTileDimensionsMatchFullBitmapSnapshot() throws IOException {
        for (int[] shape : new int[][]{{1,1},{1,1033},{1033,1},{27,35}}) {
            verifyBitmap(shape[0],shape[1]);
        }
    }

    private static void verifyBitmap(int width, int height) throws IOException {
        Bitmap live = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Bitmap frozen = null;
        try {
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) row[x] = 0xff000000 | ((x * 7331 ^ y * 57943) & 0xffffff);
                live.setPixels(row,0,width,0,y,width,1);
            }
            // The former production source copy is retained only as an independent test oracle.
            frozen = live.copy(Bitmap.Config.ARGB_8888,false);
            UnmodifiedRowWindow window = new UnmodifiedRowWindow(width,height,512,16,
                    (target,offset,first,count)->live.getPixels(target,offset,width,0,first,width,count));
            while (window.nextBand()) {
                int top = window.bandTop();
                for (int left = 0; left < width; left += 512) {
                    int x = Math.max(0,left - 16), y = Math.max(0,top - 16);
                    int w = Math.min(width,left + 528) - x, h = Math.min(height,top + 528) - y;
                    int[] expected = new int[w * h], actual = new int[w * h];
                    frozen.getPixels(expected,0,w,x,y,w,h);
                    window.copyPixels(actual,0,w,x,y,w,h);
                    assertArrayEquals("Source tile including all overwritten halos",expected,actual);
                    int copyWidth = Math.min(512,width - left), copyHeight = Math.min(512,height - top);
                    Arrays.fill(actual,0xffc317e9);
                    live.setPixels(actual,0,copyWidth,left,top,copyWidth,copyHeight);
                }
            }
            for (int y = 0; y < height; y++) {
                live.getPixels(row,0,width,0,y,width,1);
                for (int pixel : row) assertTrue("All output pixels were overwritten",pixel == 0xffc317e9);
            }
        } finally {
            if (frozen != null) frozen.recycle();
            live.recycle();
        }
    }
}
