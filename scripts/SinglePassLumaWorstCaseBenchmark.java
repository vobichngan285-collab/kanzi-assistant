import com.vobichngan.kanzi.SinglePassLumaConfirmGate;
import java.util.Arrays;
import java.util.Locale;

/** CI CPU replay benchmark, includes sampled motion/exposure fallback paths. Not Android UVC. */
public final class SinglePassLumaWorstCaseBenchmark {
    private static final int W=1920,H=1080,L=670,T=320,R=1270,B=710;
    private static final int WARM=300, N=1300;

    private static byte[] base() {
        byte[] img=new byte[W*H*3/2];
        Arrays.fill(img,0,W*H,(byte)190);
        Arrays.fill(img,W*H,img.length,(byte)128);
        for(int y=T;y<B;y++)
            for(int x=L;x<R;x++)
                if((((x/5)+(y/9))%3)==0 || (y%19==1))
                    img[y*W+x]=(byte)35;
        return img;
    }
    private static byte[] changed(byte[] src,int shift,int brightness,boolean replace) {
        byte[] dst=new byte[src.length];
        System.arraycopy(src,W*H,dst,W*H,src.length-W*H);
        for(int y=0;y<H;y++) for(int x=0;x<W;x++){
            int xx=x-shift;
            int value=xx<0||xx>=W?190:(src[y*W+xx]&255);
            if(replace && x>=L && x<R && y>=T && y<B)
                value=((x/3+y/7)%2==0)?40:225;
            dst[y*W+x]=(byte)Math.min(255,value+brightness);
        }
        return dst;
    }
    private static long p95(long[] sortedValues) {
        Arrays.sort(sortedValues);
        return sortedValues[(int)Math.floor((sortedValues.length-1)*.95)];
    }
    private static void bench(String name,byte[] ref,byte[] test,boolean expectedReady) {
        long[] durations=new long[N];int accepted=0;
        for(int i=0;i<N+WARM;i++){
            SinglePassLumaConfirmGate gate=new SinglePassLumaConfirmGate(
                new SinglePassLumaConfirmGate.Config(true));
            long started=System.nanoTime();
            SinglePassLumaConfirmGate.Decision arm=gate.arm(
                "有机纯牛奶",.95f,ref,W,H,L,T,R,B,10,1000,1320);
            if(arm.action!=SinglePassLumaConfirmGate.Action.WAIT)
                throw new AssertionError("Invalid reference: "+arm.reason);
            SinglePassLumaConfirmGate.Decision first=
                gate.observe(test,W,H,11,1410);
            SinglePassLumaConfirmGate.Decision second=first.action==SinglePassLumaConfirmGate.Action.WAIT?
                gate.observe(test,W,H,12,1500):first;
            long elapsed=System.nanoTime()-started;
            if(i>=WARM) {
                durations[i-WARM]=elapsed;
                if(second.action==SinglePassLumaConfirmGate.Action.READY)accepted++;
            }
        }
        if((accepted==N)!=expectedReady)
            throw new AssertionError(name+" unexpected ready="+accepted+"/"+N);
        System.out.printf(Locale.US,
            "CLOUD_COST case=%s frames=%d ready=%d p50_us=%.2f p95_us=%.2f%n",
            name,N,accepted,durationsAfterMedian(durations)/1000.0,p95(durations)/1000.0);
    }
    private static long durationsAfterMedian(long[] values){
        long[] sorted=values.clone();Arrays.sort(sorted);return sorted[(sorted.length-1)/2];
    }
    public static void main(String[] args){
        byte[] source=base();
        bench("static",source,source,true);
        bench("jitter_2px",source,changed(source,2,0,false),true);
        bench("exposure_plus10",source,changed(source,0,10,false),true);
        bench("jitter2_plus10",source,changed(source,2,10,false),true);
        bench("different_label",source,changed(source,0,0,true),false);
        System.out.println("CLOUD_WORST_CASE_BENCH=PASS (no OCR/USB/TTS)");
    }
}
