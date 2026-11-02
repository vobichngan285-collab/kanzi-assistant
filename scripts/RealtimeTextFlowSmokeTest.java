import com.vobichngan.kanzi.ReadingOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Real-time OCR: read product title top-first and coherent independent areas.
 * Do not invent brand names or delete smaller text solely by box confidence.
 */
public final class RealtimeTextFlowSmokeTest {
    private static void check(boolean ok,String message) {
        if(!ok)throw new AssertionError(message);
    }
    private static ReadingOrder.Box box(String text,int l,int t,int w,int h,float confidence) {
        return new ReadingOrder.Box(text,confidence,
            new float[]{l,l+w,l+w,l},new float[]{t,t,t+h,t+h});
    }
    private static void inOrder(String text,String...parts) {
        int previous=-1;
        for(String part:parts){
            int index=text.indexOf(part);
            check(index>=0,"missing OCR text "+part+" from "+text);
            check(text.indexOf(part,index+1)<0,"duplicated text "+part+" in "+text);
            check(index>previous,"out-of-order OCR text: "+text);
            previous=index;
        }
    }
    public static void main(String[]args){
        // Detection order is arbitrary, as in the milk-carton sample.
        List<ReadingOrder.Box> milk=Arrays.asList(
            box("优质蛋白",210,148,95,22,.95f),
            box("原生高钙",30,145,95,22,.94f),
            box("有机高钙纯牛奶",92,76,220,31,.90f),
            box("净含量200mL",40,240,150,20,.52f),
            box("特仑苏",106,15,180,31,.94f));
        String text=ReadingOrder.arrangeRealtime(milk);
        inOrder(text,"特仑苏","有机高钙纯牛奶","原生高钙","优质蛋白","净含量200mL");
        // Packaging side text is important, even when it is short or faint.
        List<ReadingOrder.Box> note=Arrays.asList(
            box("第二行说明",20,66,120,19,.87f),
            box("侧边小字",220,42,85,15,.27f),
            box("第一行说明",20,40,120,19,.92f),
            box("上方标签",50,3,140,20,.95f));
        String notes=ReadingOrder.arrangeRealtime(note);
        check(notes.startsWith("上方标签"),"top label must be first: "+notes);
        check(notes.contains("侧边小字"),"low-confidence side info must not be lost");
        check(notes.indexOf("第一行说明")<notes.indexOf("第二行说明"),
            "a coherent paragraph cannot read backwards");
        System.out.println("REALTIME_TEXT_FLOW=PASS");
    }
}
