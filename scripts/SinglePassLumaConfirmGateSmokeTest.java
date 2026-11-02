import com.vobichngan.kanzi.SinglePassLumaConfirmGate;
import java.util.Arrays;

/**
 * Synthetic luma safety cases; DOES NOT measure OCR correctness or real USB jank.
 */
public final class SinglePassLumaConfirmGateSmokeTest {
    private static final int W=128,H=96,L=32,T=24,R=96,B=72;
    private static void check(boolean ok,String label) {
        if(!ok) throw new AssertionError(label);
    }
    private static byte[] frame() {
        byte[] nv21=new byte[W*H*3/2];
        Arrays.fill(nv21,0,W*H,(byte)190);
        Arrays.fill(nv21,W*H,nv21.length,(byte)128);
        // Dense black glyph-like stripes inside the detected OCR region.
        for(int y=T;y<B;y++)
            for(int x=L;x<R;x++)
                if(((x-L)/3)%2==0 && y%4!=0)
                    nv21[y*W+x]=(byte)30;
        return nv21;
    }
    private static SinglePassLumaConfirmGate on() {
        return new SinglePassLumaConfirmGate(
                new SinglePassLumaConfirmGate.Config(true));
    }
    private static SinglePassLumaConfirmGate.Decision arm(
            SinglePassLumaConfirmGate g,byte[] img,long seq,long captured,long now) {
        return g.arm("有机纯牛奶",.94f,img,W,H,L,T,R,B,seq,captured,now);
    }
    private static SinglePassLumaConfirmGate.Decision follow(
            SinglePassLumaConfirmGate g,byte[] img,long seq,long now) {
        return g.observe(img,W,H,seq,now);
    }
    public static void main(String[] args) {
        byte[] image=frame();
        SinglePassLumaConfirmGate disabled=new SinglePassLumaConfirmGate();
        check(arm(disabled,image,1,1000,1100).reason ==
                SinglePassLumaConfirmGate.Reason.DISABLED,"disabled by default");
        check(!disabled.isArmed(),"disabled gate never captures sample");
        SinglePassLumaConfirmGate good=on();
        check(arm(good,image,1,1000,1100).action ==
                SinglePassLumaConfirmGate.Action.WAIT,"arm one OCR result");
        check(follow(good,image,1,1140).newFrames==0,
                "same source frame cannot confirm");
        check(follow(good,image,2,1190).action ==
                SinglePassLumaConfirmGate.Action.WAIT,"one luma frame insufficient");
        SinglePassLumaConfirmGate.Decision ready=follow(good,image,3,1300);
        check(ready.action==SinglePassLumaConfirmGate.Action.READY &&
                "有机纯牛奶".equals(ready.text) && ready.newFrames==2,
                "confirmed with one OCR and two cheap frames");
        check(!good.isArmed(),"ready result clears its pending snapshot");
        check(follow(good,image,4,1400).reason ==
                SinglePassLumaConfirmGate.Reason.UNARMED,"cannot double publish");
        arm(good,image,5,1500,1600);
        follow(good,image,6,1700);
        check(follow(good,image,7,1830).action ==
                SinglePassLumaConfirmGate.Action.DUPLICATE,
                "previously announced text is not re-spoken");

        good.reset();
        arm(good,image,8,2000,2070);
        follow(good,image,9,2150);
        check(follow(good,image,10,2270).action ==
                SinglePassLumaConfirmGate.Action.READY,
                "manual stop/restart enables same text");

        byte[] changed=image.clone();
        for(int y=T;y<B;y++) for(int x=L;x<R;x++)
            changed[y*W+x]=(byte)155;
        SinglePassLumaConfirmGate moving=on();
        arm(moving,image,1,1000,1080);
        check(follow(moving,changed,2,1170).reason ==
                SinglePassLumaConfirmGate.Reason.FRAME_CHANGED,
                "different label / camera movement rejects pending text");
        check(!moving.isArmed(),"changed scene cancels pending candidate");

        byte[] globalChanged=image.clone();
        for(int y=0;y<H;y++)
            for(int x=0;x<W;x++)
                if(x<L-4) globalChanged[y*W+x]=(byte)50;
        SinglePassLumaConfirmGate scene=on();
        arm(scene,image,1,1000,1080);
        check(follow(scene,globalChanged,2,1160).reason ==
                SinglePassLumaConfirmGate.Reason.FRAME_CHANGED,
                "global camera change detected even if OCR ROI stays the same");

        SinglePassLumaConfirmGate t=on();
        check(arm(t,image,1,1000,2301).reason ==
                SinglePassLumaConfirmGate.Reason.REFERENCE_TOO_OLD,
                "long OCR should not arm a stale camera frame");
        check(t.arm("标签",.2f,image,W,H,L,T,R,B,1,1000,1050).reason ==
                SinglePassLumaConfirmGate.Reason.LOW_CONFIDENCE,
                "weak OCR rejected");
        check(t.arm("长文".repeat(30),.95f,image,W,H,L,T,R,B,1,1000,1050).reason ==
                SinglePassLumaConfirmGate.Reason.TOO_LONG,
                "long book page cannot enter short scan");
        check(t.arm("安全",.95f,image,W,H,L,T,W+1,B,1,1000,1050).reason ==
                SinglePassLumaConfirmGate.Reason.INVALID_INPUT,
                "invalid crop bounds rejected");
        byte[] blank=new byte[W*H*3/2];
        Arrays.fill(blank,0,W*H,(byte)200);
        check(t.arm("字",.95f,blank,W,H,L,T,R,B,1,1000,1080).reason ==
                SinglePassLumaConfirmGate.Reason.FLAT_OR_BLURRY_ROI,
                "flat text crop cannot be approved by luma-only confirmation");

        SinglePassLumaConfirmGate outOfOrder=on();
        arm(outOfOrder,image,10,1000,1080);
        check(follow(outOfOrder,image,9,1200).reason ==
                SinglePassLumaConfirmGate.Reason.OUT_OF_ORDER,"old frame ignored");
        check(follow(outOfOrder,image,11,1230).newFrames==1,
                "old frame does not count");
        check(follow(outOfOrder,image,12,1350).action ==
                SinglePassLumaConfirmGate.Action.READY,"later distinct frames confirm");

        SinglePassLumaConfirmGate late=on();
        arm(late,image,1,1000,1080);
        check(follow(late,image,2,1770).reason ==
                SinglePassLumaConfirmGate.Reason.WINDOW_EXPIRED,
                "delayed camera frame is not accepted");
        SinglePassLumaConfirmGate gap=on();
        arm(gap,image,1,1000,1080);
        check(follow(gap,image,2,1500).reason ==
                SinglePassLumaConfirmGate.Reason.FRAME_GAP,
                "camera stall cannot count as stability");

        SinglePassLumaConfirmGate cancelled=on();
        arm(cancelled,image,1,1000,1080);
        cancelled.cancel();
        check(follow(cancelled,image,2,1200).reason ==
                SinglePassLumaConfirmGate.Reason.UNARMED,
                "stop or reading epoch invalidates pending OCR");

        System.out.println("SINGLE_PASS_LUMA_CONFIRM_GATE=PASS");
    }
}
