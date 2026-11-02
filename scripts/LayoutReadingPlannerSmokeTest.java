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
    private static ReadingOrder.Box slanted(String text, float x, float top) {
        return new ReadingOrder.Box(text, 1f,
            new float[]{x,x+100,x+100,x},
            new float[]{top,top+20,top+40,top+20});
    }
    private static void assertSequence(LayoutReadingPlanner.Plan plan, String expected) {
        check(plan.verified.accepted,"planner must pass strict OCR ID validation");
        check(plan.verified.exactText().equals(expected),
            "unexpected reading sequence: " + plan.verified.exactText());
    }
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
        // Single broad AI region must NOT prevent splitting a two-page book.
        LayoutReadingPlanner.Plan broad=LayoutReadingPlanner.plan(items,boxes,
            Collections.singletonList(new LayoutRegion(2,.99f,0,0,400,100)));
        assertSequence(broad,"左页第一行\n左页第二行\n右页第一行\n右页第二行");

        // When geometry detects multiple columns, a full-width heading ABOVE
        // both columns must be first, not inserted between left and right pages.
        List<StrictOcrOrderGuard.Item> headingItems=Arrays.asList(
            new StrictOcrOrderGuard.Item(90,"跨页总标题"),
            new StrictOcrOrderGuard.Item(20,"右页第一行"),
            new StrictOcrOrderGuard.Item(10,"左页第一行"),
            new StrictOcrOrderGuard.Item(21,"右页第二行"),
            new StrictOcrOrderGuard.Item(11,"左页第二行"));
        List<ReadingOrder.Box> headingBoxes=Arrays.asList(
            new ReadingOrder.Box("跨页总标题",1f,
                new float[]{0,450,450,0},new float[]{0,0,20,20}),
            box("右页第一行",300,80),box("左页第一行",10,80),
            box("右页第二行",300,120),box("左页第二行",10,120));
        assertSequence(LayoutReadingPlanner.plan(headingItems,headingBoxes,
            Collections.<LayoutRegion>emptyList()),
            "跨页总标题\n左页第一行\n左页第二行\n右页第一行\n右页第二行");

        // Camera perspective: line 1 right half is physically below the
        // beginning of line 2. Compare projected baselines, not centerY.
        List<StrictOcrOrderGuard.Item> skewItems=Arrays.asList(
            new StrictOcrOrderGuard.Item(1,"第一行左"),
            new StrictOcrOrderGuard.Item(2,"第二行"),
            new StrictOcrOrderGuard.Item(3,"第一行右"));
        List<ReadingOrder.Box> skewBoxes=Arrays.asList(
            slanted("第一行左",0,10),
            slanted("第二行",0,36),
            slanted("第一行右",140,38));
        assertSequence(LayoutReadingPlanner.plan(skewItems,skewBoxes,
            Collections.<LayoutRegion>emptyList()),"第一行左\n第一行右\n第二行");

        // A product label must preserve exactly the OCR characters and numbers.
        List<StrictOcrOrderGuard.Item> milk=Arrays.asList(
            new StrictOcrOrderGuard.Item(8,"蛋白质3.2g"),
            new StrictOcrOrderGuard.Item(9,"净含量250mL"),
            new StrictOcrOrderGuard.Item(7,"纯牛奶"));
        List<ReadingOrder.Box> milkBoxes=Arrays.asList(
            box("蛋白质3.2g",20,90),
            box("净含量250mL",20,50),
            box("纯牛奶",20,10));
        assertSequence(LayoutReadingPlanner.plan(milk,milkBoxes,
            Collections.<LayoutRegion>emptyList()),"纯牛奶\n净含量250mL\n蛋白质3.2g");
        System.out.println("LAYOUT_READING_PLANNER=PASS");
    }
}
