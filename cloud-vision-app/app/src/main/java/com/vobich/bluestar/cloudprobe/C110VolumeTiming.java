package com.vobich.bluestar.cloudprobe;

/** Android-free timing guard for one hardware volume double press. */
final class C110VolumeTiming {
    private C110VolumeTiming(){}
    static boolean secondDownAfterFirstUp(long lastUp,long secondDown,long maxGapMs){
        return lastUp>0L&&secondDown>=lastUp&&maxGapMs>0L&&
            secondDown-lastUp<=maxGapMs;
    }
}
