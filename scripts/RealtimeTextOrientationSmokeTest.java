import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.RealtimeTextOrientation;
import java.util.ArrayList;
import java.util.List;

/**
 * A book cover remains in physical reading order after turning the book
 * clockwise or counterclockwise 90 degrees in the camera view.
 * This test exercises ONLY existing OCR box geometry; no extra inference.
 */
public final class RealtimeTextOrientationSmokeTest {
    private static void check(boolean p,String message) {
        if(!p)throw new AssertionError(message);
    }
    private static ReadingOrder.Box box(String text,float x,float y,float width) {
        return new ReadingOrder.Box(text,.98f,
            new float[]{x,x+width,x+width,x},
            new float[]{y,y,y+22,y+22});
    }
    private static List<ReadingOrder.Box> turn(
            List<ReadingOrder.Box> source,int originalWidth,int originalHeight,int degrees) {
        List<ReadingOrder.Box> output=new ArrayList<>();
        for(ReadingOrder.Box b:source){
            float[] x=new float[4],y=new float[4];
            for(int i=0;i<4;i++){
                if(degrees==90) {
                    x[i]=originalHeight-b.y[i];
                    y[i]=b.x[i];
                } else {
                    x[i]=b.y[i];
                    y[i]=originalWidth-b.x[i];
                }
            }
            output.add(new ReadingOrder.Box(b.text,b.confidence,x,y));
        }
        return output;
    }
    public static void main(String[]args) {
        List<ReadingOrder.Box> upright=new ArrayList<>();
        upright.add(box("阅读巅峰为好书而生",60,6,175));
        upright.add(box("简爱",60,65,130));
        upright.add(box("作者与译者",60,112,160));
        upright.add(box("出版社说明",60,157,170));
        String expected="阅读巅峰为好书而生\n简爱\n作者与译者\n出版社说明";
        check(RealtimeTextOrientation.inferredClockwiseRotation(upright)==0,
            "ordinary Chinese lines must not be rotated");
        String original=RealtimeTextOrientation.arrange(upright,400,260);
        check(original.replace("\n\n","\n").equals(expected),"upright order: "+original);
        List<ReadingOrder.Box> cw=turn(upright,400,260,90);
        check(RealtimeTextOrientation.inferredClockwiseRotation(cw)==270,
            "clockwise sideways book must correct counterclockwise");
        String clockwiseText=RealtimeTextOrientation.arrange(cw,260,400);
        check(clockwiseText.replace("\n\n","\n").equals(expected),
            "clockwise sideways cover order: "+clockwiseText);
        List<ReadingOrder.Box> ccw=turn(upright,400,260,270);
        check(RealtimeTextOrientation.inferredClockwiseRotation(ccw)==90,
            "counterclockwise sideways book must correct clockwise");
        String counterclockwiseText=RealtimeTextOrientation.arrange(ccw,260,400);
        check(counterclockwiseText.replace("\n\n","\n").equals(expected),
            "counterclockwise sideways cover order: "+counterclockwiseText);
        System.out.println("REALTIME_TEXT_ORIENTATION=PASS");
    }
}
