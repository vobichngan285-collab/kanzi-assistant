package com.vobich.bluestar.cloudprobe;

/** RC15 rolling 10-second update counts, including freeze and stale exclusions. */
public final class UsbRollingFpsTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args){
        UsbRollingFps f=new UsbRollingFps();
        f.add(1000,0,0,true);
        check(f.stats(1000).count==0,"first empty baseline excluded");
        f.add(3000,600,2000,true); f.add(5000,590,2000,true);
        f.add(7000,0,2000,true); f.add(9000,300,2000,true);
        UsbRollingFps.Stats s=f.stats(9000);
        check(s.count==4,"four usable samples");
        check(s.lastFps10==300,"current is newest");
        check(s.meanFps10==373,"rounded mean 37.3");
        check(s.minFps10==0,"zero-FPS stall retained");
        check(s.maxFps10==600,"high 60fps");
        check(f.stats(15001).count==2,"10 second window discards old samples");
        f.add(16000,770,2000,false);
        check(f.stats(16000).count==2,"closed camera sample ignored");
        f.reset();
        check(f.stats(18000).count==0,"source switch clears stale history");
        System.out.println("RC15_USB_ROLLING_10S_FPS_STALE_AND_FREEZE=PASS");
    }
}
