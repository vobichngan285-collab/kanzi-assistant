import com.vobichngan.kanzi.FrameChangeGate;
import com.vobichngan.kanzi.SinglePassLumaConfirmGate;
import java.util.Arrays;

/**
 * Deterministic simulated USB camera callback timeline.
 * No real camera and no OCR model. Tests whether the proposed design can
 * avoid a second expensive OCR in the 2.8s duplicate-scene holdoff.
 */
public final class SinglePassLumaPipelineSimulation {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static byte[] syntheticLabel() {
        int width=320,height=240;
        byte[] data=new byte[width*height*3/2];
        Arrays.fill(data,0,width*height,(byte)195);
        for (int y=75;y<155;y++)
            for (int x=65;x<255;x++)
                if (((x/5)+(y/9))%2==0)
                    data[y*width+x]=(byte)45;
        return data;
    }
    public static void main(String[] args) {
        byte[] first=syntheticLabel();
        int w=320,h=240;
        FrameChangeGate existing=new FrameChangeGate();
        check(existing.accept(first,w,h,1000,false),"first scene runs OCR");
        check(!existing.accept(first,w,h,1300,false),
                "2800ms duplicate-holdoff delays any second OCR");
        check(!existing.accept(first,w,h,3799,false),
                "same frame does not pass old OCR gate before 2800ms");
        check(existing.accept(first,w,h,3800,false),
                "second full OCR only becomes eligible after 2800ms");

        SinglePassLumaConfirmGate lowCost=new SinglePassLumaConfirmGate(
                new SinglePassLumaConfirmGate.Config(true));
        // Simulate ONE OCR on frame seq 1 at 1000ms, finishing at 1320ms.
        check(lowCost.arm("店名 125mg",.94f,first,w,h,65,75,255,155,1,1000,1320)
                .action == SinglePassLumaConfirmGate.Action.WAIT,
                "OCR result can be armed after first inference");
        check(lowCost.observe(first,w,h,2,1400).action ==
                SinglePassLumaConfirmGate.Action.WAIT,
                "first lightweight confirmation frame");
        check(lowCost.observe(first,w,h,3,1490).action ==
                SinglePassLumaConfirmGate.Action.READY,
                "second lightweight frame confirms within 170ms of OCR completion");
        System.out.println("SINGLE_PASS_TIMELINE=PASS old_second_ocr_eligible_at_ms=3800"
                +" low_cost_ready_at_ms=1490"
                +" simulated_extra_confirmation_after_ocr_ms=170"
                +" additional_ocr_calls=0");

        SinglePassLumaConfirmGate changed=new SinglePassLumaConfirmGate(
                new SinglePassLumaConfirmGate.Config(true));
        changed.arm("店名 125mg",.94f,first,w,h,65,75,255,155,1,1000,1320);
        byte[] moved=first.clone();
        for (int y=75;y<155;y++)
            for(int x=65;x<255;x++)
                moved[y*w+x]=(byte)180;
        check(changed.observe(moved,w,h,2,1400).action ==
                SinglePassLumaConfirmGate.Action.REJECT,
                "moving to another product must cancel the old OCR text");
        System.out.println("SINGLE_PASS_MOVED_SCENE=PASS");
    }
}
