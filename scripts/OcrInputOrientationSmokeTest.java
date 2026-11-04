import com.vobichngan.kanzi.OcrInputOrientation;
import com.vobichngan.kanzi.UsbPreviewMirror;

/** Proves OCR orientation is source-frame-specific and not affected by any preview toggles. */
public final class OcrInputOrientationSmokeTest {
    static int checks;
    static void ok(boolean b,String msg) {checks++;if(!b)throw new AssertionError(msg);}
    static void approx(float a,float b,String msg) {ok(Math.abs(a-b)<.001f,msg+": "+a+" != "+b);}
    public static void main(String[] args) {
        final int w=1920,h=1080;
        for(int angle:new int[]{0,90,180,270}) {
            int[] size=OcrInputOrientation.inputSize(w,h,angle);
            ok(size[0]==((angle%180==0)?w:h) &&
               size[1]==((angle%180==0)?h:w),"input dimensions");
            float[] source={0f,0f,w,h,w*.23f,h*.67f};
            for(int i=0;i<source.length;i+=2) {
                float[] p=OcrInputOrientation.sourceToInput(source[i],source[i+1],w,h,angle);
                float[] inverse=OcrInputOrientation.inputToSource(p[0],p[1],w,h,angle);
                approx(inverse[0],source[i],"x inverse "+angle);
                approx(inverse[1],source[i+1],"y inverse "+angle);
            }
        }
        int[] rotated=OcrInputOrientation.inputSize(w,h,270);
        ok(rotated[0]==1080 && rotated[1]==1920,
            "USB sideways NV21 should yield portrait OCR input at 270");
        // USB preview mirror adjustment must not appear in OCR source mapping.
        float[] fixed=OcrInputOrientation.sourceToInput(300f,500f,w,h,270);
        for(boolean factory:new boolean[]{false,true}) {
            for(boolean extra:new boolean[]{false,true}) {
                boolean previewFlip=UsbPreviewMirror.shouldMirror(factory,extra);
                float screen=UsbPreviewMirror.mapX(300f,w,factory,extra);
                float[] unchanged=OcrInputOrientation.sourceToInput(300f,500f,w,h,270);
                approx(fixed[0],unchanged[0],"OCR x invariant under preview mirror");
                approx(fixed[1],unchanged[1],"OCR y invariant under preview mirror");
                ok(previewFlip?screen==1620f:screen==300f,"preview change visible");
            }
        }
        ok(OcrInputOrientation.normalize(-90)==0 &&
           OcrInputOrientation.normalize(360)==0 &&
           OcrInputOrientation.normalize(270)==270,"safe rotation values");
        System.out.println("PREVIEW_AND_OCR_CHANNELS_INDEPENDENT=PASS checks="+checks);
    }
}
