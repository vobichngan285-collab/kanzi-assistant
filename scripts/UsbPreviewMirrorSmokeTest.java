import com.vobichngan.kanzi.UsbPreviewMirror;

/** Offline JVM regression: preview-only correction, never OCR pixel mutation. */
public final class UsbPreviewMirrorSmokeTest {
    private static int checks;
    private static void check(boolean b, String msg) {
        checks++;
        if (!b) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        // User scenario: guitar is physically on right (x=75% of frame),
        // but the USB display initially reverses it to x=25%.
        final float width=1080f, right=.75f*width;
        check(UsbPreviewMirror.shouldMirror(true,false),
            "USB default must enable left/right correction");
        check(UsbPreviewMirror.mapX(right,width,true,false)==270f,
            "default USB display-only correction must flip source coordinates");
        check(UsbPreviewMirror.mapX(270f,width,true,false)==right,
            "mirror must be an involution");
        check(!UsbPreviewMirror.shouldMirror(false,false),
            "user may disable correction for a camera already correctly oriented");
        check(!UsbPreviewMirror.shouldMirror(true,true),
            "additional user mirror cancels USB default correction");
        check(UsbPreviewMirror.shouldMirror(false,true),
            "additional user mirror alone works for other cameras");
        check(UsbPreviewMirror.mapX(0f,width,true,false)==width,
            "edge maps right-to-left");
        check(UsbPreviewMirror.mapX(width,width,true,false)==0f,
            "right edge maps left");
        check(UsbPreviewMirror.mapX(width/2,width,true,false)==width/2,
            "center stays centered");
        check(UsbPreviewMirror.mapX(right,width,false,false)==right,
            "camera without USB calibration is unchanged");
        check(UsbPreviewMirror.mapX(right,width,true,true)==right,
            "manual reversal is live and bidirectional");
        boolean bad=false;
        try { UsbPreviewMirror.mapX(Float.NaN,width,true,false); }
        catch(IllegalArgumentException e) {bad=true;}
        check(bad,"invalid overlay geometry must be rejected");
        // This component has NO access to the camera NV21 buffer, OCR engine,
        // model, or speech dispatch; only presentation coordinates are mapped.
        System.out.println("USB_PREVIEW_MIRROR_ONLY=PASS checks="+checks);
    }
}
