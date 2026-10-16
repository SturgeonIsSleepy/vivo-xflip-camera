package com.sturgeon.xflipcamera;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.exifinterface.media.ExifInterface;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Result;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Status;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

/** Synthetic JPEG/EXIF only. These tests cannot prove real camera exposure or HDR quality. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class JpegExposureInspectorTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private final List<Uri> inputs=List.of(Uri.parse("content://exposure-fixture/1"),
            Uri.parse("content://exposure-fixture/2"),Uri.parse("content://exposure-fixture/3"));
    private final List<File> files=new ArrayList<>();
    private ReadOnlyProvider provider;
    private ContentResolver resolver;

    @Before public void setUp() throws Exception {
        for(int i=0;i<3;i++){files.add(temporary.newFile("synthetic-"+i+".jpg"));fixture(i);}
        provider=new ReadOnlyProvider();for(int i=0;i<3;i++)provider.files.put(inputs.get(i),files.get(i));
        ProviderInfo info=new ProviderInfo();info.authority="exposure-fixture";
        provider.attachInfo(RuntimeEnvironment.getApplication(),info);
        ShadowContentResolver.registerProviderInternal(info.authority,provider);
        resolver=RuntimeEnvironment.getApplication().getContentResolver();
    }

    @Test public void exposureMetadataCandidateUsesOriginalInputIndicesWithoutPublishingAnything() throws Exception {
        List<String> before=fingerprints();
        Result result=JpegExposureInspector.inspect(resolver,List.of(inputs.get(2),inputs.get(0),inputs.get(1)),()->false);
        assertEquals(Status.METADATA_CANDIDATE_ONLY,result.status());assertEquals(List.of(1,2,0),result.sourceOrder());
        assertEquals(2,result.referenceIndex());assertEquals(2,result.lowerCaptureEv(),0.000001);
        assertEquals(2,result.upperGainEv(),0.000001);
        assertFalse(result.toString().contains("content://"));assertFalse(result.toString().contains("Fixture Camera"));
        preserved(before);
    }

    @Test public void sameExposureAndIsoOnlyChangesAreNotAccepted() throws Exception {
        for(boolean isoOnly:new boolean[]{false,true}) {
            for(int i=0;i<3;i++){
                attribute(i,ExifInterface.TAG_EXPOSURE_TIME,"1/250");
                attribute(i,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,String.valueOf(isoOnly?100*(1<<(i*2)):100));
                attribute(i,ExifInterface.TAG_EXPOSURE_BIAS_VALUE,String.valueOf((i-1)*2));
            }
            List<String> before=fingerprints();assertEquals(Status.INSUFFICIENT_CAPTURE_SPAN,inspect().status());preserved(before);
        }
    }

    @Test public void missingExposureOrCameraMetadataFailsClosed() throws Exception {
        for(String tag:new String[]{ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_MODEL}) {
            fixture(1);attribute(1,tag,null);List<String> before=fingerprints();
            assertEquals(Status.MISSING_OR_INVALID_METADATA,inspect().status());preserved(before);
        }
    }

    @Test public void duplicateUrisAndByteCopiesAreRejectedButDigestIncludesIgnoredTail() throws Exception {
        assertEquals(Status.DUPLICATE_FILE,JpegExposureInspector.inspect(resolver,List.of(inputs.get(0),inputs.get(0),inputs.get(2)),()->false).status());
        assertEquals(0,provider.opens);
        Files.copy(files.get(0).toPath(),files.get(1).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        List<String> before=fingerprints();assertEquals(Status.DUPLICATE_FILE,inspect().status());preserved(before);
        // Same header/pixels, different complete bytes: the digest must include data after EXIF/SOS/EOI.
        Files.write(files.get(1).toPath(),new byte[]{42},StandardOpenOption.APPEND);
        before=fingerprints();assertEquals(Status.INSUFFICIENT_CAPTURE_SPAN,inspect().status());preserved(before);
    }

    @Test public void flashLensAndNormalizedGeometryAreChecked() throws Exception {
        String[] tags={ExifInterface.TAG_FLASH,ExifInterface.TAG_FOCAL_LENGTH,ExifInterface.TAG_ORIENTATION};
        String[] values={"1","8/1","6"};
        Status[] expected={Status.FLASH_OR_UNKNOWN,Status.CAMERA_OR_LENS_CHANGED,Status.INCONSISTENT_GEOMETRY};
        for(int i=0;i<tags.length;i++){
            fixture(1);attribute(1,tags[i],values[i]);List<String> before=fingerprints();
            assertEquals(expected[i],inspect().status());preserved(before);
        }
    }

    @Test public void invalidCountAndNonContentUriCauseNoProviderOperations() throws Exception {
        assertEquals(Status.INPUT_COUNT,JpegExposureInspector.inspect(resolver,List.of(),()->false).status());
        assertThrows(IllegalArgumentException.class,()->JpegExposureInspector.inspect(resolver,
                List.of(inputs.get(0),Uri.fromFile(files.get(1)),inputs.get(2)),()->false));
        assertThrows(IllegalArgumentException.class,()->JpegExposureInspector.inspect(resolver,
                Arrays.asList(inputs.get(0),null,inputs.get(2)),()->false));
        assertEquals(0,provider.opens);assertEquals(0,provider.writes);
    }

    @Test public void revokedReadAndCorruptInputNeverYieldPartialPositiveResult() throws Exception {
        List<String> before=fingerprints();provider.denied=inputs.get(1);
        assertThrows(FileNotFoundException.class,this::inspect);preserved(before);
        provider.securityDenial=true;
        assertThrows(SecurityException.class,this::inspect);preserved(before);
        provider.denied=null;Files.write(files.get(2).toPath(),new byte[]{(byte)137,80,78,71,13,10});
        before=fingerprints();assertThrows(IOException.class,this::inspect);preserved(before);
    }

    @Test public void cancellationBeforeOpenAndInsideReadClosesTheInput() throws Exception {
        List<String> before=fingerprints();
        assertThrows(InterruptedIOException.class,()->JpegExposureInspector.inspect(resolver,inputs,()->true));
        assertEquals(0,provider.opens);
        AtomicBoolean cancelled=new AtomicBoolean();provider.onOpen=()->cancelled.set(true);
        assertThrows(InterruptedIOException.class,()->JpegExposureInspector.inspect(resolver,inputs,cancelled::get));
        assertEquals(1,provider.opens);preserved(before);
    }

    @Test public void byteBudgetAfterValidExifCannotBeSwallowedByTheParser() throws Exception {
        byte[] block=new byte[16*1024];
        try(FileOutputStream out=new FileOutputStream(files.get(0),true)) {
            while(files.get(0).length()<=JpegExposureInspector.MAX_INPUT_BYTES)out.write(block);
        }
        List<String> before=fingerprints();
        IOException failure=assertThrows(IOException.class,this::inspect);
        assertTrue(failure.getMessage().contains("budget"));preserved(before);
    }

    @Test public void editedExifOnIdenticalPixelsIsStillOnlyAMetadataCandidateNotCaptureProof() throws Exception {
        byte[] same=Files.readAllBytes(files.get(0).toPath());
        for(int i=0;i<3;i++) {
            Files.write(files.get(i).toPath(),same);
            attribute(i,ExifInterface.TAG_EXPOSURE_TIME,new String[]{"1/1000","1/250","2/125"}[i]);
        }
        List<String> before=fingerprints();assertEquals(Status.METADATA_CANDIDATE_ONLY,inspect().status());
        // A different file hash/EXIF ladder cannot establish different sensor measurements.
        preserved(before);
    }

    private Result inspect() throws IOException{return JpegExposureInspector.inspect(resolver,inputs,()->false);}
    private void fixture(int index) throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);
        try {
            int level=new int[]{32,96,192}[index];bitmap.eraseColor(Color.rgb(level,level,level));
            try(FileOutputStream out=new FileOutputStream(files.get(index))){assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,98,out));}
        } finally {bitmap.recycle();}
        ExifInterface exif=new ExifInterface(files.get(index));
        exif.setAttribute(ExifInterface.TAG_MAKE,"Fixture Camera");exif.setAttribute(ExifInterface.TAG_MODEL,"Synthetic Model");
        exif.setAttribute(ExifInterface.TAG_ORIENTATION,"1");exif.setAttribute(ExifInterface.TAG_FLASH,"0");
        exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,new String[]{"1/1000","1/250","2/125"}[index]);
        exif.setAttribute(ExifInterface.TAG_F_NUMBER,"2/1");exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH,"4/1");
        exif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,"100");exif.saveAttributes();
    }
    private void attribute(int index,String tag,String value) throws Exception {
        ExifInterface exif=new ExifInterface(files.get(index));exif.setAttribute(tag,value);exif.saveAttributes();
    }
    private List<String> fingerprints() throws Exception {
        List<String> result=new ArrayList<>();byte[] buffer=new byte[16*1024];
        for(File file:files) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream stream=Files.newInputStream(file.toPath())) {int n;while((n=stream.read(buffer))!=-1)digest.update(buffer,0,n);}
            StringBuilder hex=new StringBuilder(64);
            for(byte value:digest.digest()) {hex.append(Character.forDigit((value&255)>>>4,16));hex.append(Character.forDigit(value&15,16));}
            result.add(hex.toString());
        }
        return result;
    }
    private void preserved(List<String> before) throws Exception {
        assertEquals(before,fingerprints());assertEquals(0,provider.writes);
        assertTrue("Fixture descriptors must close on every exit",provider.descriptors.stream().noneMatch(FileDescriptor::valid));
    }
    private static final class ReadOnlyProvider extends ContentProvider {
        final Map<Uri,File> files=new HashMap<>();final List<FileDescriptor> descriptors=new ArrayList<>();
        int opens,writes;boolean securityDenial;Uri denied;Runnable onOpen=()->{};
        @Override public boolean onCreate(){return true;}
        @Override public String getType(Uri uri){return "image/jpeg";}
        @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
        @Override public Uri insert(Uri uri,ContentValues v){writes++;throw new AssertionError("Read-only inspector inserted output");}
        @Override public int update(Uri uri,ContentValues v,String s,String[] a){writes++;throw new AssertionError("Read-only inspector changed media");}
        @Override public int delete(Uri uri,String s,String[] a){writes++;throw new AssertionError("Read-only inspector deleted media");}
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
            assertEquals("r",mode);opens++;
            if(uri.equals(denied)){
                if(securityDenial)throw new SecurityException("Synthetic grant revocation");
                throw new FileNotFoundException("Synthetic read denial");
            }
            File file=files.get(uri);if(file==null)throw new FileNotFoundException("Unknown fixture input");
            ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
            descriptors.add(fd.getFileDescriptor());onOpen.run();return fd;
        }
    }
}
