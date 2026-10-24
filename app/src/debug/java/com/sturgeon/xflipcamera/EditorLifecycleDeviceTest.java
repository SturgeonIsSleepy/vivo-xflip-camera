package com.sturgeon.xflipcamera;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Synthetic input, real cover Activity/recreate/worker. Does not open or operate the OEM camera. */
public final class EditorLifecycleDeviceTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        SharedPreferences prefs=getTargetContext().getSharedPreferences("system_capture",0);
        Map<String,?> previous=prefs.getAll();
        SystemCaptureActivity owner=null;
        try {
            File file=new File(getTargetContext().getFilesDir(),"tone-fixtures/nlm-full.jpg");
            byte[] original=Files.readAllBytes(file.toPath());Uri source=Uri.fromFile(file);
            int before=mediaRows();
            getTargetContext().startActivity(new Intent(getTargetContext(),SystemCaptureActivity.class)
                    .putExtra("manual_entry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            await(()->active()!=null,10000);owner=active();SystemCaptureActivity first=owner;
            ui(()->{set(first,"photo",null);set(first,"output",source);set(first,"originalPhoto",source);
                set(first,"pending",false);set(first,"reading",true);first.recreate();});
            await(()->active()!=null && active()!=first,10000);owner=active();SystemCaptureActivity restored=owner;
            await(()->!((Boolean)get(restored,"reading")),15000);
            check(onUi(()->((Button)get(restored,"shutter")).isEnabled()),"output-only controls");
            check(onUi(()->source.equals(get(restored,"output"))),"output-only source");
            result.putBoolean("output_only_recreate",true);
            ui(()->open(restored,source));await(()->ready(editor(restored)),15000);
            PhotoEditor current=editor(restored);
            ui(()->{
                SeekBar black=(SeekBar)get(current,"black");Button save=(Button)get(current,"save");
                for(int i=1;i<=40;i++) {black.setProgress(i);check(!save.isEnabled(),"stale preview save");}
                Button compare=(Button)get(current,"compare");compare.performClick();compare.performClick();
                check(!save.isEnabled(),"compare enabled stale save");
            });
            await(()->ready(current),15000);result.putBoolean("rapid_levels_and_compare",true);
            ui(restored::recreate);await(()->active()!=null && active()!=restored,10000);owner=active();
            SystemCaptureActivity rebuilt=owner;await(()->ready(editor(rebuilt)),15000);
            PhotoEditor after=editor(rebuilt);
            check(onUi(()->((SeekBar)get(after,"black")).getProgress()==40),"editor state restore");
            check(onUi(()->((Button)get(after,"save")).isEnabled()),"restored matching preview");
            result.putBoolean("editor_recreate_and_settings",true);
            ui(()->((CheckBox)get(after,"denoise")).setChecked(true));await(()->ready(after),15000);
            ui(()->((Button)get(after,"save")).performClick());Thread.sleep(500);
            check(onUi(()->(Boolean)get(after,"busy")),"save was not running");
            ui(()->rebuilt.getOnBackPressedDispatcher().onBackPressed());
            ExecutorService worker=(ExecutorService)get(after,"executor");
            check(worker.awaitTermination(15,TimeUnit.SECONDS),"cancel worker timeout");
            check(onUi(()->get(rebuilt,"editor")==null),"Back did not close editor");
            check(mediaRows()==before,"cancel published media");
            check(Arrays.equals(original,Files.readAllBytes(file.toPath())),"source modified");
            result.putBoolean("in_progress_back_cancel",true);result.putBoolean("no_new_media",true);
            result.putBoolean("synthetic_original_preserved",true);
            ui(()->open(rebuilt,source));await(()->ready(editor(rebuilt)),15000);
            ui(()->rebuilt.getOnBackPressedDispatcher().onBackPressed());
            result.putBoolean("editor_reopens_after_cancel",true);
            check(rebuilt.getDisplay().getDisplayId()==1,"wrong display");
            result.putInt("display_id",1);result.putBoolean("synthetic_only",true);
        } catch(Exception failure) {result.putString("error",failure.toString());}
        finally {
            SystemCaptureActivity last=owner;
            if(last!=null) ui(last::finish);
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:previous.entrySet()) {
                String key=entry.getKey();Object value=entry.getValue();
                if(value instanceof String)restore.putString(key,(String)value);
                else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Integer)restore.putInt(key,(Integer)value);
                else if(value instanceof Long)restore.putLong(key,(Long)value);
                else if(value instanceof Float)restore.putFloat(key,(Float)value);
                else throw new IllegalStateException("Unsupported preference type");
            }
            check(restore.commit(),"preferences restore");
        }
        finish(result.containsKey("error")?0:-1,result);
    }
    private void ui(Runnable work) {runOnMainSync(work);}
    private boolean onUi(BooleanSupplier work) {
        AtomicReference<Boolean> result=new AtomicReference<>();ui(()->result.set(work.getAsBoolean()));return result.get();
    }
    private void await(BooleanSupplier work,long timeout)throws InterruptedException {
        long end=android.os.SystemClock.uptimeMillis()+timeout;
        while(!onUi(work)) {if(android.os.SystemClock.uptimeMillis()>=end)throw new IllegalStateException("UI wait timeout");Thread.sleep(50);}
    }
    private static boolean ready(PhotoEditor editor) {
        if(editor==null || (Boolean)get(editor,"busy"))return false;
        PhotoAdjustmentProcessor.Options current=new PhotoAdjustmentProcessor.Options(
                ((SeekBar)get(editor,"black")).getProgress(),((SeekBar)get(editor,"white")).getProgress(),
                ((CheckBox)get(editor,"denoise")).isChecked(),((CheckBox)get(editor,"detail")).isChecked(),
                ((CheckBox)get(editor,"hdr")).isChecked());
        return current.equals(get(editor,"processedOptions"));
    }
    private static SystemCaptureActivity active() {return (SystemCaptureActivity)get(null,SystemCaptureActivity.class,"active");}
    private static PhotoEditor editor(SystemCaptureActivity owner) {return (PhotoEditor)get(owner,"editor");}
    private static Object get(Object owner,String name) {return get(owner,owner.getClass(),name);}
    private static Object get(Object owner,Class<?> type,String name) {
        try {Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static void set(Object owner,String name,Object value) {
        try {Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private static void open(SystemCaptureActivity owner,Uri source) {
        try {Method method=SystemCaptureActivity.class.getDeclaredMethod("openEditor",Uri.class,Bundle.class);
            method.setAccessible(true);method.invoke(owner,source,null);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    private int mediaRows() {
        try(Cursor cursor=getTargetContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},MediaStore.MediaColumns.OWNER_PACKAGE_NAME+"=?",
                new String[]{getTargetContext().getPackageName()},null)) {
            if(cursor==null)throw new IllegalStateException("No media cursor");return cursor.getCount();
        }
    }
    private static void check(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
