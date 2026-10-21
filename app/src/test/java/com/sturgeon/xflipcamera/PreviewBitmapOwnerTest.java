package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import androidx.activity.ComponentActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Native Bitmap ownership only; no MediaStore, camera or device lifecycle claims. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PreviewBitmapOwnerTest {
    private ActivityController<ComponentActivity> activity;
    private ImageView view;
    private PreviewBitmapOwner owner;

    @Before public void setUp() {
        activity=Robolectric.buildActivity(ComponentActivity.class).setup();
        view=new ImageView(activity.get());owner=new PreviewBitmapOwner();
    }

    @After public void tearDown() {
        owner.close(view);activity.pause().stop().destroy();
    }

    @Test public void replacingDisplayedPreviewRecyclesOnlyThePreviousBitmap() {
        Bitmap first=bitmap(),second=bitmap();
        owner.show(view,first);owner.show(view,second);
        assertTrue(first.isRecycled());assertFalse(second.isRecycled());
        assertSame(second,((BitmapDrawable)view.getDrawable()).getBitmap());
    }

    @Test public void staleWorkerResultIsRecycledWithoutTouchingDisplayedPreview() {
        Bitmap displayed=bitmap(),stale=bitmap();owner.show(view,displayed);
        owner.discard(stale);owner.discard(displayed);
        assertTrue(stale.isRecycled());assertFalse(displayed.isRecycled());
        assertSame(displayed,((BitmapDrawable)view.getDrawable()).getBitmap());
    }

    @Test public void closeClearsDrawableAndRecyclesCurrentPreviewIdempotently() {
        Bitmap displayed=bitmap();owner.show(view,displayed);owner.close(view);owner.close(view);
        assertTrue(displayed.isRecycled());assertNull(view.getDrawable());
    }

    @Test public void recycledBitmapCannotBecomeDisplayedOwnership() {
        Bitmap recycled=bitmap();recycled.recycle();
        assertThrows(IllegalArgumentException.class,()->owner.show(view,recycled));
        assertNull(view.getDrawable());
    }

    private static Bitmap bitmap() { return Bitmap.createBitmap(17,13,Bitmap.Config.ARGB_8888); }
}
