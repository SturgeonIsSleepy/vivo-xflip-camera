package com.sturgeon.xflipcamera;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.sturgeon.xflipcamera.core.MultiFrameAccumulator;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.features2d.BFMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Registered three-photo SDR JPEG fusion, with conservative motion rejection and original preservation. */
public final class PhotoBurstProcessor {
    public record Result(Uri uri, int acceptedFrames, int referenceIndex, double noiseSigma) { }
    private PhotoBurstProcessor() { }

    public static Result merge(ContentResolver resolver, List<Uri> sources, BooleanSupplier cancelled,
            Consumer<String> progress) throws IOException {
        return merge(resolver,sources,cancelled,progress,PhotoEnhancer.Options.NONE);
    }

    public static Result merge(ContentResolver resolver, List<Uri> sources, BooleanSupplier cancelled,
            Consumer<String> progress, PhotoEnhancer.Options options) throws IOException {
        if (sources.size() != 3 || new HashSet<>(sources).size() != 3) throw new IOException("需要三张不同的原图");
        if (!OpenCVLoader.initLocal()) throw new IOException("OpenCV 初始化失败");
        Core.setNumThreads(2);
        long started = System.nanoTime();
        List<Mat> gray = new ArrayList<>();
        Set<String> hashes = new HashSet<>();
        int reference = 0;
        double best = -1;
        Bitmap output = null;
        try {
            for (int i = 0; i < 3; i++) {
                check(cancelled);
                MessageDigest digest;
                try { digest = MessageDigest.getInstance("SHA-256"); }
                catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
                try (InputStream stream = resolver.openInputStream(sources.get(i))) {
                    if (stream == null) throw new IOException("原图不可读取");
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = stream.read(buffer)) != -1) { check(cancelled); digest.update(buffer, 0, count); }
                }
                String fingerprint = Base64.getEncoder().encodeToString(digest.digest());
                if (!hashes.add(fingerprint)) throw new IOException("输入文件重复，未进行多帧合成");
                Bitmap small = decode(resolver, sources.get(i), 1024);
                Mat rgba = new Mat(), mono = new Mat();
                try { Utils.bitmapToMat(small, rgba); Imgproc.cvtColor(rgba, mono, Imgproc.COLOR_RGBA2GRAY); }
                finally { rgba.release(); small.recycle(); }
                gray.add(mono);
                double score = sharpness(mono);
                if (score > best) { best = score; reference = i; }
            }
            if (best < 8) throw new IOException("画面纹理不足，保留原图");
            Bitmap anchor = decode(resolver, sources.get(reference), 0);
            int width = anchor.getWidth(), height = anchor.getHeight();
            int[] pixels = new int[width * height];
            try { anchor.getPixels(pixels, 0, width, 0, 0, width, height); }
            finally { anchor.recycle(); }
            MultiFrameAccumulator accumulator = new MultiFrameAccumulator(width, height, pixels);
            int acceptedFrames = 1;
            int[] tile = new int[width * 64];
            byte[] bytes = new byte[width * 64 * 4];
            Mat template = gray.get(reference);
            for (int i = 0; i < 3; i++) {
                if (i == reference) continue;
                check(cancelled);
                progress.accept("正在对齐并合成第 " + (i + 1) + " 张，原图保留");
                Mat candidateGray = gray.get(i);
                if (candidateGray.size().equals(template.size()) && sharpness(candidateGray) >= best * 0.45) {
                    Mat transform = align(template, candidateGray);
                    if (transform == null) continue;
                    Mat candidate = new Mat(), warped = new Mat();
                    try {
                        double fx = (double) width / template.cols(), fy = (double) height / template.rows();
                        double[] a = new double[]{transform.get(0,0)[0], transform.get(0,1)[0] * fx / fy,
                                transform.get(0,2)[0] * fx, transform.get(1,0)[0] * fy / fx,
                                transform.get(1,1)[0], transform.get(1,2)[0] * fy};
                        transform.put(0,0,a);
                        Bitmap bitmap = decode(resolver, sources.get(i), 0);
                        try {
                            if (bitmap.getWidth() != width || bitmap.getHeight() != height) throw new IOException("三张照片尺寸不一致");
                            Utils.bitmapToMat(bitmap, candidate);
                        } finally { bitmap.recycle(); }
                        Imgproc.warpAffine(candidate, warped, transform, new Size(width, height), Imgproc.INTER_LINEAR,
                                Core.BORDER_CONSTANT, new Scalar(0,0,0,0));
                        candidate.release();
                        int offset = (int) Math.round(Core.mean(template).val[0] - Core.mean(candidateGray).val[0]);
                        if (Math.abs(offset) > 12) continue;
                        long accepted = 0;
                        for (int top = 0; top < height; top += 64) {
                            check(cancelled);
                            int rows = Math.min(64, height - top), count = rows * width;
                            warped.get(top, 0, bytes);
                            for (int j = 0; j < count; j++) {
                                int at = j * 4;
                                tile[j] = (bytes[at + 3] & 255) << 24 | clamp((bytes[at] & 255) + offset) << 16
                                        | clamp((bytes[at + 1] & 255) + offset) << 8 | clamp((bytes[at + 2] & 255) + offset);
                            }
                            accepted += accumulator.addTile(top, rows, tile);
                        }
                        if (accepted > (long) width * height / 10) acceptedFrames++;
                        Log.i("XFlipPhotoBurst", "candidate=" + i + ", accepted_fraction=" + (double) accepted / (width * height));
                    } finally { transform.release(); candidate.release(); warped.release(); }
                }
            }
            if (acceptedFrames < 2) throw new IOException("没有足够可靠的对齐帧，原图保留");
            check(cancelled);
            progress.accept("正在保留细节并另存合成照片");
            output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                check(cancelled);
                accumulator.readRow(y, row);
                output.setPixels(row, 0, width, 0, y, width, 1);
            }
            double sigma = accumulator.noiseSigma;
            accumulator = null; pixels = null;
            if (!options.detail()) sharpen(output, sigma * 0.65, cancelled);
            String tone = PhotoEnhancer.apply(output, options, cancelled, progress);
            Uri saved = JpegPhotoWriter.write(resolver, sources.get(reference), output,"XFlipMerged_",
                    "Robust aligned JPEG fusion; accepted frames=" + acceptedFrames + "; " + tone,cancelled);
            Log.i("XFlipPhotoBurst", "merged_frames=" + acceptedFrames + ", reference=" + reference + ", sigma="
                    + sigma + ", ms=" + (System.nanoTime() - started) / 1_000_000);
            return new Result(saved, acceptedFrames, reference, sigma);
        } catch (RuntimeException error) { throw new IOException("多帧处理失败：" + error.getClass().getSimpleName(), error); }
        finally { for (Mat mat : gray) mat.release(); if (output != null) output.recycle(); }
    }

    private static Bitmap decode(ContentResolver resolver, Uri source, int side) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source), (decoder, info, data) -> {
            int width = info.getSize().getWidth(), height = info.getSize().getHeight();
            if (!"image/jpeg".equals(info.getMimeType()) || width < 128 || height < 128
                    || (long) width * height > 13_000_000L) throw new IllegalArgumentException("支持 13MP 以内 SDR JPEG");
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            if (side > 0 && Math.max(width, height) > side) {
                double ratio = (double) side / Math.max(width, height);
                decoder.setTargetSize((int) Math.round(width * ratio), (int) Math.round(height * ratio));
            }
        });
        if (Build.VERSION.SDK_INT >= 34 && bitmap.hasGainmap()) { bitmap.recycle(); throw new IOException("暂不合成 HDR gain-map 照片"); }
        return bitmap;
    }

    /** Candidate to reference; robust ORB geometry is refined with ECC for subpixel alignment. */
    private static Mat align(Mat reference, Mat candidate) {
        ORB detector = ORB.create(1800);
        BFMatcher matcher = BFMatcher.create(Core.NORM_HAMMING);
        MatOfKeyPoint a = new MatOfKeyPoint(), b = new MatOfKeyPoint();
        Mat da = new Mat(), db = new Mat(), empty = new Mat(), inliers = new Mat(), result = null;
        MatOfPoint2f from = new MatOfPoint2f(), to = new MatOfPoint2f();
        List<MatOfDMatch> matches = new ArrayList<>();
        try {
            detector.detectAndCompute(reference, empty, a, da);
            detector.detectAndCompute(candidate, empty, b, db);
            if (da.rows() < 16 || db.rows() < 16) return null;
            matcher.knnMatch(db, da, matches, 2);
            KeyPoint[] ka = a.toArray(), kb = b.toArray();
            List<Point> src = new ArrayList<>(), dst = new ArrayList<>();
            for (MatOfDMatch pair : matches) {
                DMatch[] m = pair.toArray();
                if (m.length == 2 && m[0].distance < m[1].distance * 0.75 && m[0].distance < 65) {
                    src.add(kb[m[0].queryIdx].pt); dst.add(ka[m[0].trainIdx].pt);
                }
            }
            if (src.size() < 16) return null;
            from.fromList(src); to.fromList(dst);
            result = Calib3d.estimateAffinePartial2D(from, to, inliers, Calib3d.RANSAC, 1.8, 2000, 0.99, 10);
            if (result.empty() || Core.countNonZero(inliers) < Math.max(16, src.size() * 0.45)) return null;
            int quadrants = 0;
            for (int i = 0; i < src.size(); i++) if (inliers.get(i,0)[0] != 0) {
                Point p = dst.get(i); quadrants |= 1 << ((p.x > reference.cols()/2.0 ? 1 : 0) + (p.y > reference.rows()/2.0 ? 2 : 0));
            }
            if (Integer.bitCount(quadrants) < 3 || !reasonable(result, reference)) return null;
            Mat inverse = new Mat(), refined = new Mat();
            try {
                Imgproc.invertAffineTransform(result, inverse); inverse.convertTo(inverse, CvType.CV_32F);
                double correlation = Video.findTransformECC(reference, candidate, inverse, Video.MOTION_AFFINE,
                        new TermCriteria(TermCriteria.COUNT | TermCriteria.EPS, 35, 0.00001));
                Imgproc.invertAffineTransform(inverse, refined);
                if (correlation > 0.8 && reasonable(refined, reference)
                        && Math.abs(refined.get(0,2)[0] - result.get(0,2)[0]) < 4
                        && Math.abs(refined.get(1,2)[0] - result.get(1,2)[0]) < 4) {
                    refined.convertTo(result, CvType.CV_64F);
                }
            } catch (CvException ignored) { /* The robust geometric estimate remains usable. */ }
            finally { inverse.release(); refined.release(); }
            Mat accepted = result; result = null; return accepted;
        } finally {
            if (result != null) result.release();
            for (MatOfDMatch pair : matches) pair.release();
            a.release(); b.release(); da.release(); db.release(); empty.release(); inliers.release(); from.release(); to.release();
            detector.clear(); matcher.clear();
        }
    }

    private static boolean reasonable(Mat matrix, Mat reference) {
        double a = matrix.get(0,0)[0], b = matrix.get(1,0)[0], scale = Math.hypot(a,b);
        return Double.isFinite(scale) && scale > 0.95 && scale < 1.05 && Math.abs(Math.atan2(b,a)) < Math.toRadians(12)
                && Math.abs(matrix.get(0,1)[0] + b) < 0.03 && Math.abs(matrix.get(1,1)[0] - a) < 0.03
                && Math.abs(matrix.get(0,2)[0]) < reference.cols() * 0.15
                && Math.abs(matrix.get(1,2)[0]) < reference.rows() * 0.15;
    }

    private static double sharpness(Mat gray) {
        Mat laplacian = new Mat(); MatOfDouble mean = new MatOfDouble(), deviation = new MatOfDouble();
        try {
            Imgproc.Laplacian(gray, laplacian, CvType.CV_32F);
            Core.meanStdDev(laplacian, mean, deviation); double sigma = deviation.toArray()[0]; return sigma * sigma;
        } finally { laplacian.release(); mean.release(); deviation.release(); }
    }

    private static void sharpen(Bitmap bitmap, double sigma, BooleanSupplier cancelled) throws IOException {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] before = new int[width], center = new int[width], after = new int[width], result = new int[width];
        bitmap.getPixels(before,0,width,0,0,width,1); bitmap.getPixels(center,0,width,0,1,width,1);
        for (int y = 1; y < height - 1; y++) {
            check(cancelled); bitmap.getPixels(after,0,width,0,y+1,width,1);
            MultiFrameAccumulator.sharpenRow(before,center,after,result,sigma);
            bitmap.setPixels(result,0,width,0,y,width,1);
            int[] reuse = before; before = center; center = after; after = reuse;
        }
    }

    private static int clamp(int value) { return Math.max(0,Math.min(255,value)); }
    private static void check(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException("已取消合成，原图保留");
    }
}
