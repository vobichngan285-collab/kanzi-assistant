package com.vobich.bluestar.cloudprobe;
public final class UsbStreamHealthTest {
    private static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
    public static void main(String[] args){
        UsbStreamHealth health=new UsbStreamHealth();
        UsbStreamHealth.Sample startup=health.sample(1000,1000,0,0,0,0,false);
        ok(startup.state==UsbStreamHealth.OPENING,"closed -> opening not stalled");
        health.reset();
        health.sample(4000,1000,20,90,3980,3990,true);
        UsbStreamHealth.Sample healthy=health.sample(6000,1000,30,210,5980,5990,true);
        ok(healthy.state==UsbStreamHealth.HEALTHY,"healthy");
        ok(healthy.rawFps10==50,"5Hz raw");
        ok(healthy.previewFps10==600,"60Hz texture buffer");
        UsbStreamHealth.Sample rawOnly=health.sample(8000,1000,30,330,5980,7990,true);
        ok(rawOnly.state==UsbStreamHealth.RAW_STALLED,"raw stopped, texture alive");
        UsbStreamHealth.Sample both=health.sample(10000,1000,30,330,5980,7990,true);
        ok(both.state==UsbStreamHealth.BOTH_STALLED,"both stopped");
        UsbStreamHealth.Sample viewOnly=health.sample(12000,1000,40,330,11990,7990,true);
        ok(viewOnly.state==UsbStreamHealth.PREVIEW_STALLED,"native alive, preview frozen");
        health.reset();
        ok(health.sample(20000,19000,0,0,0,0,true).state==
            UsbStreamHealth.OPENING,"warmup grace on resume");
        System.out.println("RC14_USB_LIFECYCLE_FPS_4_STATES=PASS");
    }
}
