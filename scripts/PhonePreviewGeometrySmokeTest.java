import com.vobichngan.kanzi.PhonePreviewGeometry;

public class PhonePreviewGeometrySmokeTest {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static void close(double got, double wanted, String message) {
        if (Math.abs(got-wanted) > 0.02) throw new AssertionError(message+": "+got+" vs "+wanted);
    }
    static double distance(float[] a, float[] b) {
        return Math.hypot(a[0]-b[0],a[1]-b[1]);
    }
    static void verify(int viewW, int viewH, int frameW, int frameH,
                       int degrees, boolean mirror, boolean flip) {
        PhonePreviewGeometry.Layout g=PhonePreviewGeometry.fit(
            viewW,viewH,frameW,frameH,degrees,mirror,flip);
        // No content stretching: a pixel has the same physical screen size
        // in both the x and y source directions, at every right-angle rotation.
        float[] o=g.mapNative(0,0), x=g.mapNative(1,0), y=g.mapNative(0,1);
        close(distance(o,x),distance(o,y),"no non-uniform pixel scale");
        float[] p1=g.mapNative(0,0),p2=g.mapNative(frameW,0),
            p3=g.mapNative(frameW,frameH),p4=g.mapNative(0,frameH);
        float loX=Math.min(Math.min(p1[0],p2[0]),Math.min(p3[0],p4[0]));
        float hiX=Math.max(Math.max(p1[0],p2[0]),Math.max(p3[0],p4[0]));
        float loY=Math.min(Math.min(p1[1],p2[1]),Math.min(p3[1],p4[1]));
        float hiY=Math.max(Math.max(p1[1],p2[1]),Math.max(p3[1],p4[1]));
        close(hiX-loX,g.fittedWidth,"fitted width");
        close(hiY-loY,g.fittedHeight,"fitted height");
        close((hiX+loX)/2,viewW/2.0,"center horizontally");
        close((hiY+loY)/2,viewH/2.0,"center vertically");
        check(loX>=-0.02 && loY>=-0.02 && hiX<=viewW+0.02 && hiY<=viewH+0.02,
              "all source corners must fit screen, no cropping");
        float[] t=g.textureMatrix;
        float inputX=(float)viewW * .237f,inputY=(float)viewH * .619f;
        float mappedTx=t[0]*inputX+t[1]*inputY+t[2],
              mappedTy=t[3]*inputX+t[4]*inputY+t[5];
        float[] expected=g.mapNative(inputX*frameW/viewW,inputY*frameH/viewH);
        close(mappedTx,expected[0],"TextureView X matrix");
        close(mappedTy,expected[1],"TextureView Y matrix");
    }
    public static void main(String[] args) {
        int[][] screens={{1080,1920},{1920,1080},{691,1536},{720,1560}};
        int[][] sources={{1280,720},{1920,1080},{640,480}};
        for (int[] screen:screens) for(int[] src:sources)
            for(int angle:new int[]{0,90,180,270})
                for(boolean mirror:new boolean[]{false,true})
                    for(boolean flip:new boolean[]{false,true})
                        verify(screen[0],screen[1],src[0],src[1],angle,mirror,flip);
        PhonePreviewGeometry.Layout portrait=PhonePreviewGeometry.fit(
            1080,1920,1280,720,90,false,false);
        close(portrait.fittedWidth,1080,"9:16 fills portrait width");
        close(portrait.fittedHeight,1920,"9:16 fills portrait height");
        PhonePreviewGeometry.Layout sideways=PhonePreviewGeometry.fit(
            1080,1920,1280,720,0,false,false);
        close(sideways.fittedWidth,1080,"16:9 fills portrait width");
        close(sideways.fittedHeight,607.5,"16:9 letterboxes without warp");
        check(PhonePreviewGeometry.rotation(90,270)==0,"manual rotation must work");
        check(PhonePreviewGeometry.rotation(90,90)==180,"rotation composes");
        // Android Camera2 TextureView already rotates sensor buffers into
        // device NATURAL orientation. A sensor=90 landscape raw image is
        // already portrait INSIDE TextureView. Applying sensor90 again was
        // the 0.9.3 double-rotation regression visible in phone screenshots.
        for(int sensor: new int[]{90,270}) {
            PhonePreviewGeometry.Layout auto = PhonePreviewGeometry.camera2Preview(
                1080,1920,1280,720,sensor,0,false,false);
            PhonePreviewGeometry.Layout uprightNatural = PhonePreviewGeometry.fit(
                1080,1920,720,1280,0,false,false);
            for(float[] xy:new float[][]{{0f,0f},{360f,640f},{720f,1280f}}) {
                float[] got=auto.mapNative(xy[0],xy[1]);
                float[] expected=uprightNatural.mapNative(xy[0],xy[1]);
                close(got[0],expected[0],"camera2 auto orientation X");
                close(got[1],expected[1],"camera2 auto orientation Y");
            }
            float[] tl=auto.mapNative(0,0), tr=auto.mapNative(720,0),
                bl=auto.mapNative(0,1280);
            check(tr[0]>tl[0] && bl[1]>tl[1],
                "default camera preview must be UPRIGHT and not sideways");
            close(auto.fittedWidth,1080,"natural 16:9 portrait width");
            close(auto.fittedHeight,1920,"natural 16:9 portrait height");
        }
        for(int sensor:new int[]{0,90,180,270}) {
            int[] naturalSize=PhonePreviewGeometry.naturalBufferSize(1280,720,sensor);
            for(int extra:new int[]{0,90,180,270})
                for(boolean mirror:new boolean[]{false,true})
                    for(boolean flip:new boolean[]{false,true}) {
                        PhonePreviewGeometry.Layout layout=PhonePreviewGeometry.camera2Preview(
                            1080,1920,1280,720,sensor,extra,mirror,flip);
                        verify(1080,1920,naturalSize[0],naturalSize[1],
                            extra,mirror,flip);
                        check(layout.fittedWidth>0 && layout.fittedHeight>0,
                            "all camera angle settings produce a valid FIT");
                    }
        }
        for(int sensor:new int[]{0,90,180,270}) {
            int sw=1280,sh=720;
            int ow=sensor%180==0?sw:sh;
            int oh=sensor%180==0?sh:sw;
            for(float[] p:new float[][]{{0,0},{ow/2f,oh/2f},{ow,oh}}) {
                float[] natural=PhonePreviewGeometry.ocrToNatural(
                    p[0],p[1],sw,sh,sensor,sensor);
                close(natural[0],p[0],"matching OCR and sensor X");
                close(natural[1],p[1],"matching OCR and sensor Y");
            }
        }
        // When device display and OCR rotations differ from sensor orientation,
        // polygons must still land at their sensor-natural position.
        float[] off=PhonePreviewGeometry.ocrToNatural(50,1180,1280,720,270,90);
        close(off[0],670,"OCR-to-natural from 270 OCR 90 sensor X");
        close(off[1],100,"OCR-to-natural from 270 OCR 90 sensor Y");
        System.out.println("PHONE_PREVIEW_GEOMETRY=PASS");
    }
}
