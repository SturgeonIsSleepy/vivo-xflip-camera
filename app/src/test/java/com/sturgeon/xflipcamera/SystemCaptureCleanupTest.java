package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

/** Fault-controlled ContentResolver cleanup only; no camera, MediaStore or device claims. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public final class SystemCaptureCleanupTest {
    private static final Uri PHOTO = Uri.parse("content://cleanup/cache/photo");
    private static final Uri OUTPUT = Uri.parse("content://cleanup/pending/output");
    private ActivityController<SystemCaptureActivity> controller;
    private SystemCaptureActivity activity;
    private CleanupProvider provider;

    @Before public void setUp() throws Exception {
        provider = new CleanupProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = "cleanup";
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("cleanup", provider);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), SystemCaptureActivity.class)
                .putExtra("manual_entry", true);
        controller = Robolectric.buildActivity(SystemCaptureActivity.class, intent).setup();
        activity = controller.get();
        field("photo").set(activity, PHOTO);
        field("output").set(activity, OUTPUT);
    }

    @After public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test public void providerRuntimeFailureCannotBlockSecondDeleteOrStateDetach() throws Exception {
        SecurityException failure = new SecurityException("Injected cache denial");
        provider.firstFailure = failure;
        activity.discardPendingPhoto();
        assertEquals(List.of(PHOTO, OUTPUT), provider.deleted);
        assertNull(field("photo").get(activity));
        assertNull(field("output").get(activity));
    }

    @Test public void providerErrorRunsAllDeletesAndRethrowsAfterStateDetach() throws Exception {
        AssertionError failure = new AssertionError("Injected provider failure");
        provider.firstFailure = failure;
        assertSame(failure, assertThrows(AssertionError.class, activity::discardPendingPhoto));
        assertEquals(List.of(PHOTO, OUTPUT), provider.deleted);
        assertNull(field("photo").get(activity));
        assertNull(field("output").get(activity));
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = SystemCaptureActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class CleanupProvider extends ContentProvider {
        final List<Uri> deleted = new ArrayList<>();
        Throwable firstFailure;

        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) { return 0; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            deleted.add(uri);
            if (deleted.size() == 1) {
                if (firstFailure instanceof RuntimeException) throw (RuntimeException) firstFailure;
                if (firstFailure instanceof Error) throw (Error) firstFailure;
            }
            return 1;
        }
    }
}
