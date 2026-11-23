package com.vobich.bluestar.cloudprobe;
/** Choose a TRUE Camera2 JPEG mode; never rescale 8MP to pretend native 3MP. */
final class PhoneNativeStillPolicy {
    static final int ORIGINAL_8MP=0,DIRECT_3MP=1;
    static final long ORIGINAL_TARGET=8000000L,DIRECT_TARGET=2560L*1440L;
    private PhoneNativeStillPolicy(){}
    static int normalize(int mode){return mode==DIRECT_3MP?DIRECT_3MP:ORIGINAL_8MP;}
    static long target(int mode){return normalize(mode)==DIRECT_3MP?DIRECT_TARGET:ORIGINAL_TARGET;}
    static boolean eligible(int width,int height,int mode){
        if(width<=0||height<=0)return false;
        long pixels=(long)width*height;
        return normalize(mode)==DIRECT_3MP?
            pixels>=3000000L&&pixels<4000000L:pixels>=2000000L;
    }
    static double score(int width,int height,int mode){
        if(!eligible(width,height,mode))return Double.POSITIVE_INFINITY;
        return Math.abs(Math.log(((double)((long)width*height))/target(mode)))+
            Math.abs(Math.log((width/(double)height)/(16.0/9.0)))*0.15;
    }
}
