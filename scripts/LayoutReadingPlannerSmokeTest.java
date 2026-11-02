import com.vobichngan.kanzi.LayoutReadingPlanner;
import com.vobichngan.kanzi.LayoutRegion;
import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.StrictOcrOrderGuard;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LayoutReadingPlannerSmokeTest {
    private static ReadingOrder.Box box(String s,float x,float y) {
        return new ReadingOrder.Box(s,1f,
            new float[]{x,x+80,x+80,x},new float[]{y,y,y+20,y+20});
    }
    private static void check(boolean ok,String msg) { if(!ok)throw new AssertionError(msg); }
    public static void main(String[] args) {
        List<StrictOcrOrderGuard.Item> items=Arrays.asList(
            new StrictOcrOrderGuard.Item(20,"右页第一行"),
            new StrictOcrOrderGuard.Item(10,"左页第一行"),
            new StrictOcrOrderGuard.Item(21,"右页第二行"),
            new StrictOcrOrderGuard.Item(11,"左页第二行"));
        List<ReadingOrder.Box> boxes=Arrays.asList(
            box("右页第一行",300,10),box("左页第一行",10,10),
            box("右页第二行",300,50),box("左页第二行",10,50));
        List<LayoutRegion> regions=Arrays.asList(
            new LayoutRegion(2,.9f,0,0,150,100),
            new LayoutRegion(2,.9f,250,0,400,100));
        LayoutReadingPlanner.Plan p=LayoutReadingPlanner.plan(items,boxes,regions);
        check(p.verified.accepted,"invalid permutation");
        check(p.verified.exactText().equals("左页第一行\n左页第二行\n右页第一行\n右页第二行"),
            "page reading order incorrect");
        check(p.usedLayout,"layout assignment missing");
        check(p.placements.get(0).regionId==0,"wrong region assignment");
        LayoutReadingPlanner.Plan fallback=LayoutReadingPlanner.plan(items,boxes,Collections.<LayoutRegion>emptyList());
        check(fallback.verified.accepted,"geometry fallback invalid");
        check(fallback.verified.ordered.size()==4,"OCR box lost");
        System.out.println("LAYOUT_READING_PLANNER=PASS");
    }
}
