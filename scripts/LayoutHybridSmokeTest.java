import com.vobichngan.kanzi.LayoutRegion;
import com.vobichngan.kanzi.ReadingOrder;
import java.util.Arrays;
import java.util.List;

/** Proves ML boundaries can block an incorrect geometric join without removing text. */
public final class LayoutHybridSmokeTest {
    private static ReadingOrder.Box box(String t, int x,int y,int w,int h) {
        return new ReadingOrder.Box(t,.94f,
            new float[]{x,x+w,x+w,x},new float[]{y,y,y+h,y+h});
    }
    private static void check(boolean yes,String message) {
        if(!yes)throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<ReadingOrder.Box> blocks=Arrays.asList(
            box("左区域第一行",40,100,125,22),
            box("旁注第一行",170,100,100,22),
            box("左区域第二行",40,127,125,22),
            box("旁注第二行",170,127,100,22),
            box("左区域第三行",40,154,125,22),
            box("旁注第三行",170,154,100,22));
        List<LayoutRegion> regions=Arrays.asList(
            new LayoutRegion(2,.96f,30,90,166,190),
            new LayoutRegion(12,.93f,169,90,280,190));
        String hybrid=ReadingOrder.arrange(blocks,regions);
        String legacy=ReadingOrder.arrange(blocks);
        check(hybrid.contains("左区域第一行\n左区域第二行\n左区域第三行"),
            "AI failed to keep text region continuous: "+hybrid);
        check(hybrid.contains("旁注第一行\n旁注第二行\n旁注第三行"),
            "AI failed to keep note region continuous: "+hybrid);
        check(!hybrid.contains("左区域第一行旁注第一行"),
            "ML layout boundaries did not block cross-region joining: "+hybrid);
        for(ReadingOrder.Box item:blocks)
            check(hybrid.contains(item.text),"lost recognized text: "+item.text);
        String fallback=ReadingOrder.arrange(blocks,Arrays.asList(regions.get(0)));
        check(legacy.equals(fallback),
            "single uncertain AI region must fall back to geometric reading");
        // A model cannot fill in text, invent a new phrase or rewrite OCR.
        String cleaned=hybrid.replaceAll("[\\s]+","");
        String all="左区域第一行左区域第二行左区域第三行旁注第一行旁注第二行旁注第三行";
        for(String expected:Arrays.asList("左区域第一行","旁注第一行"))
            check(cleaned.contains(expected),"OCR preservation failure");
        System.out.println("AI_GEOMETRY_HYBRID_SMOKE_TEST=PASS");
    }
}
