package com.vobich.bluestar.cloudprobe;

/** Conservative 720p default with optional native 1080p detail preservation. */
final class ImageQualityPolicy {
    static int pick(int sourceLongEdge,String policy,double fineRatio,
                    double broadRatio,double brightness,double variance){
        if("720".equals(policy))return 1280;
        if("1080".equals(policy))return 1920;
        if(sourceLongEdge<1600)return 1280;
        boolean softButVisible=brightness>=48&&brightness<=218&&variance>=300
            &&broadRatio>=0.050&&fineRatio<0.017;
        return softButVisible?1920:1280;
    }
}
