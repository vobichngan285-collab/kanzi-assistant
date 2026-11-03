import com.vobichngan.kanzi.LineOverlayPlanner;
import com.vobichngan.kanzi.ReadingOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure-Java isolated test; never edits app's OCR, overlay or TTS path. */
public final class LineOverlayPlannerSmokeTest {
    private static int assertions = 0;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    private static ReadingOrder.Box box(String text, float x, float y, float w, float h) {
        return new ReadingOrder.Box(text, .9f,
            new float[]{x, x + w, x + w, x},
            new float[]{y, y, y + h, y + h});
    }
    private static ReadingOrder.Box tilted(String text, float x, float y) {
        return new ReadingOrder.Box(text, .93f,
            new float[]{x, x+24, x+24, x},
            new float[]{y, y+3, y+15, y+12});
    }
    private static void exactAndNoLoss(List<ReadingOrder.Box> src, LineOverlayPlanner.Plan plan) {
        check(plan.originalBoxCount == src.size(), "incorrect source count");
        boolean[] seen = new boolean[src.size()];
        for (LineOverlayPlanner.Line line : plan.lines) {
            check(line.sourceIds.length > 0, "empty group");
            for (int id : line.sourceIds) {
                check(id >= 0 && id < src.size(), "out-of-range original id");
                check(!seen[id], "duplicate source OCR id " + id);
                seen[id] = true;
            }
            if (line.merged) check(line.sourceIds.length >= 2, "merged must have >=2 IDs");
            else check(line.sourceIds.length == 1, "fallback must expose each source separately");
        }
        for (int i=0;i<seen.length;i++) check(seen[i], "missing original OCR box " + i);
    }
    private static LineOverlayPlanner.Line merged(LineOverlayPlanner.Plan p) {
        for (LineOverlayPlanner.Line l:p.lines) if (l.merged) return l;
        throw new AssertionError("no merged row");
    }
    private static void testUnorderedSameRow() {
        List<ReadingOrder.Box> source=Arrays.asList(
            box("文",25,20,12,14), box("字",40,20,12,14), box("中",10,20,12,14));
        String oldSpeech=ReadingOrder.arrangeRealtime(source);
        LineOverlayPlanner.Plan p=LineOverlayPlanner.plan(source);
        exactAndNoLoss(source,p);
        check(p.lines.size()==1,"three close fragments must share one line");
        check(p.mergedLineCount==1,"one merged overlay line");
        check(merged(p).rawText.equals("中文字"),"original fragments in geometric order");
        check(Arrays.equals(merged(p).sourceIds,new int[]{2,0,1}),"source IDs not traceable");
        check(ReadingOrder.arrangeRealtime(source).equals(oldSpeech),"must not modify live speech input");
    }
    private static void testTwoAdjacentRows() {
        List<ReadingOrder.Box> source=Arrays.asList(
            box("上",10,20,12,14), box("行",25,20,12,14),
            box("下",10,37,12,14), box("行",25,37,12,14));
        LineOverlayPlanner.Plan p=LineOverlayPlanner.plan(source);
        exactAndNoLoss(source,p);
        check(p.lines.size()==2,"adjacent rows must remain separate");
        check(p.mergedLineCount==2,"each row may merge internally");
        check(p.lines.get(0).rawText.equals("上行"),"upper row stable");
        check(p.lines.get(1).rawText.equals("下行"),"lower row stable");
    }
    private static void testRemoteAndOverlap() {
        List<ReadingOrder.Box> gap=Arrays.asList(box("菜单",10,20,24,14),
                                                 box("价格",160,20,24,14));
        LineOverlayPlanner.Plan a=LineOverlayPlanner.plan(gap);
        exactAndNoLoss(gap,a);
        check(a.lines.size()==2,"distant label must not bridge region");
        List<ReadingOrder.Box> duplicate=Arrays.asList(box("同",10,20,12,14),
                                                       box("一",11,20,12,14));
        LineOverlayPlanner.Plan b=LineOverlayPlanner.plan(duplicate);
        exactAndNoLoss(duplicate,b);
        check(b.lines.size()==2,"overlapping OCR boxes must not be flattened");
    }
    private static void testSlantedRowEnclosesOriginal() {
        List<ReadingOrder.Box> src=Arrays.asList(tilted("倾斜",10,40),tilted("文字",38,43));
        LineOverlayPlanner.Plan p=LineOverlayPlanner.plan(src);
        exactAndNoLoss(src,p);
        check(p.lines.size()==1,"slanted same-baseline fragments should join");
        LineOverlayPlanner.Line row=merged(p);
        // Polygon is a projected parallelogram with top/bottom along its first
        // and fourth corners; every original point must lie inside that band.
        float[] q=row.outline;
        float slope=(q[3]-q[1])/(q[2]-q[0]);
        float lower=q[7], upper=q[1];
        for(ReadingOrder.Box source:src)for(int j=0;j<4;j++) {
            float projected=source.y[j]-slope*(source.x[j]-q[0]);
            check(projected>=upper-.002f && projected<=lower+.002f,
                "new outline clips an original corner");
        }
    }
    private static void testVerticalAndMalformedFallback() {
        List<ReadingOrder.Box> src=Arrays.asList(
            box("竖",10,10,8,18),box("排",10,32,8,18),
            new ReadingOrder.Box("坏框",.2f,
                new float[]{Float.NaN,2,2,1},new float[]{1,1,2,2}),
            null,
            box("",80,80,18,10));
        LineOverlayPlanner.Plan p=LineOverlayPlanner.plan(src);
        exactAndNoLoss(src,p);
        check(p.lines.size()==src.size(),"unsafe, invalid and blank boxes kept individually");
        int unpositionable=0;
        for(LineOverlayPlanner.Line l:p.lines) if(!l.drawable)unpositionable++;
        check(unpositionable==2,"invalid and null boxes not drawable");
    }
    private static void testNoChangeToTextOrGeometry() {
        List<ReadingOrder.Box> original=Arrays.asList(
            box("甲",10,20,12,14),box("乙",25,20,12,14),
            box("标签",90,55,45,14));
        String spoken=ReadingOrder.arrangeRealtime(original);
        List<float[]> before=new ArrayList<>();
        for(ReadingOrder.Box b:original) {
            float[] corners=new float[8];
            for(int i=0;i<4;i++){corners[2*i]=b.x[i];corners[2*i+1]=b.y[i];}
            before.add(corners);
        }
        LineOverlayPlanner.Plan p=LineOverlayPlanner.plan(original);
        exactAndNoLoss(original,p);
        check(ReadingOrder.arrangeRealtime(original).equals(spoken),
            "shadow-only planner changed reading text");
        for(int j=0;j<original.size();j++) {
            for(int i=0;i<4;i++) {
                check(original.get(j).x[i]==before.get(j)[2*i], "x source mutated");
                check(original.get(j).y[i]==before.get(j)[2*i+1], "y source mutated");
            }
        }
        LineOverlayPlanner.Plan empty=LineOverlayPlanner.plan(new ArrayList<>());
        check(empty.lines.isEmpty(),"empty OCR result should create no overlays");
    }
    public static void main(String[] args) {
        testUnorderedSameRow();
        testTwoAdjacentRows();
        testRemoteAndOverlap();
        testSlantedRowEnclosesOriginal();
        testVerticalAndMalformedFallback();
        testNoChangeToTextOrGeometry();
        System.out.println("LINE_OVERLAY_PLANNER_SMOKE_TEST=PASS assertions=" + assertions);
    }
}
