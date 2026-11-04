import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.VerticalLongColumnLive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Synthetic data only: no uploaded user photograph or verbatim text. */
public final class VerticalLongColumnLiveSmokeTest {
    static int count=0;
    static void yes(boolean b,String s){count++;if(!b)throw new AssertionError(s);}
    static ReadingOrder.Box box(String text,float cx,float top,float width,float height,float conf){
        float x=cx-width/2;
        return new ReadingOrder.Box(text,conf,
            new float[]{x,x+width,x+width-12,x-12},
            new float[]{top,top+2,top+height,top+height-2});
    }
    static String txt(int n){return "第"+n+"列正文甲乙丙丁戊己庚辛壬癸中医调理甲乙丙丁"; }
    static List<ReadingOrder.Box> five(){
        List<ReadingOrder.Box> list=new ArrayList<>();
        list.add(box("标题",810,270,160,120,.99f));         // #1
        list.add(box("终",350,330,31,35,.99f));            // #2 continuation
        list.add(box(txt(3),425,330,44,600,.98f));         // #3
        list.add(box(txt(4),475,326,44,604,.98f));         // #4
        list.add(box(txt(5),575,410,44,522,.98f));         // #5 first/rightmost
        list.add(box("店铺",740,640,130,75,.99f));         // #6
        list.add(box(txt(7),525,322,44,612,.98f));         // #7
        list.add(box(txt(8),375,327,44,602,.97f));         // #8 last/leftmost
        return list;
    }
    public static void main(String[]args){
        List<ReadingOrder.Box> sample=five();
        var disabled=VerticalLongColumnLive.tryArrange(false,sample,1080,1920);
        yes(!disabled.used && disabled.text.isEmpty(),"MUST NOT change default horizontal/live path");
        var live=VerticalLongColumnLive.tryArrange(true,sample,1080,1920);
        yes(live.used, "explicitly selected five Chinese vertical columns should pass: "+live.status);
        yes(live.columnIds.equals(Arrays.asList(4,6,3,2,7)),
            "source IDs must order right-to-left "+live.columnIds);
        yes(live.preservedBoxCount==sample.size(),"must preserve ALL original boxes");
        String expected=txt(5)+txt(7)+txt(4)+txt(3)+txt(8)+"终";
        yes(live.text.startsWith(expected),"paragraph must be continuous with next-column joins and trailing glyph");
        yes(live.trailingGlyphId==1,"one separate last character should merge only by strict geometry");
        for(ReadingOrder.Box b:sample) {
            yes(live.text.contains(b.text),"missing original text: "+b.text);
            yes(live.text.indexOf(b.text)==live.text.lastIndexOf(b.text),
                "duplicate original text: "+b.text);
        }
        // Explicit mode itself must NOT be enough: a normal horizontal page
        // or noisy short crops must be rejected automatically.
        List<ReadingOrder.Box> horizontal=new ArrayList<>();
        for(int i=0;i<8;i++)
            horizontal.add(box("正常横排书页甲乙丙丁戊己庚辛壬"+i,440,250+i*36,550,30,.98f));
        yes(!VerticalLongColumnLive.tryArrange(true,horizontal,1080,1920).used,
            "no vertical false positive on horizontal page");
        List<ReadingOrder.Box> low=new ArrayList<>(sample);
        low.set(4,box(txt(5),575,410,44,522,.55f));
        yes(!VerticalLongColumnLive.tryArrange(true,low,1080,1920).used,
            "must fail closed on low confidence center column");
        List<ReadingOrder.Box> missing=new ArrayList<>(sample);
        missing.set(4,box("一",575,410,44,522,.98f));
        yes(!VerticalLongColumnLive.tryArrange(true,missing,1080,1920).used,
            "unrecovered crop must never be hallucinated from neighboring lines");
        // An ambiguous extra short glyph must never be merged as a continuation.
        List<ReadingOrder.Box> ambiguous=new ArrayList<>(sample);
        ambiguous.add(box("续",290,340,25,31,.99f));
        var safe=VerticalLongColumnLive.tryArrange(true,ambiguous,1080,1920);
        yes(safe.used && safe.trailingGlyphId==-1,"ambiguous short glyph should remain separate");
        yes(safe.preservedBoxCount==ambiguous.size(),"ambiguous glyph also retained");
        System.out.println("VERTICAL_LONG_COLUMN_LIVE=PASS checks="+count);
    }
}
