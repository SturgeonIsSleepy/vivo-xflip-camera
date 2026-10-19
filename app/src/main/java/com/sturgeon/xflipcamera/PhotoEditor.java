package com.sturgeon.xflipcamera;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import com.sturgeon.xflipcamera.core.InputLevels;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Edits a returned JPEG in an app-owned window; never draws controls over the OEM camera. */
final class PhotoEditor {
    interface Processor {
        Bitmap decode(ContentResolver resolver, Uri source, int previewSize) throws IOException;
        void apply(Bitmap bitmap, PhotoAdjustmentProcessor.Options options, BooleanSupplier cancelled) throws IOException;
        Uri save(ContentResolver resolver, Uri source, PhotoAdjustmentProcessor.Options options,
                BooleanSupplier cancelled, Consumer<String> progress) throws IOException;
    }
    private static final Processor PRODUCTION = new Processor() {
        @Override public Bitmap decode(ContentResolver resolver, Uri source, int previewSize) throws IOException {
            return PhotoAdjustmentProcessor.decode(resolver,source,previewSize);
        }
        @Override public void apply(Bitmap bitmap, PhotoAdjustmentProcessor.Options options,
                BooleanSupplier cancelled) throws IOException {
            PhotoAdjustmentProcessor.apply(bitmap,options,cancelled,message->{});
        }
        @Override public Uri save(ContentResolver resolver, Uri source, PhotoAdjustmentProcessor.Options options,
                BooleanSupplier cancelled, Consumer<String> progress) throws IOException {
            return PhotoAdjustmentProcessor.save(resolver,source,options,cancelled,progress);
        }
    };
    private final ComponentActivity owner;
    private final Consumer<Uri> finished;
    private volatile boolean closed;
    private OnBackPressedCallback backCallback;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor;
    private final Processor processor;
    private final AtomicInteger generation=new AtomicInteger();
    private Uri source;
    private Bitmap original,processed;
    private ImageView image;
    private HistogramView histogram;
    private SeekBar black,white;
    private CheckBox showHistogram,denoise,detail,hdr;
    private TextView values,status;
    private Button compare,reset,save;
    private boolean showingOriginal,busy,updating;
    private final Runnable render=this::renderPreview;

    PhotoEditor(ComponentActivity owner, Uri source, Bundle state, Consumer<Uri> finished) {
        this(owner,source,state,finished,Executors.newSingleThreadExecutor(),PRODUCTION);
    }

