import com.vobichngan.kanzi.LayoutReadingPlanner;
import com.vobichngan.kanzi.LayoutRegion;
import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.StrictOcrOrderGuard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A more realistic crowded double-page layout with interleaved OCR detections,
 * a shared title, and a detector returning a single page-wide region.
 * Does not establish real-camera accuracy; guards a reproducible geometry case.
 */
public final class DenseTwoPageReadingSmokeTest {
    private static ReadingOrder.Box box(String text,float left,float top,float width) {
        return new ReadingOrder.Box(text,.98f,
            new float[]{left,left+width,left+width,left},
            new float[]{top,top,top+18,top+18});
    }
    private static void check(boolean p,String m) { if(!p)throw new AssertionError(m); }
    public static void main(String[] args) {
        List<StrictOcrOrderGuard.Item> source=new ArrayList<>();
        List<ReadingOrder.Box> geometry=new ArrayList<>();
        // Interleave two pages in raw detection order: R1,L1,R2,L2...
        StringBuilder expected=new StringBuilder("第一章：双页正文");
        source.add(new StrictOcrOrderGuard.Item(900,"第一章：双页正文"));
        geometry.add(box("第一章：双页正文",20,4,370));
        for(int line=0;line<12;line++) {
            String right="右页第"+line+"行正文";
            String left="左页第"+line+"行正文";
            source.add(new StrictOcrOrderGuard.Item(100+line,right));
            geometry.add(box(right,270,45+line*26,110));
            source.add(new StrictOcrOrderGuard.Item(200+line,left));
            geometry.add(box(left,10,45+line*26,110));
        }
        for(int line=0;line<12;line++)expected.append("\n左页第").append(line).append("行正文");
        for(int line=0;line<12;line++)expected.append("\n右页第").append(line).append("行正文");
        List<LayoutRegion> onlyBroad=Collections.singletonList(
            new LayoutRegion(2,.99f,0,0,400,400));
        LayoutReadingPlanner.Plan plan=LayoutReadingPlanner.plan(source,geometry,onlyBroad);
        check(plan.verified.accepted,"ID permutation must be valid");
        check(plan.placements.size()==source.size(),"must preserve all OCR boxes");
        check(plan.verified.exactText().equals(expected.toString()),
            "crowded two-page reading order was interleaved\n"+plan.verified.exactText());
        check(plan.placements.get(0).ocrId==900,"chapter heading should be first");
        System.out.println("DENSE_TWO_PAGE_READING=PASS");
    }
}
