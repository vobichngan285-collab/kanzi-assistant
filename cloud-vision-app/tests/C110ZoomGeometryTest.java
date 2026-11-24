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
        // In portrait cover, 1x means the visible center slice, not the full
        // 16:9 source. Manual zoom crops again within the same viewport.
        C110ZoomGeometry.Region cover=C110ZoomGeometry.centeredCover(
            1920,1080,1f,9f/16f);
        eq(cover.width==608&&cover.height==1080&&cover.left==656&&cover.top==0,
            "portrait 9:16 viewport crops left/right without stretching");
        C110ZoomGeometry.Region cover2=C110ZoomGeometry.centeredCover(
            1920,1080,2f,9f/16f);
        eq(cover2.width==304&&cover2.height==540&&cover2.left==808&&cover2.top==270,
            "2x zoom OCR exactly within portrait visible viewport");
        C110ZoomGeometry.Region nativeCover=C110ZoomGeometry.centeredCover(
            3840,2160,1f,9f/16f);
        eq(nativeCover.width==1215&&nativeCover.height==2160&&nativeCover.left==1312,
            "8M native SD portrait cover uses original image coordinates");
        eq(C110ZoomGeometry.centeredCover(1920,1080,1f,16f/9f)
            .full(1920,1080),"matching viewport ratio stays full frame");
        C110ZoomGeometry.Region wide=C110ZoomGeometry.centeredCover(
            1920,1080,1f,2f);
        eq(wide.width==1920&&wide.height==960&&wide.top==60,
            "landscape cover center-crops vertically");
        boolean rejected=false;
        try{C110ZoomGeometry.centeredCover(1920,1080,1f,Float.NaN);}
        catch(IllegalArgumentException expected){rejected=true;}
        eq(rejected,"invalid viewport aspect must reject rather than mis-map OCR");
        // v1.0.87 PixelCopy uses THIS exact sourceRect as its bitmap size.
        // No 2MP intermediary resize; source ROI and output have equal pixels.
        float[] zooms={1f,2f,4f};
        float[] views={9f/16f,16f/9f,1f,2f};
        int[][] frameSizes={{1920,1080},{2560,1440},{3840,2160}};
        for(int[] size:frameSizes)for(float z:zooms)for(float aspect:views){
            C110ZoomGeometry.Region roi=
                C110ZoomGeometry.centeredCover(size[0],size[1],z,aspect);
            eq(roi.left>=0&&roi.top>=0,"ROI nonnegative coordinates");
            eq(roi.width>=2&&roi.height>=2,"ROI has valid pixel dimensions");
            eq(roi.left+roi.width<=size[0]&&roi.top+roi.height<=size[1],
                "ROI within real source borders");
            eq((long)roi.width*roi.height<=(long)size[0]*size[1],
                "ROI cannot increase the number of source pixels");
            if(z==1f&&Math.abs(aspect-(16f/9f))<.01f)
                eq(roi.full(size[0],size[1]),"1x full landscape no crop");
        }
        eq(C110ZoomGeometry.centeredCover(1920,1080,2f,9f/16f).width==304,
            "2x portrait sourceRect width matches current VLC centerCrop geometry");
        eq(C110ZoomGeometry.centered(1920,1080,4f).height==270,
            "4x full landscape sourceRect exactly 1/4 linear dimensions");
        System.out.println("PASS C110 visible ROI geometry 1x/2x/4x no extra scaling");
        System.out.println("PASS C110+ portrait cover maps live/native OCR to visible region");
    }
}
