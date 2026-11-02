import com.vobichngan.kanzi.RealtimeSceneTracker;
import java.util.Arrays;

/** Simulate first capture, static paper, camera pan, and return to same label. */
public final class RealtimeSceneTrackerSmokeTest {
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static byte[] frame(int width,int height,int kind) {
        byte[] data=new byte[width*height*3/2];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int value=kind==0? ((x/13+y/9)%2==0?210:45) :
                       kind==1? ((x/11+y/17)%2==0?55:205) :
                       ((x/7+y/21)%2==0?180:72);
            data[y*width+x]=(byte)value;
        }
        Arrays.fill(data,width*height,data.length,(byte)128);
        return data;
    }
    private static byte[] exposure(byte[] src,int change){
        byte[] copy=src.clone();
        for(int i=0;i<copy.length*2/3;i++)
            copy[i]=(byte)Math.min(255,(copy[i]&255)+change);
        return copy;
    }
    public static void main(String[]args){
        RealtimeSceneTracker t=new RealtimeSceneTracker();
        byte[] a=frame(320,240,0),b=frame(320,240,1);
        long start=t.observe(a,320,240,1000);
        check(start>0,"first frame has a generation");
        check(t.observe(a,320,240,1100)==start,"static camera must not retrigger OCR");
        check(t.observe(exposure(a,3),320,240,1210)==start,"exposure noise cannot flip target");
        check(t.observe(b,320,240,1310)==start,"one changed frame is not confirmed");
        long second=t.observe(b,320,240,1410);
        check(second>start,"new page must invalidate old OCR result");
        check(t.observe(b,320,240,1510)==second,"same new page stays stable");
        check(t.observe(a,320,240,1610)==second,"return needs two samples");
        long revisit=t.observe(a,320,240,1720);
        check(revisit>second,"leaving A for B then returning to A is a new scan");
        t.reset();
        check(t.generation()>revisit,"mode switch cancels in-flight result");
        check(t.observe(null,320,240,1840)==t.generation(),"invalid frame ignored");
        System.out.println("REALTIME_SCENE_TRACKER=PASS");
    }
}
