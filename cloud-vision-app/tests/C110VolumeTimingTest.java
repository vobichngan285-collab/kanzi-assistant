package com.vobich.bluestar.cloudprobe;

/** Deterministic key timing regression, no Android/ADB required. */
public final class C110VolumeTimingTest {
    private static void check(boolean ok,String caseName){
        if(!ok)throw new AssertionError(caseName);
    }
    public static void main(String[] ignored){
        check(C110VolumeTiming.secondDownAfterFirstUp(1000,1100,600),
            "quick double volume click");
        check(C110VolumeTiming.secondDownAfterFirstUp(1000,1600,600),
            "600ms upper boundary");
        check(!C110VolumeTiming.secondDownAfterFirstUp(1000,1601,600),
            "slow presses should change volume, not shutter");
        check(!C110VolumeTiming.secondDownAfterFirstUp(0,100,600),
            "no prior UP means no double click");
        check(!C110VolumeTiming.secondDownAfterFirstUp(1000,999,600),
            "reordered or stale hardware key timestamp");
        check(!C110VolumeTiming.secondDownAfterFirstUp(1000,1020,0),
            "invalid gesture window");
        System.out.println("PASS C110 hardware volume timing");
    }
}
