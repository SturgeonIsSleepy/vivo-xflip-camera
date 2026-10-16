package com.sturgeon.xflipcamera.core;

import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Frame;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Result;
import com.sturgeon.xflipcamera.core.ExposureBracketAssessment.Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ExposureBracketChecks {
    private static int assertions;
    private ExposureBracketChecks() { }
    static int run() {
        assertions = 0;
        for (int[] order : new int[][]{{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}}) {
            List<Frame> frames = new ArrayList<>();
            for (int at : order) frames.add(frame(at,0.001*Math.pow(4,at),100));
            Result result = ExposureBracketAssessment.assess(frames);
            check(result.status()==Status.METADATA_CANDIDATE_ONLY,"2EV bracket is a metadata candidate only");
            for(int rank=0;rank<3;rank++) check(order[result.sourceOrder().get(rank)]==rank,"Input indices sorted by capture exposure");
            check(order[result.referenceIndex()]==1,"Middle exposure reference index");
            check(Math.abs(result.lowerCaptureEv()-2)<1e-10 && Math.abs(result.upperCaptureEv()-2)<1e-10,"Capture spans");
            check(Math.abs(result.lowerGainEv()-2)<1e-10 && Math.abs(result.upperGainEv()-2)<1e-10,"Gain spans");
            try { result.sourceOrder().add(4); throw new AssertionError("Mutable order"); }
            catch (UnsupportedOperationException expected) { assertions++; }
        }
        status(List.of(frame(0,.004,100),frame(1,.004,100),frame(2,.004,100)),Status.INSUFFICIENT_CAPTURE_SPAN);
        status(List.of(frame(0,.004,100),frame(1,.004,400),frame(2,.004,1600)),Status.INSUFFICIENT_CAPTURE_SPAN);
        status(List.of(frame(0,.001,1600),frame(1,.004,400),frame(2,.016,100)),Status.INSUFFICIENT_GAIN_SPAN);
        status(List.of(frame(0,.001,6400),frame(1,.004,400),frame(2,.016,25)),Status.INSUFFICIENT_GAIN_SPAN);
        status(List.of(frame(0,.001,100),frame(1,.002,100),frame(2,.004,100)),Status.METADATA_CANDIDATE_ONLY);
        status(List.of(frame(0,.001,100),frame(1,.001999,100),frame(2,.004,100)),Status.INSUFFICIENT_CAPTURE_SPAN);
        status(List.of(frame(0,.001,100),frame(0,.004,100),frame(2,.016,100)),Status.DUPLICATE_FILE);
        Frame rotated = new Frame(240,320,6,.004,2,100,4,0,"camera",digest(1));
        status(List.of(frame(0,.001,100),rotated,frame(2,.016,100)),Status.METADATA_CANDIDATE_ONLY);
        status(List.of(frame(0,.001,100),new Frame(320,240,6,.004,2,100,4,0,"camera",digest(1)),frame(2,.016,100)),Status.INCONSISTENT_GEOMETRY);
        for(int flash:new int[]{-1,1,9,65}) status(List.of(frame(0,.001,100),
                new Frame(320,240,1,.004,2,100,4,flash,"camera",digest(1)),frame(2,.016,100)),Status.FLASH_OR_UNKNOWN);
        for(double focal:new double[]{2,4.1,8}) status(List.of(frame(0,.001,100),
                new Frame(320,240,1,.004,2,100,focal,0,"camera",digest(1)),frame(2,.016,100)),Status.CAMERA_OR_LENS_CHANGED);
        status(List.of(frame(0,.001,100),new Frame(320,240,1,.004,2.8,100,4,0,"camera",digest(1)),frame(2,.016,100)),Status.CAMERA_OR_LENS_CHANGED);
        status(List.of(frame(0,.001,100),new Frame(320,240,1,.004,2,100,4,0,"other",digest(1)),frame(2,.016,100)),Status.CAMERA_OR_LENS_CHANGED);
        for (int[] order : new int[][]{{0,1,2},{1,0,2},{2,1,0}}) {
            List<Frame> group=List.of(new Frame(320,240,1,.001,2,100,3.98,0,"camera",digest(0)),
                    frame(1,.004,100),new Frame(320,240,1,.016,2,100,4.02,0,"camera",digest(2)));
            status(List.of(group.get(order[0]),group.get(order[1]),group.get(order[2])),Status.CAMERA_OR_LENS_CHANGED);
        }
        for(double bad:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,0,-1}) {
            for(int field=0;field<4;field++) {
                double[] values={.004,2,100,4};values[field]=bad;
                status(List.of(frame(0,.001,100),new Frame(320,240,1,values[0],values[1],values[2],values[3],0,"camera",digest(1)),
                        frame(2,.016,100)),Status.MISSING_OR_INVALID_METADATA);
            }
        }
        for(int orientation:new int[]{-1,0,9}) status(List.of(frame(0,.001,100),
                new Frame(320,240,orientation,.004,2,100,4,0,"camera",digest(1)),frame(2,.016,100)),Status.MISSING_OR_INVALID_METADATA);
        for(String bad:new String[]{null,""," ","g".repeat(64),"a".repeat(63)}) status(List.of(frame(0,.001,100),
                new Frame(320,240,1,.004,2,100,4,0,"camera",bad),frame(2,.016,100)),Status.MISSING_OR_INVALID_METADATA);
        status(Arrays.asList(frame(0,.001,100),null,frame(2,.016,100)),Status.MISSING_OR_INVALID_METADATA);
        status(List.of(),Status.INPUT_COUNT);status(null,Status.INPUT_COUNT);
        status(List.of(frame(0,.001,100),frame(1,.004,100)),Status.INPUT_COUNT);
        for(int width:new int[]{0,-1,100000}) status(List.of(frame(0,.001,100),
                new Frame(width,100000,1,.004,2,100,4,0,"camera",digest(1)),frame(2,.016,100)),Status.MISSING_OR_INVALID_METADATA);
        Frame privateFrame = new Frame(320,240,1,.004,2,100,4,0,"PRIVATE_CAMERA",digest(1));
        check(!privateFrame.toString().contains("PRIVATE_CAMERA")&&!privateFrame.toString().contains(digest(1)),"Private metadata not exposed by toString");
        return assertions;
    }
    private static Frame frame(int id,double seconds,double iso) {return new Frame(320,240,1,seconds,2,iso,4,0,"camera",digest(id));}
    private static String digest(int id) {return String.format(java.util.Locale.ROOT,"%064x",id+1);}
    private static void status(List<Frame> input,Status expected) {
        Result result=ExposureBracketAssessment.assess(input);
        check(result.status()==expected,"Expected "+expected+" got "+result.status());
        if(expected!=Status.METADATA_CANDIDATE_ONLY) check(result.referenceIndex()==-1 && result.sourceOrder().isEmpty()
                && Double.isNaN(result.lowerCaptureEv()),"Rejected inputs cannot supply a usable merge plan");
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
}
