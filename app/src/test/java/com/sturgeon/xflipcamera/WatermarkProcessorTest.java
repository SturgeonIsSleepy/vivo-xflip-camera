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
import android.graphics.Paint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.exifinterface.media.ExifInterface;

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Real native graphics plus a fake owned MediaStore; no camera, phone, network image or real GPS. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatermarkProcessorTest {
    private static final Uri SOURCE = Uri.parse("content://media/external/images/media/1");
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private FixtureProvider provider;
    private ContentResolver resolver;
    private File source;

    @Before public void setUp() throws Exception {
        source = temporary.newFile("source.jpg");
        provider = new FixtureProvider(temporary.getRoot(), source);
        ProviderInfo info = new ProviderInfo();
        info.authority = "media";
        provider.attachInfo(RuntimeEnvironment.getApplication(), info);
        ShadowContentResolver.registerProviderInternal("media", provider);
        resolver = RuntimeEnvironment.getApplication().getContentResolver();
        fixture(1);
    }

    @Test public void allExifOrientationsPreserveCornerDirectionsAndCaptureMetadata() throws Exception {
        String[] expected = {"RGBY", "GRYB", "YBGR", "BYRG", "RBGY", "BRYG", "YGBR", "GYRB"};
        for (int orientation = 1; orientation <= 8; orientation++) {
            fixture(orientation);
            byte[] original = Files.readAllBytes(source.toPath());
            Uri result = WatermarkProcessor.mark(resolver, SOURCE, "XFlip TEST");
            assertNotEquals(SOURCE, result);
            assertArrayEquals(original, Files.readAllBytes(source.toPath()));
            Bitmap image = BitmapFactory.decodeFile(provider.files.get(result).getAbsolutePath());
            assertNotNull(image);
            try {
                assertEquals(orientation >= 5 ? 240 : 320, image.getWidth());
                assertEquals(orientation >= 5 ? 320 : 240, image.getHeight());
                int[] x = {image.getWidth() / 4, 3 * image.getWidth() / 4};
                int[] y = {image.getHeight() / 4, 3 * image.getHeight() / 4};
                for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) {
                    int color = color(expected[orientation - 1].charAt(2 * row + column));
                    near(color, image.getPixel(x[column], y[row]));
                }
                int white = 0, black = 0;
                for (int yy = image.getHeight() - 35; yy < image.getHeight(); yy++) {
                    for (int xx = 0; xx < image.getWidth(); xx++) {
                        int pixel = image.getPixel(xx, yy);
                        if (Color.red(pixel) > 220 && Color.green(pixel) > 220 && Color.blue(pixel) > 220) white++;
                        if (Color.red(pixel) < 15 && Color.green(pixel) < 15 && Color.blue(pixel) < 15) black++;
                    }
                }
                assertTrue("Caption has actual rasterized white glyphs", white > 10);
                assertTrue("Footer has actual dark pixels", black > 100);
            } finally {
                image.recycle();
            }
            ExifInterface after = new ExifInterface(provider.files.get(result));
            assertEquals(1, after.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0));
            assertEquals("Fixture Camera", after.getAttribute(ExifInterface.TAG_MAKE));
            assertEquals("Synthetic Model", after.getAttribute(ExifInterface.TAG_MODEL));
            assertEquals("2026:01:02 03:04:05", after.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
            assertEquals(0.01, after.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1), 0.00001);
            assertArrayEquals(new double[]{0, 0}, after.getLatLong(), 0.000001); // Synthetic only.
            assertTrue(after.getAttribute(ExifInterface.TAG_SOFTWARE).contains("user text"));
            assertEquals(Integer.valueOf(0), provider.lastUpdate.getAsInteger(MediaStore.Images.Media.IS_PENDING));
            assertEquals(Integer.valueOf(1), provider.lastInsert.getAsInteger(MediaStore.Images.Media.IS_PENDING));
        }
        assertEquals(0, provider.deletes);
    }

    @Test public void captionChangesRasterAndDoesNotChangeTheOriginal() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());
        Uri a = WatermarkProcessor.mark(resolver, SOURCE, "LEFT");
        Uri b = WatermarkProcessor.mark(resolver, SOURCE, "RIGHT");
        assertFalse(Arrays.equals(footer(a), footer(b)));
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
        assertTrue(provider.files.get(a).isFile());
        assertTrue(provider.files.get(b).isFile());
    }

    @Test public void invalidCaptionAndNonContentUriCreateNoOutput() {
        for (String caption : new String[]{null, "", "  ", "a\nb", "a\u202eb", "X".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> WatermarkProcessor.mark(resolver, SOURCE, caption));
        }
        assertThrows(IllegalArgumentException.class,
                () -> WatermarkProcessor.mark(resolver, Uri.fromFile(source), "TEST"));
        assertEquals(0, provider.inserts);
        assertEquals(0, provider.deletes);
    }

    @Test public void zeroRowPublicationRollsBackWithoutFalseSuccess() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());
        provider.publishRows = 0;
        IOException failure = assertThrows(IOException.class,
                () -> WatermarkProcessor.mark(resolver, SOURCE, "TEST"));
        assertTrue(failure.getMessage().contains("updated 0"));
        assertEquals(1, provider.deletes);
        assertFalse(provider.lastDestination.isFile());
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
    }

    @Test public void cleanupFailurePreservesThePublicationError() throws Exception {
        provider.publishRows = 0;
        provider.failCleanup = true;
        IOException failure = assertThrows(IOException.class,
                () -> WatermarkProcessor.mark(resolver, SOURCE, "TEST"));
        assertTrue(failure.getMessage().contains("updated 0"));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0] instanceof SecurityException);
        assertTrue(source.isFile());
    }

    @Test public void unreadableSourceCreatesNoOutput() {
        provider.denySource = true;
        assertThrows(FileNotFoundException.class, () -> WatermarkProcessor.mark(resolver, SOURCE, "TEST"));
        assertEquals(0, provider.inserts);
        assertEquals(0, provider.deletes);
    }

    @Test public void corruptSourceCreatesNoOutputAndIsUnchanged() throws Exception {
        byte[] broken = {1, 2, 3, 4, 5};
        Files.write(source.toPath(), broken);
        assertThrows(IOException.class, () -> WatermarkProcessor.mark(resolver, SOURCE, "TEST"));
        assertEquals(0, provider.inserts);
        assertArrayEquals(broken, Files.readAllBytes(source.toPath()));
    }

    @Test public void outputWriteFailureDeletesOnlyTheNewItem() throws Exception {
        byte[] original = Files.readAllBytes(source.toPath());
        provider.denyWrite = true;
        assertThrows(IOException.class, () -> WatermarkProcessor.mark(resolver, SOURCE, "TEST"));
        assertEquals(1, provider.inserts);
        assertEquals(1, provider.deletes);
        assertArrayEquals(original, Files.readAllBytes(source.toPath()));
    }

    private void fixture(int orientation) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW};
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++) {
                paint.setColor(colors[2 * row + column]);
                canvas.drawRect(160 * column, 120 * row, 160 * (column + 1), 120 * (row + 1), paint);
            }
            try (FileOutputStream stream = new FileOutputStream(source)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream));
            }
        } finally {
            bitmap.recycle();
        }
        ExifInterface metadata = new ExifInterface(source);
        metadata.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(orientation));
        metadata.setAttribute(ExifInterface.TAG_MAKE, "Fixture Camera");
        metadata.setAttribute(ExifInterface.TAG_MODEL, "Synthetic Model");
        metadata.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:02 03:04:05");
        metadata.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/100");
        metadata.setLatLong(0, 0);
        metadata.saveAttributes();
    }

    private int[] footer(Uri uri) {
        Bitmap bitmap = BitmapFactory.decodeFile(provider.files.get(uri).getAbsolutePath());
        try {
            int[] pixels = new int[bitmap.getWidth() * 35];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, bitmap.getHeight() - 35, bitmap.getWidth(), 35);
            return pixels;
        } finally {
            bitmap.recycle();
        }
    }

    private static int color(char name) {
        return switch (name) {
            case 'R' -> Color.RED;
            case 'G' -> Color.GREEN;
            case 'B' -> Color.BLUE;
            case 'Y' -> Color.YELLOW;
            default -> throw new AssertionError("Unknown fixture color");
        };
    }

    private static void near(int expected, int actual) {
        assertTrue(Math.abs(Color.red(expected) - Color.red(actual)) <= 20);
        assertTrue(Math.abs(Color.green(expected) - Color.green(actual)) <= 20);
        assertTrue(Math.abs(Color.blue(expected) - Color.blue(actual)) <= 20);
    }

    private static final class FixtureProvider extends ContentProvider {
        private final File directory;
        private final Map<Uri, File> files = new HashMap<>();
        private int inserts;
        private int deletes;
        private int publishRows = 1;
        private boolean failCleanup;
        private boolean denySource;
        private boolean denyWrite;
        private File lastDestination;
        private ContentValues lastInsert;
        private ContentValues lastUpdate;

        FixtureProvider(File directory, File source) {
            this.directory = directory;
            files.put(SOURCE, source);
        }
        @Override public boolean onCreate() { return true; }
        @Override public String getType(Uri uri) { return "image/jpeg"; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                String[] arguments, String order) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) {
            assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, uri);
            lastInsert = new ContentValues(values);
            Uri result = Uri.withAppendedPath(uri, String.valueOf(++inserts + 1));
            lastDestination = new File(directory, "output-" + inserts + ".jpg");
            files.put(result, lastDestination);
            return result;
        }
        @Override public int update(Uri uri, ContentValues values, String where, String[] arguments) {
            assertNotEquals(SOURCE, uri);
            lastUpdate = new ContentValues(values);
            return publishRows;
        }
        @Override public int delete(Uri uri, String where, String[] arguments) {
            assertNotEquals("The source must never be deleted", SOURCE, uri);
            deletes++;
            if (failCleanup) throw new SecurityException("Synthetic cleanup denial");
            File file = files.get(uri);
            return file != null && file.delete() ? 1 : 0;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            if (SOURCE.equals(uri)) {
                assertEquals("The source must only be opened read-only", "r", mode);
                if (denySource) throw new FileNotFoundException("Synthetic read denial");
            } else if (denyWrite && !"r".equals(mode)) {
                throw new FileNotFoundException("Synthetic write denial");
            }
            File file = files.get(uri);
            if (file == null) throw new FileNotFoundException("Unknown synthetic item");
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
        }
    }
}
