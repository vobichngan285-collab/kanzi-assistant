package com.vobichngan.kanzi;

public final class UsbCameraHealthMonitorSmokeTest {
    private static void yes(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UsbCameraHealthMonitor m = new UsbCameraHealthMonitor();
        String s=m.sample(5000,0,0,0,false,false,true);
        yes(s.contains("尚不能判定断流"),"Do not falsely classify before OPENED");
        m.onOpened(10000,100,0);
        m.onRawNv21At(1920,1080,11000);
        s=m.sample(12000,120,11900,0,true,true,true);
        yes(s.contains("启动观察"),"Startup must be given a grace period");
        m.onRawNv21At(1920,1080,13200);
        s=m.sample(14000,140,13900,0,true,true,true);
        yes(s.contains("帧与GL提交有活动"),"Healthy callbacks must be reported");
        s=m.sample(17000,140,13900,0,false,true,true);
        yes(s.contains("原始帧与直显更新均停滞"),"Both pipelines stopped");
        m.onRawNv21At(1920,1080,17700);
        s=m.sample(18000,140,13900,0,false,true,true);
        yes(s.contains("原始NV21仍到达，但直显更新停滞"),"GL stalled while UVC live");
        s=m.sample(22000,175,21900,0,false,true,true);
        yes(s.contains("原始NV21回调停滞，直显仍在更新"),"Raw UVC stopped while GL alive");
        m.onRawNv21At(1920,1080,22400);
        s=m.sample(23000,190,22900,1,false,true,true);
        yes(s.contains("EGL交换缓冲出现错误"),"Report recorded error counter change");
        yes(m.dump().contains("CHANGE"),"Retain bounded incident transitions");
        for(int i=0;i<130;i++)m.sample(24000+i*1000,190,22900,1,false,true,true);
        yes(m.dump().split("\n").length<84,"Bounded ~60s logs");
        m.onError(160000,"USB error");
        s=m.sample(161000,190,22900,1,false,false,true);
        yes(s.contains("USB ERROR"),"Handle state error");
        yes(m.dump().contains("STATE ERROR"),"Keep failure event");
        yes(m.sample(162000,190,22900,1,false,false,false).contains("手机摄像头模式"),
            "Disable USB monitor during phone camera mode");
        System.out.println("BLUE_STAR_USB_CAMERA_HEALTH_MONITOR=PASS");
    }
}
