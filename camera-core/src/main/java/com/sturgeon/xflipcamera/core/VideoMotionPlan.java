package com.sturgeon.xflipcamera.core;

/** Translation-only stabilization of small grayscale frames; no Android or codec dependency. */
public final class VideoMotionPlan {
    public static final float SCALE = 1.10f;
    private static final float MAX_TRANSLATION = 0.098f;
    private final long[] times;
    private final float[] x;
    private final float[] y;
    public final int acceptedPairs;

    public record Shift(float x, float y, boolean reliable, double error) { }

    private VideoMotionPlan(long[] times, float[] x, float[] y, int acceptedPairs) {
        this.times = times.clone();
        this.x = x;
        this.y = y;
        this.acceptedPairs = acceptedPairs;
    }

    /** The returned shift is the movement of image features from before to after. */
    public static Shift estimate(byte[] before, byte[] after, int width, int height) {
        if (width < 32 || height < 32 || width > 256 || height > 256
                || before.length != width * height || after.length != before.length) {
            throw new IllegalArgumentException("Invalid analysis frame");
        }
        int radius = Math.min(8, Math.min(width, height) / 10);
        int margin = radius + 2;
        double meanBefore = 0, meanAfter = 0, texture = 0;
        int count = 0;
        for (int py = margin; py < height - margin; py += 2) {
            for (int px = margin; px < width - margin; px += 2) {
                int at = py * width + px;
                meanBefore += before[at] & 255;
                meanAfter += after[at] & 255;
                texture += Math.abs((before[at] & 255) - (before[at + 1] & 255));
                texture += Math.abs((before[at] & 255) - (before[at + width] & 255));
                count++;
            }
        }
        if (texture / count < 6) return new Shift(0, 0, false, 255);
        double brightness = (meanAfter - meanBefore) / count;
        double best = Double.POSITIVE_INFINITY;
        int side = radius * 2 + 1;
        double[] errors = new double[side * side];
        int bestX = 0, bestY = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double sum = 0;
                for (int py = margin; py < height - margin; py += 2) {
                    for (int px = margin; px < width - margin; px += 2) {
                        int at = py * width + px;
                        sum += Math.abs((after[at + dy * width + dx] & 255)
                                - (before[at] & 255) - brightness);
                    }
                }
                double error = sum / count;
                errors[(dy + radius) * side + dx + radius] = error;
                if (error < best) {
                    best = error;
                    bestX = dx;
                    bestY = dy;
                }
            }
        }
        // Neighboring integer shifts belong to the same subpixel minimum, not a competing match.
        double second = Double.POSITIVE_INFINITY;
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
            if (Math.abs(dx - bestX) > 1 || Math.abs(dy - bestY) > 1) {
                second = Math.min(second, errors[(dy + radius) * side + dx + radius]);
            }
        }
        boolean reliable = best < 22 && second - best > Math.max(0.8, best * 0.04)
                && Math.abs(bestX) < radius && Math.abs(bestY) < radius;
        int at = (bestY + radius) * side + bestX + radius;
        float x = bestX, y = bestY;
        if (reliable) {
            x += subpixel(errors[at - 1], best, errors[at + 1]);
            y += subpixel(errors[at - side], best, errors[at + side]);
        }
        return new Shift(x, y, reliable, best);
    }

    private static float subpixel(double left, double center, double right) {
        double curve = left + right - 2 * center;
        return curve > 0 ? (float) Math.max(-0.5, Math.min(0.5, (left - right) / (2 * curve))) : 0;
    }

    public static VideoMotionPlan create(long[] times, Shift[] steps, int width, int height) {
        if (times.length < 3 || times.length != steps.length || width < 1 || height < 1) {
            throw new IllegalArgumentException("Invalid motion samples");
        }
        int size = times.length, accepted = 0;
        float[] pathX = new float[size], pathY = new float[size];
        int[] segment = new int[size];
        for (int i = 1; i < size; i++) {
            if (times[i] <= times[i - 1]) throw new IllegalArgumentException("Non-monotonic times");
            Shift step = steps[i];
            if (step != null && step.reliable()) {
                pathX[i] = pathX[i - 1] + step.x();
                pathY[i] = pathY[i - 1] + step.y();
                segment[i] = segment[i - 1];
                accepted++;
            } else segment[i] = segment[i - 1] + 1;
        }
        float[] x = new float[size], y = new float[size];
        for (int i = 0; i < size; i++) {
            // Local linear fit preserves intentional constant-speed pans, including at clip ends.
            double weight = 0, t = 0, tt = 0, xx = 0, yy = 0, tx = 0, ty = 0;
            for (int j = Math.max(0, i - 3); j <= Math.min(size - 1, i + 3); j++) {
                if (segment[j] != segment[i]) continue;
                double dt = (times[j] - times[i]) / 100000.0;
                double w = 4 - Math.abs(j - i);
                weight += w;
                t += w * dt;
                tt += w * dt * dt;
                xx += w * pathX[j];
                yy += w * pathY[j];
                tx += w * dt * pathX[j];
                ty += w * dt * pathY[j];
            }
            double denominator = weight * tt - t * t;
            double smoothX = denominator > 0 ? (xx * tt - tx * t) / denominator : pathX[i];
            double smoothY = denominator > 0 ? (yy * tt - ty * t) / denominator : pathY[i];
            x[i] = clamp((float) ((smoothX - pathX[i]) * 2 * SCALE / width));
            y[i] = clamp((float) (-(smoothY - pathY[i]) * 2 * SCALE / height));
        }
        return new VideoMotionPlan(times, x, y, accepted);
    }

    public float xAt(long timeUs) { return interpolate(x, timeUs); }
    public float yAt(long timeUs) { return interpolate(y, timeUs); }
    public int sampleCount() { return times.length; }

    private float interpolate(float[] values, long timeUs) {
        int index = java.util.Arrays.binarySearch(times, timeUs);
        if (index >= 0) return values[index];
        int next = -index - 1;
        if (next == 0) return values[0];
        if (next == times.length) return values[next - 1];
        float fraction = (float) (timeUs - times[next - 1]) / (times[next] - times[next - 1]);
        return values[next - 1] + fraction * (values[next] - values[next - 1]);
    }

    private static float clamp(float value) {
        return Math.max(-MAX_TRANSLATION, Math.min(MAX_TRANSLATION, value));
    }
}
