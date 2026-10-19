package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;
import com.sturgeon.xflipcamera.core.ProcessingTimings;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowContentResolver;

/** Actual JPEG/EXIF with synthetic pixels and a fault-controlled MediaStore, never a real device. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, instrumentedPackages = {"androidx.exifinterface.media"},
        shadows = WatermarkProcessorTest.ExifFdShadow.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JpegPhotoWriterTest {
    private static final Uri SOURCE = Uri.parse("content://media/external/images/media/1");
    private static final Uri OLDER = Uri.parse("content://media/external/images/media/2");
    private static final String DESCRIPTION = "Synthetic processed JPEG, no private image";
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ContentResolver resolver;
    private WriterProvider provider;
    private File source, older;
    private byte[] olderBytes;

    @Before public void setUp() throws Exception {
        WatermarkProcessorTest.clearFixtureDescriptors();
        source = temporary.newFile("source.jpg"); fixture(1);
        older = temporary.newFile("older.jpg");
        olderBytes = Files.readAllBytes(source.toPath()); Files.write(older.toPath(), olderBytes);
        provider = new WriterProvider(temporary.getRoot(), source, older);
        ProviderInfo info = new ProviderInfo(); info.authority = "media";
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("media", provider);
        resolver = RuntimeEnvironment.getApplication().getContentResolver();
    }

    @Test public void normalizedWorkingPixelsAndCaptureMetadataArePublishedWithoutChangingInputs() throws Exception {
        String[] corners = {"RGBY", "GRYB", "YBGR", "BYRG", "RBGY", "BRYG", "YGBR", "GYRB"};
        for (int orientation = 1; orientation <= 8; orientation++) {
            fixture(orientation);
            byte[] original = Files.readAllBytes(source.toPath());
            Bitmap working = workingBitmap();
            try {
                int[] before = pixels(working);
                Uri result = write(working, () -> false);
                assertNotEquals(SOURCE, result); assertNotEquals(OLDER, result);
                assertArrayEquals(before, pixels(working));
                Bitmap saved = BitmapFactory.decodeFile(provider.files.get(result).getAbsolutePath());
                assertNotNull(saved);
                try {
                    assertEquals(orientation >= 5 ? 240 : 320, saved.getWidth());
                    assertEquals(orientation >= 5 ? 320 : 240, saved.getHeight());
                    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                        int expected = color(corners[orientation - 1].charAt(y * 2 + x));
                        int actual = saved.getPixel((2 * x + 1) * saved.getWidth() / 4,
                                (2 * y + 1) * saved.getHeight() / 4);
                        near(expected, actual);
                    }
                } finally { saved.recycle(); }
                ExifInterface metadata = new ExifInterface(provider.files.get(result));
                assertEquals(1, metadata.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0));
                assertEquals("Fixture Camera", metadata.getAttribute(ExifInterface.TAG_MAKE));
                assertEquals("Synthetic Model", metadata.getAttribute(ExifInterface.TAG_MODEL));
                assertEquals("2026:01:02 03:04:05", metadata.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
                assertEquals(0.01, metadata.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1), 0.00001);
                assertArrayEquals(new double[]{0, 0}, metadata.getLatLong(), 0.000001);
                assertEquals(DESCRIPTION, metadata.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION));
                assertTrue(metadata.getAttribute(ExifInterface.TAG_SOFTWARE).contains("XFlip Photo Processing"));
                assertEquals(Integer.valueOf(1), provider.lastInsert.getAsInteger(MediaStore.Images.Media.IS_PENDING));
                assertEquals(Integer.valueOf(0), provider.lastUpdate.getAsInteger(MediaStore.Images.Media.IS_PENDING));
                assertEquals("Pictures/XFlipCamera", provider.lastInsert.getAsString(MediaStore.Images.Media.RELATIVE_PATH));
                assertTrue(provider.visible.contains(result)); assertTrue(provider.pending.isEmpty());
                assertTrue("Fixture output descriptors close before publication", provider.closedBeforePublish);
                preserved(original);
            } finally { working.recycle(); }
        }
        assertEquals(0, provider.deletes);
        assertEquals(8, provider.updates);
    }

    @Test public void cancellationBeforeWritingDeletesOnlyNewPendingItem() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        try { assertThrows(InterruptedIOException.class, () -> write(bitmap, () -> true)); }
        finally { bitmap.recycle(); }
        rolledBack(original); assertEquals(0, provider.updates);
    }

    @Test public void cancellationAfterEncodingAndExifPreventsPublication() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        AtomicInteger checks = new AtomicInteger();
        try { assertThrows(InterruptedIOException.class, () -> write(bitmap, () -> checks.incrementAndGet() >= 2)); }
        finally { bitmap.recycle(); }
        assertEquals(2, checks.get()); assertTrue(provider.jpegExistedBeforeMetadata);
        rolledBack(original); assertEquals(0, provider.updates);
    }

    @Test public void unexpectedPublicationCountsDoNotReportSuccess() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        try {
            for (int count : new int[]{0, 2}) {
                provider.publishRows = count;
                IOException failure = assertThrows(IOException.class, () -> write(bitmap, () -> false));
                assertTrue(failure.getMessage().contains("updated " + count));
                assertTrue(provider.pending.isEmpty()); assertFalse(provider.destination.isFile()); preserved(original);
            }
        } finally { bitmap.recycle(); }
        assertEquals(2, provider.updates); assertEquals(2, provider.deletes);
    }

    @Test public void metadataReadDeniedAfterEncodingRollsBack() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        provider.denySource = true;
        try { assertThrows(FileNotFoundException.class, () -> write(bitmap, () -> false)); }
        finally { bitmap.recycle(); }
        rolledBack(original); assertEquals(0, provider.updates);
    }

    @Test public void outputWriteDeniedDoesNotDeleteAnOlderResult() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap(); provider.denyWrite = true;
        try { assertThrows(FileNotFoundException.class, () -> write(bitmap, () -> false)); }
        finally { bitmap.recycle(); }
        rolledBack(original); assertEquals(0, provider.updates);
    }

    @Test public void publicationErrorPropagatesSameInstanceAfterOwnedRollback() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        Error injected = new OutOfMemoryError("Injected provider error, no heap exhaustion");
        provider.publishError = injected;
        try { assertSame(injected, assertThrows(OutOfMemoryError.class, () -> write(bitmap, () -> false))); }
        finally { bitmap.recycle(); }
        assertTrue(provider.jpegExistedBeforeMetadata); rolledBack(original); assertEquals(1, provider.updates);
    }

    @Test public void cleanupErrorCannotReplaceOriginalPublicationFailure() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        provider.publishRows = 0; Error cleanup = new AssertionError("Injected cleanup error"); provider.cleanupError = cleanup;
        try {
            IOException primary = assertThrows(IOException.class, () -> write(bitmap, () -> false));
            assertTrue(primary.getMessage().contains("updated 0"));
            assertArrayEquals(new Throwable[]{cleanup}, primary.getSuppressed());
        } finally { bitmap.recycle(); }
        assertEquals(1, provider.deletes); assertEquals(1, provider.pending.size());
        assertTrue("Cleanup failure is not claimed as deletion", provider.destination.isFile()); preserved(original);
    }

    @Test public void cancellationAfterSuccessfulPublicationDoesNotDeleteCommittedCopy() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath()); Bitmap bitmap = workingBitmap();
        AtomicBoolean cancelled = new AtomicBoolean(); provider.onPublished = () -> cancelled.set(true);
        try {
            Uri saved = write(bitmap, cancelled::get);
            assertTrue(cancelled.get()); assertTrue(provider.visible.contains(saved));
            assertTrue(provider.files.get(saved).isFile()); assertTrue(provider.pending.isEmpty());
        } finally { bitmap.recycle(); }
        assertEquals(0, provider.deletes); preserved(original);
    }

    @Test public void profiledFullSaveMatchesUnprofiledJpegAndExifWithoutChangingSource() throws Exception {
        for (int orientation : new int[]{1,6}) {
            fixture(orientation);byte[] original = Files.readAllBytes(source.toPath());
            PhotoAdjustmentProcessor.Options options = new PhotoAdjustmentProcessor.Options(40,215,false,false,false);
            Uri plain = PhotoAdjustmentProcessor.save(resolver,SOURCE,options,()->false,message->{});
            ProcessingTimings timing = saveTiming();
            Uri profiled = PhotoAdjustmentProcessor.save(resolver,SOURCE,options,()->false,message->{},timing);
            assertNotEquals(plain,profiled);
            assertArrayEquals(Files.readAllBytes(provider.files.get(plain).toPath()),Files.readAllBytes(provider.files.get(profiled).toPath()));
            assertEquals(List.of(ProcessingTimings.Stage.DECODE,ProcessingTimings.Stage.INPUT_LEVELS,
                            ProcessingTimings.Stage.TONE_DETAIL,ProcessingTimings.Stage.JPEG_TRANSACTION,ProcessingTimings.Stage.BITMAP_RELEASE),
                    timing.finish().stages().stream().map(ProcessingTimings.Measurement::stage).toList());
            assertTrue(provider.visible.contains(plain));assertTrue(provider.visible.contains(profiled));
            assertTrue(provider.pending.isEmpty());preserved(original);
        }
        assertEquals(0,provider.deletes);
    }

    @Test public void profiledPublicationErrorRetainsOriginalErrorAndRollsBackOnlyOwnedCopy() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());
        AssertionError injected = new AssertionError("Synthetic profiled publish failure");provider.publishError = injected;
        ProcessingTimings timing = saveTiming();
        assertSame(injected,assertThrows(AssertionError.class,() -> PhotoAdjustmentProcessor.save(resolver,SOURCE,
                new PhotoAdjustmentProcessor.Options(40,215,false,false,false),()->false,message->{},timing)));
        assertEquals(ProcessingTimings.Stage.BITMAP_RELEASE,timing.finish().stages().get(4).stage());
        rolledBack(original);
    }

    @Test public void profiledCancellationAtWriteStillDeletesOnlyNewPendingRow() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());AtomicBoolean cancelled = new AtomicBoolean();
        ProcessingTimings timing = saveTiming();
        assertThrows(InterruptedIOException.class,() -> PhotoAdjustmentProcessor.save(resolver,SOURCE,
                new PhotoAdjustmentProcessor.Options(40,215,false,false,false),cancelled::get,message->cancelled.set(true),timing));
        assertEquals(ProcessingTimings.Stage.BITMAP_RELEASE,timing.finish().stages().get(4).stage());
        rolledBack(original);
    }

    private static ProcessingTimings saveTiming() {
        long[] samples = {0};
        return new ProcessingTimings(ProcessingTimings.Stage.DECODE,() -> {
            long n = samples[0]++;return new ProcessingTimings.Sample(n,n,n);
        });
    }

    private Uri write(Bitmap bitmap, BooleanSupplier cancelled) throws IOException {
        return JpegPhotoWriter.write(resolver, SOURCE, bitmap, "Synthetic_", DESCRIPTION, cancelled);
    }
    private Bitmap workingBitmap() throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, SOURCE), (decoder, info, data) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE); decoder.setMutableRequired(true);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
        });
    }
    private void rolledBack(byte[] original) throws IOException {
        assertEquals(1, provider.inserts); assertEquals(1, provider.deletes); assertTrue(provider.pending.isEmpty());
        assertFalse(provider.destination.isFile()); preserved(original);
    }
    private void preserved(byte[] original) throws IOException {
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
        assertArrayEquals(olderBytes, Files.readAllBytes(older.toPath()));
        assertTrue(provider.visible.contains(SOURCE)); assertTrue(provider.visible.contains(OLDER));
    }
    private void fixture(int orientation) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap); Paint paint = new Paint();
            int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) {
                paint.setColor(colors[y * 2 + x]); canvas.drawRect(160*x,120*y,160*(x+1),120*(y+1),paint);
            }
            try (FileOutputStream stream = new FileOutputStream(source)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,100,stream));
            }
        } finally { bitmap.recycle(); }
        ExifInterface exif = new ExifInterface(source);
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
        exif.setAttribute(ExifInterface.TAG_MAKE,"Fixture Camera"); exif.setAttribute(ExifInterface.TAG_MODEL,"Synthetic Model");
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2026:01:02 03:04:05");
        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,"1/100"); exif.setLatLong(0,0); exif.saveAttributes();
    }
    private static int[] pixels(Bitmap bitmap) {
        int[] values = new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(values,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight()); return values;
    }
    private static int color(char letter) {
        return switch(letter) { case 'R' -> Color.RED; case 'G' -> Color.GREEN; case 'B' -> Color.BLUE; case 'Y' -> Color.YELLOW;
            default -> throw new AssertionError("Invalid fixture color"); };
    }
    private static void near(int expected, int actual) {
        assertTrue(Math.abs(Color.red(expected)-Color.red(actual))<=20);
        assertTrue(Math.abs(Color.green(expected)-Color.green(actual))<=20);
        assertTrue(Math.abs(Color.blue(expected)-Color.blue(actual))<=20);
    }

    private static final class WriterProvider extends ContentProvider {
        final File directory;
        final Map<Uri,File> files = new HashMap<>();
        final Set<Uri> pending = new HashSet<>(), visible = new HashSet<>(Set.of(SOURCE, OLDER));
        final List<FileDescriptor> outputDescriptors = new ArrayList<>();
        int inserts, updates, deletes, publishRows = 1;
        boolean denySource, denyWrite, closedBeforePublish, jpegExistedBeforeMetadata;
        Error publishError, cleanupError;
        Runnable onPublished = () -> { };
        File destination;
        ContentValues lastInsert, lastUpdate;
        WriterProvider(File directory, File source, File older) {
            this.directory = directory; files.put(SOURCE,source); files.put(OLDER,older);
        }
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return "image/jpeg"; }
        @Override public Cursor query(Uri uri,String[] p,String s,String[] a,String o) { return null; }
        @Override public Uri insert(Uri uri,ContentValues values) {
            assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,uri);
            lastInsert = new ContentValues(values); inserts++;
            Uri result = Uri.withAppendedPath(uri,String.valueOf(inserts+2));
            destination = new File(directory,"output-"+inserts+".jpg"); files.put(result,destination); pending.add(result); return result;
        }
        @Override public int update(Uri uri,ContentValues values,String where,String[] args) {
            assertTrue(pending.contains(uri)); updates++; lastUpdate = new ContentValues(values);
            closedBeforePublish = outputDescriptors.stream().noneMatch(FileDescriptor::valid);
            if (publishError!=null) throw publishError;
            if (publishRows==1) { pending.remove(uri); visible.add(uri); onPublished.run(); }
            return publishRows;
        }
        @Override public int delete(Uri uri,String where,String[] args) {
            assertNotEquals(SOURCE,uri); assertNotEquals(OLDER,uri); deletes++;
            if(cleanupError!=null) throw cleanupError;
            File file = files.remove(uri); pending.remove(uri); visible.remove(uri);
            if(file!=null && file.exists()) assertTrue(file.delete()); return file==null?0:1;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
            if(SOURCE.equals(uri) || OLDER.equals(uri)) {
                assertEquals("Input media must be read only","r",mode);
                if(denySource && SOURCE.equals(uri)) throw new FileNotFoundException("Synthetic metadata read denial");
            } else if(denyWrite && !"r".equals(mode)) throw new FileNotFoundException("Synthetic output write denial");
            File file = files.get(uri); if(file==null) throw new FileNotFoundException("Unknown fixture URI");
            if("rw".equals(mode)) jpegExistedBeforeMetadata = file.length()>0;
            ParcelFileDescriptor fd = ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode));
            WatermarkProcessorTest.registerFixtureDescriptor(fd.getFileDescriptor(),file);
            if(!SOURCE.equals(uri) && !OLDER.equals(uri)) outputDescriptors.add(fd.getFileDescriptor());
            return fd;
        }
    }
}
