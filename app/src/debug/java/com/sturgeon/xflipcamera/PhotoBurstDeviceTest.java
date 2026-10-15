package com.sturgeon.xflipcamera;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Synthetic fixtures only; invokes the production Android alignment, fusion, metadata and publisher. */
public final class PhotoBurstDeviceTest extends Instrumentation {
    private String scenario;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        scenario = arguments.getString("scenario", "good");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!List.of("good", "moving", "unrelated", "rotated", "duplicate", "cancel").contains(scenario)) {
                throw new IllegalArgumentException("Unknown synthetic scenario");
            }
            getTargetContext().startActivity(new Intent(getTargetContext(), SystemCaptureActivity.class)
                    .putExtra("manual_entry",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(1).toBundle());
            File directory = new File(getTargetContext().getFilesDir(), "burst-fixtures");
            List<Uri> sources = new ArrayList<>(); List<byte[]> hashes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                File file = new File(directory,scenario + "-" + i + ".jpg");
                hashes.add(hash(file)); sources.add(Uri.fromFile(file));
            }
            long start = System.nanoTime();
            boolean shouldReject = List.of("unrelated","duplicate","cancel").contains(scenario);
            try {
                PhotoBurstProcessor.Result merged = PhotoBurstProcessor.merge(getTargetContext().getContentResolver(), sources,
                        () -> scenario.equals("cancel"), message -> { });
                if (shouldReject) throw new IllegalStateException("Invalid input unexpectedly published");
                File output = new File(directory,scenario + "-result.jpg");
                try (InputStream source = getTargetContext().getContentResolver().openInputStream(merged.uri())) {
                    if (source == null) throw new IllegalStateException("No merged JPEG");
                    Files.copy(source, output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                android.graphics.Bitmap single = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getTargetContext().getContentResolver(), sources.get(merged.referenceIndex())),
                        (decoder, info, source) -> {
                            decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
                            decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB));
                        });
                try (java.io.OutputStream baseline = Files.newOutputStream(new File(directory,scenario + "-baseline.jpg").toPath())) {
                    if (!single.compress(android.graphics.Bitmap.CompressFormat.JPEG,97,baseline)) throw new IllegalStateException("Baseline encode failed");
                } finally { single.recycle(); }
                result.putInt("accepted_frames",merged.acceptedFrames());
                result.putInt("reference_index",merged.referenceIndex());
                result.putDouble("noise_sigma",merged.noiseSigma());
                if (getTargetContext().getContentResolver().delete(merged.uri(),null,null) != 1) {
                    throw new IllegalStateException("Synthetic output cleanup failed");
                }
                result.putString("output_uri",merged.uri().toString());
            } catch (java.io.IOException expected) {
                if (!shouldReject) throw expected;
                result.putString("rejection",expected.getMessage());
            }
            for (int i=0;i<3;i++) if (!Arrays.equals(hashes.get(i), hash(new File(directory,scenario+"-"+i+".jpg")))) {
                throw new IllegalStateException("Original fixture changed");
            }
            result.putBoolean("originals_preserved",true);
            result.putLong("processing_ms",(System.nanoTime()-start)/1_000_000);
        } catch (Exception failure) { result.putString("error",failure.toString()); }
        finish(result.containsKey("error") ? 0 : -1,result);
    }
    private static byte[] hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] data = new byte[32768]; int count;
            while((count=input.read(data))!=-1) digest.update(data,0,count);
        }
        return digest.digest();
    }
}
