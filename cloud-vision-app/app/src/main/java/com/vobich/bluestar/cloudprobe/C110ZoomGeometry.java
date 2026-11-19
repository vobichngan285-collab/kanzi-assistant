package com.vobich.bluestar.cloudprobe;

/**
 * Central digital zoom region shared by C110 VLC preview-frame OCR and native JPEG OCR.
 * Coordinates are relative to the ORIGINAL image, not the on-screen View transform.
 * All 1x requests pass through without cropping or re-encoding.
 */
final class C110ZoomGeometry {
    static final float MIN=1f;
    static final float MAX=4f;

    static final class Region {
        final int left,top,width,height;
        Region(int left,int top,int width,int height){
            this.left=left;this.top=top;this.width=width;this.height=height;
        }
        boolean full(int w,int h){
            return left==0&&top==0&&width==w&&height==h;
        }
    }

    static float clamp(float zoom){
        if(Float.isNaN(zoom)||Float.isInfinite(zoom))return MIN;
        return Math.max(MIN,Math.min(MAX,zoom));
    }

    static Region centered(int originalWidth,int originalHeight,float zoom){
        if(originalWidth<2||originalHeight<2)
            throw new IllegalArgumentException("C110 image dimensions invalid");
        float factor=clamp(zoom);
        if(factor<=1.001f)return new Region(0,0,originalWidth,originalHeight);
        int wantedW=Math.min(originalWidth,Math.max(2,Math.round(originalWidth/factor)));
        int wantedH=Math.min(originalHeight,Math.max(2,Math.round(originalHeight/factor)));
        int x=(originalWidth-wantedW)/2;
        int y=(originalHeight-wantedH)/2;
        return new Region(x,y,wantedW,wantedH);
    }

    /**
     * CenterCrop of a landscape C110 source into the portrait preview viewport,
     * followed by optional manual zoom. The identical normalized rectangle is
     * applied to PixelCopy and native SD JPEG BEFORE OCR. No frame stretching.
     */
    static Region centeredCover(int width,int height,float zoom,float viewportAspect){
        if(width<2||height<2)throw new IllegalArgumentException("C110 source invalid");
        if(Float.isNaN(viewportAspect)||Float.isInfinite(viewportAspect)||
           viewportAspect<=0f)throw new IllegalArgumentException("C110 viewport invalid");
        float sourceAspect=width/(float)height;
        float baseW=width,baseH=height;
        if(sourceAspect>viewportAspect)baseW=height*viewportAspect;
        else baseH=width/viewportAspect;
        float scale=clamp(zoom);
        int wantedW=Math.min(width,Math.max(2,Math.round(baseW/scale)));
        int wantedH=Math.min(height,Math.max(2,Math.round(baseH/scale)));
        return new Region((width-wantedW)/2,(height-wantedH)/2,
                          wantedW,wantedH);
    }

    /** Native photos and video must both be 16:9; otherwise visual/ROI alignment cannot be proven. */
    static boolean photoLooksLikeLandscapeVideo(int width,int height){
        if(width<2||height<2)return false;
        float aspect=width/(float)height;
        return Math.abs(aspect-16f/9f)<.065f;
    }

    private C110ZoomGeometry(){}
}
