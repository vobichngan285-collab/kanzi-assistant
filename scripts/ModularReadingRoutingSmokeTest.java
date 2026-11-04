import com.vobichngan.kanzi.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Original synthetic fixtures for five explicitly different reading policies:
 * normal horizontal book, a single vertical glyph column, several independent
 * vertical hospital doors, mixed 302 sign and text, and true 5-column article.
 *
 * Copyright-free generated fixtures; no user image or confidential OCR string.
 */
public final class ModularReadingRoutingSmokeTest {
    static int checks=0;
    static void ok(boolean yes,String message){
        checks++;if(!yes)throw new AssertionError(message);
    }
    static ReadingOrder.Box box(String s,float x,float y,float w,float h,float conf) {
        return new ReadingOrder.Box(s,conf,
            new float[]{x,x+w,x+w,x},
            new float[]{y,y,y+h,y+h});
    }
    static ReadingOrder.Box tilted(String s,float cx,float top,float w,float h,float conf) {
        float x=cx-w/2;
        return new ReadingOrder.Box(s,conf,new float[]{x,x+w,x+w-8,x-8},
            new float[]{top,top+1,top+h,top+h-1});
    }
    static void vertical(List<ReadingOrder.Box> boxes,String name,float cx,float y) {
        for(int i=0;i<name.length();i++)
            boxes.add(box(name.substring(i,i+1),cx-20,y+48*i,40,40,.98f));
    }
    static void singleDoor(){
        List<ReadingOrder.Box> b=new ArrayList<>();
        vertical(b,"内科",190,350);
        vertical(b,"耳鼻喉科",500,330);
        vertical(b,"眼科",850,350);
        var regions=TextRegionPartition.partition(b);
        ok(regions.size()==3,"three independent hospital signs must remain three regions; got "+regions.size());
        CenterTargetTracker focus=new CenterTargetTracker();
        var r=ReadingStrategyRouter.arrange(b,1000,1000,true,true,focus);
        ok(r.centerSelected,"central sign must be selected");
        ok("耳鼻喉科".equals(r.text),"central sign spoken exactly, not neighboring departments: "+r.text);
        ok("SINGLE_VERTICAL".equals(r.strategy),"single-vertical strategy selected");
        ok(r.selectedBoxes==4 && r.totalBoxes==8,"all eight source glyphs retained but only four spoken");
        // Old book reading never silently drops adjacent text.
        var book=ReadingStrategyRouter.arrange(b,1000,1000,true,false,focus);
        ok(!book.centerSelected && book.selectedBoxes==b.size(),
            "book mode must preserve all detected OCR source boxes");
    }
    static void singleOcrLongDoor(){
        List<ReadingOrder.Box> b=new ArrayList<>();
        b.add(box("内科",100,300,40,140,.95f));
        b.add(box("耳鼻喉科",495,320,44,265,.99f));
        b.add(box("眼科",850,300,44,130,.94f));
        var r=ReadingStrategyRouter.arrange(b,1000,980,true,true,new CenterTargetTracker());
        ok("耳鼻喉科".equals(r.text),"entire column recognized as ONE OCR box must be read intact");
        ok("SINGLE_VERTICAL".equals(r.strategy),"one-box sign specialization");
    }
    static void horizontalBook(){
        List<ReadingOrder.Box> b=new ArrayList<>();
        for(int i=0;i<7;i++)
            b.add(box("正常横排文章第"+i+"行全部文字必须保留下来",90,155+i*59,740,40,.96f));
        var r=ReadingStrategyRouter.arrange(b,1000,800,true,true,new CenterTargetTracker());
        ok(r.strategy.equals("HORIZONTAL_PAGE"),"long horizontal page must use complete old flow");
        ok(r.selectedBoxes==7 && !r.centerSelected,"book rows cannot be cropped to central line");
        for(var item:b)ok(r.text.contains(item.text),"horizontal line lost: "+item.text);
    }
    static void mixedCenter(){
        List<ReadingOrder.Box> b=new ArrayList<>();
        vertical(b,"眼科",120,285);
        vertical(b,"耳鼻喉科",495,265);
        b.add(box("302",800,340,120,45,.99f));
        var r=ReadingStrategyRouter.arrange(b,1000,940,true,true,new CenterTargetTracker());
        ok("耳鼻喉科".equals(r.text),"nearby horizontal room number must not be inserted");
        ok(r.centerSelected && r.regions>=3,"mixed vertical/horizontal area partition");
    }
    static void realArticle(){
        List<ReadingOrder.Box> b=new ArrayList<>();
        b.add(box("标题",760,260,140,100,.99f));
        b.add(box("终",360,350,35,36,.99f));
        String[] texts={
            "第一列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第二列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第三列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第四列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第五列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸"
        };
        for(int i=0;i<5;i++)
            b.add(tilted(texts[i],670-60*i,350,39,600,.98f));
        var r=ReadingStrategyRouter.arrange(b,1080,1920,true,true,new CenterTargetTracker());
        ok(r.strategy.equals("MULTI_VERTICAL"),"five-column body must be routed as full article: "+r.description);
        ok(!r.centerSelected && r.selectedBoxes==b.size(),"full traditional article cannot be cropped to central column");
        ok(r.text.indexOf(texts[0])<r.text.indexOf(texts[4]),"right-to-left original five columns");
        ok(ReadingStrategyRouter.preservesCharacters(b,r.text),"full text exact characters maintained");
    }
    static void honestFallback(){
        List<ReadingOrder.Box> bad=Arrays.asList(
            box("甲",160,250,30,30,.20f),box("乙",310,260,30,30,.32f));
        var r=ReadingStrategyRouter.arrange(bad,1000,800,true,true,new CenterTargetTracker());
        ok(!r.centerSelected,"low-confidence isolated glyphs do not pretend to be central target");
        ok(!ReadingStrategyRouter.preservesCharacters(bad,"甲甲乙"),
            "duplicate OCR glyphs must fail character multiset check");
        ok(!ReadingStrategyRouter.preservesCharacters(bad,"甲"),
            "missing OCR glyphs must fail character multiset check");
    }
    static void focusHysteresis(){
        CenterTargetTracker tracker=new CenterTargetTracker();
        List<ReadingOrder.Box> base=new ArrayList<>();
        vertical(base,"耳鼻喉科",505,300);
        vertical(base,"眼科",790,305);
        var first=ReadingStrategyRouter.arrange(base,1000,900,true,true,tracker);
        ok("耳鼻喉科".equals(first.text),"first central target selected");
        List<ReadingOrder.Box> smallMove=new ArrayList<>();
        vertical(smallMove,"耳鼻喉科",515,302);
        vertical(smallMove,"眼科",785,305);
        var second=ReadingStrategyRouter.arrange(smallMove,1000,900,true,true,tracker);
        ok("耳鼻喉科".equals(second.text),"minor hand shake cannot switch target");
        List<ReadingOrder.Box> userAimedElsewhere=new ArrayList<>();
        vertical(userAimedElsewhere,"耳鼻喉科",110,300);
        vertical(userAimedElsewhere,"眼科",490,305);
        var third=ReadingStrategyRouter.arrange(userAimedElsewhere,1000,900,true,true,tracker);
        ok("眼科".equals(third.text),"move camera clearly to new center triggers new target");
    }
    public static void main(String[]args){
        singleDoor();singleOcrLongDoor();horizontalBook();
        mixedCenter();realArticle();honestFallback();focusHysteresis();
        System.out.println("MODULAR_READING_ROUTER=PASS assertions="+checks);
    }
}
