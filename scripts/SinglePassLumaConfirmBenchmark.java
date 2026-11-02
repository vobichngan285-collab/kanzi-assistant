import com.vobichngan.kanzi.SinglePassLumaConfirmGate;
import java.util.Arrays;
import java.util.Locale;

/**
 * CI-host synthetic timing, not Android performance. Reports p50/p95 for only
 * the new low-cost luminance comparison, excluding OCR, camera and TTS.
 */
public final class SinglePassLumaConfirmBenchmark {
    private static final int W=1920,H=1080,L=660,T=350,R=1240,B=750;
    private static byte[] makeFrame() {
        byte[] image=new byte[W*H*3/2];
        Arrays.fill(image,0,W*H,(byte)192);
        Arrays.fill(image,W*H,image.length,(byte)128);
        for(int y=T;y<B;y++) for(int x=L;x<R;x++)
            if(((x/7)+(y/11))%2==0 && y%5!=0) image[y*W+x]=(byte)38;
        return image;
    }
    private static long percentile(long[] values, double fraction) {
        long[] copy=values.clone();
        Arrays.sort(copy);
        return copy[Math.min(copy.length-1,(int)((copy.length-1)*fraction))];
    }
    public static void main(String[] args) {
        byte[] frame=makeFrame();
        int warmup=1500, count=5000;
        long[] durations=new long[count];
        long checkpoint=System.nanoTime();
        int confirmed=0;
        for(int i=0;i<warmup+count;i++) {
            SinglePassLumaConfirmGate gate=new SinglePassLumaConfirmGate(
                new SinglePassLumaConfirmGate.Config(true));
            long started=System.nanoTime();
            gate.arm("125mg 3.8g",.95f,frame,W,H,L,T,R,B,i*4L,i*1000L,i*1000L+50);
            gate.observe(frame,W,H,i*4L+1,i*1000L+150);
            SinglePassLumaConfirmGate.Decision d=
                gate.observe(frame,W,H,i*4L+2,i*1000L+250);
            if(d.action==SinglePassLumaConfirmGate.Action.READY) confirmed++;
            long elapsed=System.nanoTime()-started;
            if(i>=warmup) durations[i-warmup]=elapsed;
        }
        System.out.printf(Locale.US,
                "SINGLE_PASS_LUMA_CI_BENCH=PASS frames=%d accepted=%d p50_us=%.2f p95_us=%.2f wall_ms=%.2f%n",
                count,confirmed,percentile(durations,.5)/1000.0,
                percentile(durations,.95)/1000.0,
                (System.nanoTime()-checkpoint)/1.0e6);
        if(confirmed!=warmup+count) throw new AssertionError("benchmark invalid sample");
    }
}
