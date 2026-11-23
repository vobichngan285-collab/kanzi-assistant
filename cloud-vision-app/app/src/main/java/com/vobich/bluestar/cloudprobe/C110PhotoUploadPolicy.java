package com.vobich.bluestar.cloudprobe;
/** C110 hardware always records the original; only cloud JPEG can be smaller. */
final class C110PhotoUploadPolicy {
    static final String PREF="c110_photo_upload_resolution_trial_v1";
    static final int ORIGINAL=0,P1440=1;
    static final long MAX_PIXELS=2560L*1440L;
    private C110PhotoUploadPolicy(){}
    static int normalize(int mode){return mode==P1440?P1440:ORIGINAL;}
    static double scaleFor(int width,int height,int mode){
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid C110 dimensions");
        return normalize(mode)==ORIGINAL?1.0:
            Math.min(1.0,Math.sqrt(MAX_PIXELS/(double)((long)width*height)));
    }
}
