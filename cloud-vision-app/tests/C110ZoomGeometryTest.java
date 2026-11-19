package com.vobich.bluestar.cloudprobe;

public final class C110ZoomGeometryTest {
    private static void eq(boolean passed,String explanation){
        if(!passed)throw new AssertionError(explanation);
    }
    public static void main(String[] args){
        C110ZoomGeometry.Region original=
            C110ZoomGeometry.centered(5888,3312,1f);
        eq(original.full(5888,3312),"1x must be full original, no resample");
        C110ZoomGeometry.Region half=
            C110ZoomGeometry.centered(5888,3312,2f);
        eq(half.left==1472&&half.top==828&&half.width==2944&&half.height==1656,
            "2x must crop centered 1/4 original pixel area");
        C110ZoomGeometry.Region quad=
            C110ZoomGeometry.centered(3840,2160,4f);
        eq(quad.left==1440&&quad.top==810&&quad.width==960&&quad.height==540,
            "4K 4x must be centered 960x540");
        C110ZoomGeometry.Region ten=
            C110ZoomGeometry.centered(4320,2432,4f);
        eq(ten.left==1620&&ten.top==912&&ten.width==1080&&ten.height==608,
            "10M 4x central JPEG crop");
        C110ZoomGeometry.Region live=
            C110ZoomGeometry.centered(1920,1080,2f);
        eq(live.left==480&&live.top==270&&live.width==960&&live.height==540,
            "RTSP frame matches same normalized crop");
        eq(C110ZoomGeometry.centered(3840,2160,0).full(3840,2160),
            "invalid low zoom -> pass-through");
        eq(C110ZoomGeometry.centered(3840,2160,Float.NaN).full(3840,2160),
            "NaN must never crop to arbitrary location");
        eq(C110ZoomGeometry.centered(3840,2160,Float.POSITIVE_INFINITY)
            .full(3840,2160),"Infinite zoom must be safe");
        eq(C110ZoomGeometry.centered(3840,2160,12f).width==960,"clamp 4x");
        eq(C110ZoomGeometry.photoLooksLikeLandscapeVideo(5888,3312),
            "C110+ 20M is 16:9");
        eq(C110ZoomGeometry.photoLooksLikeLandscapeVideo(4320,2432),
            "C110+ 10M is 16:9");
        eq(C110ZoomGeometry.photoLooksLikeLandscapeVideo(3840,2160),
            "C110+ 8M is 16:9");
        eq(!C110ZoomGeometry.photoLooksLikeLandscapeVideo(3000,4000),
            "reject incompatible portrait photos rather than pretend matching ROI");
        System.out.println("PASS C110+ center ROI shared across native JPEG and RTSP, original 1x, guards");
    }
}
