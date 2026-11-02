import com.vobichngan.kanzi.SinglePassLumaConfirmGate;
import java.util.Locale;
import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic replay of real-time camera *conditions* using generated luma frames.
 * Not a USB camera test or an OCR accuracy test.
 *
 * Baseline = current app's single-pass reading that can speak at first OCR finish;
 * candidate = same single OCR + subsequent cheap luma frame confirmation.
 *
 * Conditions include static view, one/two pixel hand tremor, noisy sensor,
 * changing room light, swapping label, and unsampled changed glyph pixel.
 */
public final class CloudCameraConditionReplay {
    private static final int W=640,H=480, L=180,T=146,R=470,B=313;
    private static final int SAMPLES=80;
    private static final String[] CASES = {
        "static", "sensor_noise_2", "sensor_noise_5",
        "camera_jitter_1px", "camera_jitter_2px",
        "camera_jitter_3px", "exposure_plus_5",
        "exposure_plus_10", "replace_label",
        "small_unsampled_label_change", "camera_pan_12px"
    };

    private static byte[] baseFrame(int seed) {
        byte[] frame=new byte[W*H*3/2];
        Arrays.fill(frame,0,W*H,(byte)192);
        Arrays.fill(frame,W*H,frame.length,(byte)128);
        Random r=new Random(20261103L + seed*101L);
        // Text-like strokes, full image edge texture + dense glyph-like ROI.
        for (int i=0;i<190;i++) {
            int x=L+8+r.nextInt(R-L-16);
            int y=T+10+r.nextInt(B-T-20);
            int dx=1+r.nextInt(3),dy=3+r.nextInt(19);
            for (int yy=y;yy<y+dy && yy<B;yy++)
                for (int xx=x;xx<x+dx && xx<R;xx++)
                    frame[yy*W+xx]=(byte)(25+r.nextInt(34));
        }
        // Alphanumeric-style bars and outlines for high local contrast.
        for(int x=L+10;x<R-10;x+=21)
            for(int y=T+24;y<B-22;y++)
                if(y%27!=0 && (y/27)%2==0) frame[y*W+x]=(byte)36;
        // Static background textured enough to expose image movement.
        for(int i=0;i<400;i++){
            int x=r.nextInt(W),y=r.nextInt(H);
            frame[y*W+x]=(byte)(85+r.nextInt(110));
        }
        return frame;
    }

    private static byte[] perturb(byte[] input,int seed,String condition,int phase) {
        byte[] out=input.clone();
        int shift=0,light=0,noise=0;
        switch(condition) {
            case "sensor_noise_2": noise=2;break;
            case "sensor_noise_5": noise=5;break;
            case "camera_jitter_1px": shift=phase%2==0?-1:1;break;
            case "camera_jitter_2px": shift=phase%2==0?-2:2;break;
            case "camera_jitter_3px": shift=phase%2==0?-3:3;break;
            case "camera_pan_12px": shift=12;break;
            case "exposure_plus_5": light=5;break;
            case "exposure_plus_10": light=10;break;
            case "replace_label":
                for(int y=T;y<B;y++) for(int x=L;x<R;x++)
                    out[y*W+x]=(byte)((x/3+y/7)%2==0 ? 42:203);
                return out;
            case "small_unsampled_label_change":
                // Deliberately alter 1 unsampled luma location; not guaranteed to be seen.
                out[(T+1)*W+L+1]=(byte)27;
                return out;
            default: break;
        }
        Random r=new Random(seed*10007L+phase*53L+89211L);
        if(shift!=0) {
            for(int y=0;y<H;y++) for(int x=0;x<W;x++){
                int xx=x-shift;
                out[y*W+x]=(byte)(xx<0 || xx>=W?192:input[y*W+xx]);
            }
        }
        if(light!=0 || noise!=0) {
            for(int y=0;y<H;y++) for(int x=0;x<W;x++){
                int value=(out[y*W+x]&255)+light;
                if(noise!=0) value +=r.nextInt(2*noise+1)-noise;
                out[y*W+x]=(byte)Math.max(0,Math.min(255,value));
            }
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println("CONDITION,TRIALS,CANDIDATE_READY,CANDIDATE_REJECT,CANDIDATE_WAIT,BASELINE_SPOKEN,MEAN_ADDED_CONFIRM_MS");
        boolean allStaticPassed=true;
        int missedMotion=0;
        for(String condition:CASES) {
            int ready=0,reject=0,wait=0,baselineSpoken=0;
            long accumulatedConfirmMs=0;
            for(int trial=0;trial<SAMPLES;trial++) {
                byte[] original=baseFrame(trial);
                SinglePassLumaConfirmGate gate=new SinglePassLumaConfirmGate(
                        new SinglePassLumaConfirmGate.Config(true));
                // First OCR finishes at 1320ms; legacy app can speak immediately.
                baselineSpoken++;
                SinglePassLumaConfirmGate.Decision armed =
                    gate.arm("纯牛奶 125mg",.92f,original,W,H,L,T,R,B,
                             trial*10L,1000L,1320L);
                if(armed.action!=SinglePassLumaConfirmGate.Action.WAIT) {
                    throw new AssertionError("Reference could not be armed: "+armed.reason);
                }
                SinglePassLumaConfirmGate.Decision d=armed;
                for(int p=1;p<=3;p++) {
                    d=gate.observe(perturb(original,trial,condition,p),
                            W,H,trial*10L+p,1320L+p*90L);
                    if(d.action!=SinglePassLumaConfirmGate.Action.WAIT) break;
                }
                if(d.action==SinglePassLumaConfirmGate.Action.READY){
                    ready++;
                    accumulatedConfirmMs+=180L; // two distinct frames, at 1410 and 1500
                }else if(d.action==SinglePassLumaConfirmGate.Action.REJECT) reject++;
                else wait++;
                if(condition.equals("static") && d.action!=SinglePassLumaConfirmGate.Action.READY)
                    allStaticPassed=false;
                if(condition.equals("replace_label") &&
                        d.action==SinglePassLumaConfirmGate.Action.READY) missedMotion++;
            }
            System.out.printf(Locale.US,"%s,%d,%d,%d,%d,%d,%s%n",
                condition,SAMPLES,ready,reject,wait,baselineSpoken,
                ready==0 ? "NA" : String.format(Locale.US,"%.1f",accumulatedConfirmMs/(double)ready));
            if(condition.equals("static") && ready!=SAMPLES)
                throw new AssertionError("Static scene confirmed only "+ready+"/"+SAMPLES);
        }
        if(!allStaticPassed) throw new AssertionError("Baseline static camera test failed");
        if(missedMotion!=0)
            throw new AssertionError("Large label change was accepted "+missedMotion+" times");
        System.out.println("CLOUD_CONDITION_REPLAY=PASS (simulated luma, not real camera)");
    }
}
