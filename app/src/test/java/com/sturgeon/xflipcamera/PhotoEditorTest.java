package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import androidx.activity.ComponentActivity;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

/** Controlled UI/worker ordering with real widgets/Bitmaps; never a device or OpenCV quality test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class PhotoEditorTest {
    private static final Uri SOURCE = Uri.parse("content://fixture/original");
    private ActivityController<ComponentActivity> activity;
    private ComponentActivity owner;
    private PhotoEditor editor;
    private ManualExecutor worker;
    private FixtureProcessor processor;
    private Button compare, save;
    private SeekBar black;
    private final List<Uri> completed = new ArrayList<>();

    @Before public void setUp() {
        activity=Robolectric.buildActivity(ComponentActivity.class).setup();owner=activity.get();
        owner.setTheme(android.R.style.Theme_Material_NoActionBar);
        worker=new ManualExecutor();processor=new FixtureProcessor();
        editor=new PhotoEditor(owner,SOURCE,null,completed::add,worker,processor);
        compare=button("原图");save=button("另存");black=slider("黑场");
        worker.runNext();idle();advanceDebounce();worker.runNext();idle();
        black.setProgress(10);advanceDebounce();worker.runNext();idle();
        assertTrue("Fixture has a completed changed preview",save.isEnabled());
    }

    @After public void tearDown() {
        if(editor!=null)editor.close();
        if(activity!=null)activity.pause().stop().destroy();
        idle();
    }

    @Test public void compareCannotEnableSaveBeforeCurrentPreviewCompletes() {
        black.setProgress(20);
        assertFalse(save.isEnabled());
        compare.performClick();compare.performClick();
        assertFalse("Old preview must not enable save of new settings",save.isEnabled());
        advanceDebounce();worker.runNext();idle();
        assertTrue(save.isEnabled());assertEquals(20,Color.red(displayed().getPixel(0,0)));
    }

    @Test public void failedPreviewCannotSaveThroughCompare() {
        processor.failPreview=true;black.setProgress(20);
        advanceDebounce();worker.runNext();idle();
        assertFalse(save.isEnabled());
        compare.performClick();compare.performClick();
        assertFalse("Failed settings have no matching preview",save.isEnabled());
        assertTrue(processor.working.get(processor.working.size()-1).isRecycled());
        processor.failPreview=false;black.setProgress(21);advanceDebounce();worker.runNext();idle();
        assertTrue(save.isEnabled());assertEquals(Color.BLUE,processor.original.getPixel(0,0));
    }

    @Test public void saveEntryRejectsUnpreviewedSettingsEvenIfListenerIsCalled() {
        black.setProgress(20);save.callOnClick();
        assertEquals(0,worker.queue.size());assertEquals(0,processor.saveCalls);
        assertTrue(completed.isEmpty());
    }

    @Test public void failedSaveCanRetryTheStillMatchingPreview() {
        processor.failSave=true;save.performClick();worker.runNext();idle();
        assertTrue(save.isEnabled());assertFalse(processor.original.isRecycled());
        processor.failSave=false;save.performClick();worker.runNext();idle();
        assertEquals(2,processor.saveCalls);
        assertEquals(List.of(Uri.parse("content://fixture/new-copy")),completed);
        assertTrue(processor.original.isRecycled());
    }

    @Test public void staleCompletedPreviewCannotReplaceNewerParameters() {
        black.setProgress(20);advanceDebounce();worker.runNext();
        Bitmap stale=processor.working.get(processor.working.size()-1);
        black.setProgress(30);idle();
        assertTrue(stale.isRecycled());assertFalse(save.isEnabled());
        assertEquals(10,Color.red(displayed().getPixel(0,0)));
        advanceDebounce();worker.runNext();idle();
        assertEquals(30,Color.red(displayed().getPixel(0,0)));assertTrue(save.isEnabled());
    }

    @Test public void queuedSupersededPreviewsSkipTheProcessor() {
        int before=processor.applyCalls;
        for(int value=20;value<32;value++){black.setProgress(value);advanceDebounce();}
        assertEquals(before,processor.applyCalls);assertEquals(12,worker.queue.size());
        while(!worker.queue.isEmpty()){worker.runNext();idle();}
        assertEquals(before+1,processor.applyCalls);
        assertEquals(31,Color.red(displayed().getPixel(0,0)));assertTrue(save.isEnabled());
    }

    @Test public void closeBeforeDequeuedPreviewStartsDoesNotReadRecycledOriginal() {
        int before=processor.applyCalls;
        black.setProgress(20);advanceDebounce();Runnable dequeued=worker.queue.removeFirst();
        editor.close();assertTrue(processor.original.isRecycled());
        dequeued.run();idle();
        assertEquals(before,processor.applyCalls);assertTrue(completed.isEmpty());
    }

    @Test public void closeRecyclesPreviewWaitingForMainThreadDelivery() {
        black.setProgress(20);advanceDebounce();worker.runNext();
        Bitmap pending=processor.working.get(processor.working.size()-1);assertFalse(pending.isRecycled());
        editor.close();assertTrue(pending.isRecycled());idle();assertTrue(completed.isEmpty());
    }

    @Test public void closeRecyclesDecodedOriginalBeforeItsMainCallback() {
        editor.close();worker=new ManualExecutor();processor=new FixtureProcessor();
        editor=new PhotoEditor(owner,SOURCE,null,completed::add,worker,processor);
        worker.runNext();assertFalse(processor.original.isRecycled());
        editor.close();assertTrue(processor.original.isRecycled());idle();assertTrue(completed.isEmpty());
    }

    @Test public void editorBundleRestoresSettingsInNewOwnerUsingOriginalSource() {
        black.setProgress(20);slider("白场").setProgress(200);
        ((CheckBox)find(owner.findViewById(android.R.id.content),v->v instanceof CheckBox b
                && "OpenCV NLM降噪".contentEquals(b.getText()))).setChecked(true);
        Bundle state=editor.saveState();editor.close();activity.pause().stop().destroy();
        activity=Robolectric.buildActivity(ComponentActivity.class).setup();owner=activity.get();
        owner.setTheme(android.R.style.Theme_Material_NoActionBar);
        worker=new ManualExecutor();processor=new FixtureProcessor();
        editor=new PhotoEditor(owner,SOURCE,state,completed::add,worker,processor);
        assertEquals(20,slider("黑场").getProgress());assertEquals(200,slider("白场").getProgress());
        worker.runNext();idle();advanceDebounce();worker.runNext();idle();
        assertTrue(processor.lastOptions.denoise());assertEquals(20,processor.lastOptions.black());
        assertEquals(200,processor.lastOptions.white());assertTrue(button("另存").isEnabled());
        assertEquals(Color.BLUE,processor.original.getPixel(0,0));
    }

    private Bitmap displayed() {
        ImageView view=(ImageView)find(owner.findViewById(android.R.id.content),v->v instanceof ImageView);
        return ((BitmapDrawable)view.getDrawable()).getBitmap();
    }

    private Button button(String text) {
        return (Button)find(owner.findViewById(android.R.id.content),v->v instanceof Button b && text.contentEquals(b.getText()));
    }
    private SeekBar slider(String description) {
        return (SeekBar)find(owner.findViewById(android.R.id.content),v->v instanceof SeekBar && description.contentEquals(v.getContentDescription()));
    }
    private interface Match { boolean accepts(View view); }
    private static View find(View view, Match match) {
        if(match.accepts(view))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++) {
            View result=find(group.getChildAt(i),match);if(result!=null)return result;
        }
        return null;
    }
    private static void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    private static void advanceDebounce() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120)); }

    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queue=new ArrayDeque<>();
        private boolean stopped;
        @Override public void execute(Runnable task) {
            if(stopped)throw new RejectedExecutionException();queue.addLast(task);
        }
        void runNext() { assertFalse("Expected a queued worker task",queue.isEmpty());queue.removeFirst().run(); }
        @Override public void shutdown() { stopped=true; }
        @Override public List<Runnable> shutdownNow() {
            stopped=true;List<Runnable> tasks=new ArrayList<>(queue);queue.clear();return tasks;
        }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped && queue.isEmpty(); }
        @Override public boolean awaitTermination(long timeout,TimeUnit unit) { return isTerminated(); }
    }

    private static final class FixtureProcessor implements PhotoEditor.Processor {
        boolean failPreview,failSave;
        int applyCalls,saveCalls;
        Bitmap original;
        PhotoAdjustmentProcessor.Options lastOptions;
        final List<Bitmap> working=new ArrayList<>();
        @Override public Bitmap decode(ContentResolver resolver,Uri source,int size) {
            assertEquals(SOURCE,source);assertEquals(960,size);
            original=Bitmap.createBitmap(64,48,Bitmap.Config.ARGB_8888);original.eraseColor(Color.BLUE);return original;
        }
        @Override public void apply(Bitmap bitmap,PhotoAdjustmentProcessor.Options options,
                BooleanSupplier cancelled) throws IOException {
            applyCalls++;working.add(bitmap);lastOptions=options;
            if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled fixture");
            if(failPreview)throw new IOException("Fixture preview failure");
            bitmap.eraseColor(Color.rgb(options.black(),options.white(),117));
        }
        @Override public Uri save(ContentResolver resolver,Uri source,PhotoAdjustmentProcessor.Options options,
                BooleanSupplier cancelled,Consumer<String> progress) throws IOException {
            assertEquals(SOURCE,source);saveCalls++;
            if(cancelled.getAsBoolean())throw new InterruptedIOException("Cancelled fixture");
            if(failSave)throw new IOException("Fixture save failure");
            return Uri.parse("content://fixture/new-copy");
        }
    }
}
