import com.vobichngan.kanzi.LayoutReadingPlanner;
import com.vobichngan.kanzi.LayoutRegion;
import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.StrictOcrOrderGuard;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Regression for cyclic pairwise rectangle comparisons and reused box objects. */
public final class LayoutOrderingDeterminismSmokeTest {
    private static ReadingOrder.Box box(String text,float l,float t,float r,float b) {
        return new ReadingOrder.Box(text,1f,new float[]{l,r,r,l},
            new float[]{t,t,b,b});
    }
    private static void expect(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ReadingOrder.Box a=box("A",0,0,90,90);
        ReadingOrder.Box b=box("B",40,0,70,90);
        ReadingOrder.Box c=box("C",80,0,110,30);
        List<LayoutRegion> regions=Arrays.asList(
            new LayoutRegion(2,.99f,0,0,90,90),
            new LayoutRegion(2,.99f,40,0,70,90),
            new LayoutRegion(2,.99f,80,0,110,30));
        ReadingOrder.Box[] all={a,b,c};
        String reference=null;
        for(int i=0;i<3;i++)for(int j=0;j<3;j++) {
            if(i==j)continue;
            int k=3-i-j;
            List<ReadingOrder.Box> boxes=Arrays.asList(all[i],all[j],all[k]);
            List<StrictOcrOrderGuard.Item> source=new ArrayList<>();
            for(ReadingOrder.Box item:boxes) {
                source.add(new StrictOcrOrderGuard.Item(item==a?11:item==b?12:13,item.text));
            }
            LayoutReadingPlanner.Plan p=LayoutReadingPlanner.plan(source,boxes,regions,LayoutReadingPlanner.SceneMode.BOOK);
            expect(p.verified.accepted,"strict OCR ID permutation rejected");
            if(reference==null)reference=p.verified.exactText();
            else expect(reference.equals(p.verified.exactText()),
                "Input detection order changed region ordering: "+p.verified.exactText());
        }
        expect("A\nB\nC".equals(reference),"region order was not stable: "+reference);

        // The same recognized Box instance can occur more than once in input.
        // It must never be silently lost or merged into one OCR ID.
        ReadingOrder.Box leftTop=box("左侧",0,20,100,40);
        ReadingOrder.Box leftBottom=box("左侧下",0,90,100,110);
        ReadingOrder.Box rightTop=box("右侧",250,20,350,40);
        ReadingOrder.Box rightBottom=box("右侧下",250,90,350,110);
        List<ReadingOrder.Box> duplicated=Arrays.asList(
            rightTop,leftTop,leftTop,rightBottom,leftBottom,rightBottom);
        List<StrictOcrOrderGuard.Item> ids=new ArrayList<>();
        for(int i=0;i<duplicated.size();i++)
            ids.add(new StrictOcrOrderGuard.Item(100+i,"box-"+i));
        LayoutReadingPlanner.Plan d=LayoutReadingPlanner.plan(ids,duplicated,
            Collections.<LayoutRegion>emptyList());
        expect(d.verified.accepted,"duplicate object refs caused invalid permutation");
        expect(d.placements.size()==ids.size(),"a repeated object caused an OCR ID to be lost");
        expect(new java.util.HashSet<Integer>(Arrays.asList(
            d.placements.get(0).ocrId,d.placements.get(1).ocrId,
            d.placements.get(2).ocrId,d.placements.get(3).ocrId,
            d.placements.get(4).ocrId,d.placements.get(5).ocrId)).size()==6,
            "duplicate IDs in output");
        System.out.println("LAYOUT_ORDER_DETERMINISM=PASS");
    }
}
