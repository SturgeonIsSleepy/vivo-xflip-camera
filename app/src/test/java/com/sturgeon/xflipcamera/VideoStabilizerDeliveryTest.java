package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

/** Fault-controlled published-result delivery only; no codec, MediaStore or device claims. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public final class VideoStabilizerDeliveryTest {
    private static final Uri RESULT = Uri.parse("content://stabilizer/result");
    private CleanupProvider provider;
    private VideoStabilizer stabilizer;
    private RecordingListener listener;

    @Before public void setUp() {
        provider = new CleanupProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = "stabilizer";
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("stabilizer", provider);
        stabilizer = new VideoStabilizer(RuntimeEnvironment.getApplication(), new DirectExecutorService());
        listener = new RecordingListener();
    }

    @Test public void firstActiveResultCompletesAndLaterDeliveryIsDiscarded() {
        stabilizer.dispatchPublished(RESULT, 7, listener);
        assertEquals(List.of(RESULT), listener.completed);
        assertTrue(provider.deleted.isEmpty());

        stabilizer.dispatchPublished(RESULT, 7, listener);
        assertEquals(List.of(RESULT), listener.completed);
        assertEquals(List.of(RESULT), provider.deleted);
    }

    @Test public void cancelledResultCleanupRuntimeFailureDoesNotReachMainCaller() {
        provider.failure = new SecurityException("Injected delete denial");
        stabilizer.cancel();

        stabilizer.dispatchPublished(RESULT, 7, listener);

        assertEquals(List.of(RESULT), provider.deleted);
        assertTrue(listener.completed.isEmpty());
        assertTrue(listener.failed.isEmpty());
    }

    @Test public void cancelledResultCleanupErrorRemainsFatal() {
        AssertionError failure = new AssertionError("Injected provider failure");
        provider.failure = failure;
        stabilizer.cancel();

        assertSame(failure, assertThrows(AssertionError.class,
                () -> stabilizer.dispatchPublished(RESULT, 7, listener)));
        assertEquals(List.of(RESULT), provider.deleted);
        assertTrue(listener.completed.isEmpty());
    }

    private static final class RecordingListener implements VideoStabilizer.Listener {
        final List<Uri> completed = new ArrayList<>();
        final List<String> failed = new ArrayList<>();
        @Override public void progress(String message) { }
        @Override public void completed(Uri result) { completed.add(result); }
        @Override public void failed(String reason) { failed.add(reason); }
    }

    private static final class CleanupProvider extends ContentProvider {
        final List<Uri> deleted = new ArrayList<>();
        Throwable failure;

        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) { return 0; }
        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            deleted.add(uri);
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            if (failure instanceof Error) throw (Error) failure;
            return 1;
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
        @Override public void execute(Runnable command) { command.run(); }
    }
}
