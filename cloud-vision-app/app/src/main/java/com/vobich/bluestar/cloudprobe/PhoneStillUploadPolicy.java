package com.vobich.bluestar.cloudprobe;

/** Only controls size of an already-captured real Camera2 still for cloud upload. */
final class PhoneStillUploadPolicy {
    static final int ORIGINAL=0, P1440=1, P1080=2;
    static final long ORIGINAL_MAX_PIXELS=8000000L;
    static final long P1440_MAX_PIXELS=2560L*1440L;
    static final long P1080_MAX_PIXELS=1920L*1080L;
    private PhoneStillUploadPolicy(){}
    static int normalize(int selection){
        return selection>=ORIGINAL&&selection<=P1080?selection:ORIGINAL;
    }
    static long maxPixels(int selection){
        switch(normalize(selection)){
            case P1440:return P1440_MAX_PIXELS;
            case P1080:return P1080_MAX_PIXELS;
            default:return ORIGINAL_MAX_PIXELS;
        }
    }
    static double scaleFor(int width,int height,int selection){
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid JPEG dimensions");
        return Math.min(1.0,Math.sqrt(maxPixels(selection)/(double)((long)width*height)));
    }
}
