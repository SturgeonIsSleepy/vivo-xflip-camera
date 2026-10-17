package com.sturgeon.xflipcamera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Composite RGB input histogram of the explicitly labelled preview sample. */
final class HistogramView extends View {
    private final Paint paint=new Paint();
    private long[] bins=new long[256];
    private int black,white=255;
    HistogramView(Context context) { super(context); setContentDescription("RGB综合直方图，预览采样"); }
    void setHistogram(long[] values) { bins=values.clone();invalidate(); }
    void setLevels(int black,int white) { this.black=black;this.white=white;invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);canvas.drawColor(Color.rgb(24,24,24));
        float left=8,right=getWidth()-8,top=8,bottom=getHeight()-8;
        long maximum=1;for(long count:bins)maximum=Math.max(maximum,count);
        paint.setColor(0xffeeeeee);
        for(int i=0;i<256;i++) {
            float x=left+(right-left)*i/255f;
            canvas.drawLine(x,bottom,x,bottom-(bottom-top)*bins[i]/maximum,paint);
        }
        float b=left+(right-left)*black/255f,w=left+(right-left)*white/255f;
        paint.setColor(0x99000000);canvas.drawRect(left,top,b,bottom,paint);canvas.drawRect(w,top,right,bottom,paint);
        paint.setColor(0xffffc34d);canvas.drawLine(b,top,b,bottom,paint);
        paint.setColor(0xff6ad9ff);canvas.drawLine(w,top,w,bottom,paint);
    }
}