    /** Package-only deterministic worker/processor seam; normal construction uses the real pipeline. */
    PhotoEditor(ComponentActivity owner, Uri source, Bundle state, Consumer<Uri> finished,
            ExecutorService executor, Processor processor) {
        this.owner=owner;this.source=source;this.finished=finished;
        this.executor=executor;this.processor=processor;
        LinearLayout root=new LinearLayout(owner);root.setBackgroundColor(Color.BLACK);
        image=new ImageView(owner);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout panel=new LinearLayout(owner);panel.setOrientation(LinearLayout.VERTICAL);
        int width=owner.getWindowManager().getCurrentWindowMetrics().getBounds().width();
        root.addView(panel,new LinearLayout.LayoutParams(Math.max(220,width/2),-1));
        ScrollView scroll=new ScrollView(owner);LinearLayout fields=new LinearLayout(owner);
        fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(8,4,8,4);scroll.addView(fields);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        showHistogram=checkBox("显示RGB综合直方图（预览采样）",fields);
        histogram=new HistogramView(owner);fields.addView(histogram,new LinearLayout.LayoutParams(-1,76));
        values=new TextView(owner);values.setTextColor(Color.WHITE);values.setTextSize(12);fields.addView(values);
        black=slider("黑场",fields,254);white=slider("白场",fields,255);
        denoise=checkBox("OpenCV NLM降噪",fields);detail=checkBox("文字与纹理细节",fields);
        hdr=checkBox("JPEG HDR色调",fields);
        status=new TextView(owner);status.setTextColor(Color.LTGRAY);status.setTextSize(12);fields.addView(status);
        LinearLayout buttons=new LinearLayout(owner);panel.addView(buttons,new LinearLayout.LayoutParams(-1,48));
        compare=button("原图",buttons);reset=button("还原",buttons);save=button("另存",buttons);
        Button back=button("返回",buttons);back.setOnClickListener(view->finish());
        showHistogram.setChecked(state==null ? owner.getSharedPreferences("photo_editor",android.content.Context.MODE_PRIVATE).getBoolean("histogram",true) : state.getBoolean("histogram",true));
        histogram.setVisibility(showHistogram.isChecked()?View.VISIBLE:View.GONE);
        showHistogram.setOnCheckedChangeListener((view,checked)->{
            histogram.setVisibility(checked?View.VISIBLE:View.GONE);owner.getSharedPreferences("photo_editor",android.content.Context.MODE_PRIVATE).edit().putBoolean("histogram",checked).apply();
        });
        updating=true;
        black.setProgress(state==null?0:state.getInt("black",0));
        white.setProgress(state==null?255:state.getInt("white",255));
        denoise.setChecked(state!=null && state.getBoolean("denoise"));detail.setChecked(state!=null && state.getBoolean("detail"));
        hdr.setChecked(state!=null && state.getBoolean("hdr"));updating=false;
        for(CheckBox box:new CheckBox[]{denoise,detail,hdr}) box.setOnCheckedChangeListener((view,checked)->schedulePreview());
        compare.setOnClickListener(view->{showingOriginal=!showingOriginal;showImage();});
        reset.setOnClickListener(view->{updating=true;black.setProgress(0);white.setProgress(255);
            denoise.setChecked(false);detail.setChecked(false);hdr.setChecked(false);updating=false;schedulePreview();});
        save.setOnClickListener(view->save());owner.setContentView(root);
        backCallback=new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { finish(); } };
        owner.getOnBackPressedDispatcher().addCallback(backCallback);
        setBusy(true);status.setText("正在读取原图");
        executor.execute(()->{
            try {
                Bitmap bitmap=processor.decode(owner.getContentResolver(),source,960);
                long[] bins=new long[256];int[] row=new int[bitmap.getWidth()];
                for(int y=0;y<bitmap.getHeight();y++) { bitmap.getPixels(row,0,row.length,0,y,row.length,1);InputLevels.addHistogram(bins,row); }
                main.post(()->{if(isDestroyed()) {bitmap.recycle();return;}
                    original=bitmap;histogram.setHistogram(bins);setBusy(false);schedulePreview();});
            } catch(Exception failure) { main.post(()->{if(!isDestroyed()){setBusy(false);status.setText("读取失败："+failure.getMessage());}}); }
        });
    }

    private SeekBar slider(String label,LinearLayout parent,int maximum) {
        LinearLayout row=new LinearLayout(owner);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView caption=new TextView(owner);caption.setText(label);caption.setTextSize(12);caption.setTextColor(Color.WHITE);
        row.addView(caption,new LinearLayout.LayoutParams(42,32));
        SeekBar bar=new SeekBar(owner);bar.setMax(maximum);bar.setContentDescription(label);
        row.addView(bar,new LinearLayout.LayoutParams(0,32,1));parent.addView(row);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view,int value,boolean user) {
                if(updating || black==null || white==null)return;
                updating=true;
                if(black.getProgress()>=white.getProgress()) {
                    if(view==black)black.setProgress(Math.max(0,white.getProgress()-1));
                    else white.setProgress(Math.min(255,black.getProgress()+1));
                }
                updating=false;schedulePreview();
            }
            @Override public void onStartTrackingTouch(SeekBar view) { }
            @Override public void onStopTrackingTouch(SeekBar view) { }
        });return bar;
    }

    private CheckBox checkBox(String label,LinearLayout parent) {
        CheckBox box=new CheckBox(owner);box.setText(label);box.setTextSize(12);box.setTextColor(Color.WHITE);
        box.setMinHeight(0);box.setMinimumHeight(0);box.setPadding(0,0,0,0);
        parent.addView(box,new LinearLayout.LayoutParams(-1,36));return box;
    }

    private Button button(String label,LinearLayout parent) {
        Button button=new Button(owner);button.setText(label);button.setTextSize(11);button.setAllCaps(false);
        button.setPadding(0,0,0,0);button.setMinWidth(0);button.setMinimumWidth(0);
        parent.addView(button,new LinearLayout.LayoutParams(0,-1,1));return button;
    }

    private PhotoAdjustmentProcessor.Options options() {
        return new PhotoAdjustmentProcessor.Options(black.getProgress(),white.getProgress(),denoise.isChecked(),detail.isChecked(),hdr.isChecked());
    }

    private void schedulePreview() {
        if(updating || original==null || busy)return;
        generation.incrementAndGet();main.removeCallbacks(render);showingOriginal=false;
        values.setText("输入黑场 "+black.getProgress()+"   白场 "+white.getProgress());
        histogram.setLevels(black.getProgress(),white.getProgress());save.setEnabled(false);
        status.setText("正在更新预览");main.postDelayed(render,120);
    }

    private void renderPreview() {
        int request=generation.get();PhotoAdjustmentProcessor.Options settings=options();
        Bitmap working=original.copy(Bitmap.Config.ARGB_8888,true);
        executor.execute(()->{
            try {
                processor.apply(working,settings,()->isDestroyed() || generation.get()!=request);
                main.post(()->{
                    if(isDestroyed() || generation.get()!=request){working.recycle();return;}
                    Bitmap previous=processed;processed=working;showImage();if(previous!=null)previous.recycle();
                    status.setText("预览采样；另存使用完整尺寸，原图保留");
                });
            } catch(Exception failure) { working.recycle();main.post(()->{
                if(!isDestroyed() && generation.get()==request)status.setText("预览未完成："+failure.getMessage());}); }
        });
    }

    private void showImage() {
        image.setImageBitmap(showingOriginal || processed==null?original:processed);
        compare.setText(showingOriginal?"看调整":"原图");
        save.setEnabled(!busy && !showingOriginal && processed!=null && options().changed());
    }

    private void setBusy(boolean value) {
        busy=value;
        for(View view:new View[]{black,white,denoise,detail,hdr,reset,compare})view.setEnabled(!value);
        save.setEnabled(!value && processed!=null && !showingOriginal && options().changed());
    }

    private void save() {
        if(busy || processed==null)return;
        PhotoAdjustmentProcessor.Options settings=options();int request=generation.incrementAndGet();
        main.removeCallbacks(render);setBusy(true);status.setText("正在处理完整尺寸");
        executor.execute(()->{
            try {
                Uri output=processor.save(owner.getContentResolver(),source,settings,
                        ()->isDestroyed() || generation.get()!=request,message->main.post(()->{if(!isDestroyed())status.setText(message);}));
                main.post(()->{if(!isDestroyed()){close();finished.accept(output);}});
            } catch(Exception failure) {main.post(()->{if(!isDestroyed()){setBusy(false);status.setText("未保存，原图保留："+failure.getMessage());}});}
        });
    }

    Bundle saveState() {
        Bundle state=new Bundle();state.putBoolean("histogram",showHistogram.isChecked());
        state.putInt("black",black.getProgress());state.putInt("white",white.getProgress());
        state.putBoolean("denoise",denoise.isChecked());state.putBoolean("detail",detail.isChecked());state.putBoolean("hdr",hdr.isChecked());return state;
    }

    private boolean isDestroyed() { return closed || owner.isDestroyed(); }

    private void finish() { close();finished.accept(null); }

    void close() {
        if(closed)return;closed=true;
        if(backCallback!=null)backCallback.remove();
        generation.incrementAndGet();main.removeCallbacksAndMessages(null);executor.shutdownNow();
        if(image!=null)image.setImageDrawable(null);if(original!=null)original.recycle();if(processed!=null)processed.recycle();
    }
}
