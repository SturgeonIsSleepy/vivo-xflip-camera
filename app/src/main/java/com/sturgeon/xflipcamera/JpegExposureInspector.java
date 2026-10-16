package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.WorkerThread;
import androidx.exifinterface.media.ExifInterface;
import com.sturgeon.xflipcamera.core.BoundedDigestInputStream;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Frame;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Result;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Status;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Read-only metadata screening. No camera operation, RGB decode, new media, log, upload or HDR fusion. */
public final class JpegExposureInspector {
    public static final int MAX_INPUT_BYTES = 32 * 1024 * 1024;
    private JpegExposureInspector() { }

    @WorkerThread public static Result inspect(ContentResolver resolver, List<Uri> sources,
            BooleanSupplier cancelled) throws IOException {
        Objects.requireNonNull(resolver, "resolver"); Objects.requireNonNull(cancelled, "cancelled");
        if (sources == null || sources.size() != 3) return ExposureBracketAssessment.rejected(Status.INPUT_COUNT);
        List<Uri> inputs = new ArrayList<>(sources);
        for (Uri uri : inputs) if (uri == null || !"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Select three original JPEG content URIs");
        }
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("JPEG metadata inspection cancelled");
        if (new HashSet<>(inputs).size() != 3) return ExposureBracketAssessment.rejected(Status.DUPLICATE_FILE);
        List<Frame> frames = new ArrayList<>(3);
        for (Uri input : inputs) frames.add(read(resolver, input, cancelled));
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("JPEG metadata inspection cancelled");
        return ExposureBracketAssessment.assess(frames);
    }

    private static Frame read(ContentResolver resolver, Uri uri, BooleanSupplier cancelled) throws IOException {
        try (InputStream source = open(resolver, uri);
                BoundedDigestInputStream raw = new BoundedDigestInputStream(source, MAX_INPUT_BYTES, cancelled)) {
            // Parser rewinds only this buffer. Digest/budget below it sees each physical byte once.
            BufferedInputStream buffered = new BufferedInputStream(raw, 16 * 1024);
            buffered.mark(3);
            if (buffered.read() != 0xff || buffered.read() != 0xd8 || buffered.read() != 0xff) {
                throw new IOException("Selected input is not a JPEG");
            }
            buffered.reset();
            ExifInterface exif = new ExifInterface(buffered);
            String fingerprint = raw.finishDigest(); // Sticky parser I/O/budget/cancel errors cannot become candidates.
            String make = exif.getAttribute(ExifInterface.TAG_MAKE), model = exif.getAttribute(ExifInterface.TAG_MODEL);
            String camera = make == null || model == null || make.isBlank() || model.isBlank()
                    ? null : make.trim() + "\u0000" + model.trim();
            return new Frame(exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
                    exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1),
                    exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN),
                    exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN),
                    exif.getAttributeDouble(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, Double.NaN),
                    exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN),
                    exif.getAttributeInt(ExifInterface.TAG_FLASH, -1), camera, fingerprint);
        }
    }
    private static InputStream open(ContentResolver resolver, Uri uri) throws IOException {
        InputStream stream = resolver.openInputStream(uri);
        if (stream == null) throw new IOException("Cannot read original JPEG");
        return stream;
    }
}
