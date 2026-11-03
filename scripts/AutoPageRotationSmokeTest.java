import com.vobichngan.kanzi.AutoPageRotation;
import com.vobichngan.kanzi.ReadingOrder;
import java.util.ArrayList;
import java.util.List;

public class AutoPageRotationSmokeTest {
    static ReadingOrder.Box row(String text,float confidence,float x,float y,float dx,float dy,float h) {
        return new ReadingOrder.Box(text,confidence,
            new float[]{x,x+dx,x+dx,y==Float.NEGATIVE_INFINITY?x:x},
            new float[]{y,y+dy,y+dy+h,y+h});
    }
    static ReadingOrder.Box box(String s,float c,float x,float y,float dx,float dy) {
        // Side edge approx perpendicular to line edge, 26px high.
        float m=(float)Math.hypot(dx,dy),nx=(-dy/m)*26f,ny=(dx/m)*26f;
        return new ReadingOrder.Box(s,c,
            new float[]{x,x+dx,x+dx+nx,x+nx},
            new float[]{y,y+dy,y+dy+ny,y+ny});
    }
    static void check(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
    static List<ReadingOrder.Box> sample(int angle,int count,float confidence) {
        List<ReadingOrder.Box> out=new ArrayList<>();
        String text="短视频直播和新型社交媒体平台的发展诞生了许多草根网红";
        for(int i=0;i<count;i++){
            float dx=angle==0?560:angle==90?60:-60;
            float dy=angle==0?-20:angle==90?-550:550;
            out.add(box(text,confidence,140+i*28,180+i*32,dx,dy));
        }
        return out;
    }
    public static void main(String[] args) {
        List<ReadingOrder.Box> good=sample(0,7,.98f);
        List<ReadingOrder.Box> left=sample(90,7,.98f);
        List<ReadingOrder.Box> right=sample(270,7,.98f);
        check(AutoPageRotation.candidate(good)==0,"normal horizontal must not turn");
        check(AutoPageRotation.candidate(left)==90,"user's left-rotated book");
        check(AutoPageRotation.candidate(right)==270,"right-rotated book");
        List<ReadingOrder.Box> mixed=new ArrayList<>(left);
        mixed.addAll(good);
        check(AutoPageRotation.candidate(mixed)==0,"mixed layout must preserve original");
        check(!AutoPageRotation.accept(good,left),"rotated candidate must improve actual OCR");
        List<ReadingOrder.Box> poor=sample(90,5,.88f);
        List<ReadingOrder.Box> recovered=sample(0,15,.99f);
        check(AutoPageRotation.accept(poor,recovered),"clear recovered page must pass");
        check(!AutoPageRotation.accept(recovered,poor),"no regression");
        System.out.println("AUTO_PAGE_ROTATION_SMOKE_TEST=PASS");
    }
}
