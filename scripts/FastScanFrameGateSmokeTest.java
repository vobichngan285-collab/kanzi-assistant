import com.vobichngan.kanzi.FastScanFrameGate;
import com.vobichngan.kanzi.FrameChangeGate;
import java.util.Arrays;

public final class FastScanFrameGateSmokeTest {
    private static final int W=640,H=480;
    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static byte[] original() {
        byte[] image=new byte[W*H*3/2];
        Arrays.fill(image,0,W*H,(byte)185);
        Arrays.fill(image,W*H,image.length,(byte)128);
        return image;
    }
    private static byte[] newLabel(byte[] base) {
        byte[] out=base.clone();
        // Only ~3% of pixels change: similar book-page background,
        // but a short paragraph/room label now differs.
        for(int y=195;y<267;y++)
            for(int x=240;x<379;x++)
                out[y*W+x]=(byte)65;
        return out;
    }
    private static byte[] quietNoise(byte[] src) {
        byte[] out=src.clone();
        for(int y=0;y<H;y+=7)
            for(int x=0;x<W;x+=11)
                out[y*W+x]=(byte)((src[y*W+x]&0xff)+4);
        return out;
    }
    public static void main(String[] args) {
        byte[] page1=original(),page2=newLabel(page1);
        FastScanFrameGate fast=new FastScanFrameGate();
        FrameChangeGate old=new FrameChangeGate();
        check(fast.accept(page1,W,H,1000),"first page passes");
        check(old.accept(page1,W,H,1000,false),"baseline first page passes");
        check(!fast.accept(page1,W,H,1400),"static scene must not run another OCR at 500ms");
        check(!fast.accept(quietNoise(page1),W,H,1500),"sensor noise must not run another OCR");
        check(!old.accept(page2,W,H,1650,false),
                "existing coarse gate misses this changed small text area");
        check(fast.accept(page2,W,H,1650),
                "fast gate picks up a changed short text at the next 500ms slot");
        check(!fast.accept(page2,W,H,2200),
                "unchanged new page cannot trigger repeated OCR");
        check(fast.accept(page2,W,H,3650),
                "long unchanged label eventually rechecks after 2s");
        fast.reset();
        check(fast.accept(page2,W,H,3700),
                "mode switch resets fingerprint and permits immediate next scan");
        check(!fast.accept(null,W,H,3800), "invalid input ignored");
        System.out.println("FAST_SCAN_FRAME_GATE=PASS");
    }
}
