package com.sturgeon.xflipcamera.core;

/** Luminance-only tone decisions; RGB ratios are preserved and gain is limited by channel headroom. */
public final class PhotoToneCurve {
    private final int[] values = new int[256];
    public final int black, white;
    public final boolean stretched;

    public PhotoToneCurve(long[] histogram, boolean autoLevels) { this(histogram,autoLevels,2); }

    public PhotoToneCurve(long[] histogram, boolean autoLevels, double noiseSigma) {
        if (histogram.length != 256) throw new IllegalArgumentException("256 histogram bins required");
        int lo = 0, hi = 255;
        for (long count : histogram) if (count < 0) throw new IllegalArgumentException("Negative bin");
        while (lo < 255 && histogram[lo] == 0) lo++;
        while (hi > lo && histogram[hi] == 0) hi--;
        black = lo; white = hi;
        double total=0,sum=0,square=0;
        for(int y=0;y<256;y++){total+=histogram[y];sum+=histogram[y]*y;square+=histogram[y]*(double)y*y;}
        double deviation=total>0?Math.sqrt(Math.max(0,square/total-Math.pow(sum/total,2))):0;
        // Expand real low-contrast structure, while a noise-only distribution stays unchanged.
        stretched = autoLevels && hi-lo>=32 && deviation>=Math.max(8,noiseSigma*4) && (lo!=0 || hi!=255);
        for (int y = 0; y < 256; y++) values[y] = stretched
                ? clamp((int) Math.round((y - lo) * 255.0 / (hi - lo))) : y;
    }

    public int at(int value) { return values[value]; }

    public static int luma(int color) {
        return (77 * ((color >> 16) & 255) + 150 * ((color >> 8) & 255) + 29 * (color & 255) + 128) >> 8;
    }

    public static int withLuma(int color, int target) {
        int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        double y=(77*r+150*g+29*b)/256.0;
        if(y==0)return color&0xff000000;
        // Limit the common gain to RGB headroom rather than whitening a saturated color.
        double gain=Math.min(target/y,255.0/Math.max(r,Math.max(g,b)));
        return (color&0xff000000) | clamp((int)Math.round(r*gain))<<16
                | clamp((int)Math.round(g*gain))<<8 | clamp((int)Math.round(b*gain));
    }

    public static int detailDelta(int y, double blurred, int clahe, double noise, int localMin, int localMax) {
        double detail = y - blurred;
        if (Math.abs(detail) < Math.max(2, noise * 1.8)) return 0;
        double local = clahe - y;
        if (local * detail < 0) local = 0;
        double adjustment = Math.max(-12, Math.min(12, detail * 0.55 + Math.max(-24,Math.min(24,local)) * 0.25));
        return (int) Math.round(Math.max(localMin - 3, Math.min(localMax + 3, y + adjustment)) - y);
    }

    public static double hdrCompression(long[] histogram) {
        long total = 0;
        for (long count : histogram) total += count;
        if (total == 0) return 1;
        long lowRank = Math.max(1, total / 200), highRank = total - lowRank;
        long count = 0; int low = 0, high = 255;
        for (int i=0;i<256;i++) { count += histogram[i]; if(count >= lowRank) {low=i;break;} }
        count = 0;
        for (int i=0;i<256;i++) { count += histogram[i]; if(count >= highRank) {high=i;break;} }
        double range = Math.log(Math.max(1,high) / (double) Math.max(1,low));
        return range < 0.001 ? 1 : Math.max(0.55, Math.min(1, Math.log(4) / range));
    }

    /** SDR log-base compression, with clipped endpoints unchanged; never reconstructs missing exposure data. */
    public static int hdrLuma(int y, double baseLog, double compression, double noise) {
        if (y == 0 || y == 255 || compression >= 0.999) return y;
        double log = Math.log(y / 255.0);
        double target = 255 * Math.exp(log + (compression - 1) * (baseLog - Math.log(0.5)));
        target = Math.max(y * 0.8, Math.min(y * 2.0 + 2, target));
        double feather = Math.min(1, Math.min(y,255-y) / 12.0);
        feather = feather * feather * (3 - 2 * feather);
        double strength = 0.6 * Math.min(1, 5 / Math.max(2,noise)) * feather;
        return clamp((int) Math.round(y + (target - y) * strength));
    }
    private static int clamp(int y) { return Math.max(0,Math.min(255,y)); }
}
