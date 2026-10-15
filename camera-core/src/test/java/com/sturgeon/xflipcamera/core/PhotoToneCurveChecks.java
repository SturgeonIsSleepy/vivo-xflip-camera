package com.sturgeon.xflipcamera.core;

final class PhotoToneCurveChecks {
    private static int assertions;
    static int run() {
        long[] histogram = new long[256];
        for (int y=30;y<=220;y++) histogram[y]=100;
        PhotoToneCurve curve = new PhotoToneCurve(histogram,true);
        check(curve.black==30 && curve.white==220 && curve.at(30)==0 && curve.at(220)==255,"Empty ends trimmed");
        for(int y=31;y<=220;y++) check(curve.at(y)>curve.at(y-1),"Occupied tones remain distinct");
        histogram[0]=1;histogram[255]=1;
        curve = new PhotoToneCurve(histogram,true);
        for(int y=0;y<256;y++) check(curve.at(y)==y,"No occupied tail silently clipped");
        histogram=new long[256];histogram[120]=10000;histogram[124]=100;
        curve=new PhotoToneCurve(histogram,true);
        check(!curve.stretched && curve.at(120)==120,"Flat scene not amplified");
        histogram=new long[256];histogram[95]=2000;histogram[145]=8000;
        curve=new PhotoToneCurve(histogram,true,2);
        check(curve.stretched && curve.at(95)==0 && curve.at(145)==255,"Low-contrast text is expanded");
        for(int target=0;target<256;target++) {
            int gray=0xff777777;int mapped=PhotoToneCurve.withLuma(gray,target);
            check(((mapped>>16)&255)==target && ((mapped>>8)&255)==target && (mapped&255)==target,"Gray stays neutral");
            for(int color:new int[]{0xffd04422,0xff22cc44,0xff2244d0,0xff887744}) {
                int mappedColor=PhotoToneCurve.withLuma(color,target);
                int r=(mappedColor>>16)&255,g=(mappedColor>>8)&255,b=mappedColor&255;
                if(((color>>16)&255)>((color>>8)&255))check(r>=g,"Red-green ordering preserved");else check(r<=g,"Green-red ordering preserved");
                if(((color>>8)&255)>(color&255))check(g>=b,"Green-blue ordering preserved");else check(g<=b,"Blue-green ordering preserved");
            }
        }
        check(PhotoToneCurve.withLuma(0xffff0000,255)==0xffff0000,"Saturated red does not become white");
        check(PhotoToneCurve.detailDelta(130,129,150,4,120,150)==0,"Noise is not sharpened");
        check(PhotoToneCurve.detailDelta(150,140,170,2,130,150)<=3,"Bright halo bounded");
        check(PhotoToneCurve.detailDelta(130,140,110,2,130,150)>=-3,"Dark halo bounded");
        check(PhotoToneCurve.hdrLuma(0,-5,0.55,2)==0 && PhotoToneCurve.hdrLuma(255,0,0.55,2)==255,"Clipped endpoints stay clipped");
        check(PhotoToneCurve.hdrLuma(30,Math.log(30/255.0),0.55,2)>30,"HDR tone lifts recorded shadows");
        check(PhotoToneCurve.hdrLuma(230,Math.log(230/255.0),0.55,2)<230,"HDR tone compresses recorded highlights");
        return assertions;
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
