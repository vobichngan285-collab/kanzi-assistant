import com.vobichngan.kanzi.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v52 isolated policy A/B fixtures, generated geometry (not phone images).
 * Full OCR content must survive every reading scenario.
 */
public final class ModularReadingRoutingSmokeTest {
    static int checks=0;
    static void ok(boolean condition,String message) {
        checks++;if(!condition)throw new AssertionError(message);
    }
    static ReadingOrder.Box box(String s,float x,float y,float w,float h,float conf) {
        return new ReadingOrder.Box(s,conf,
            new float[]{x,x+w,x+w,x},
            new float[]{y,y,y+h,y+h});
    }
    static ReadingOrder.Box tilted(String s,float cx,float y,float w,float h,float conf) {
        float x=cx-w/2;
        return new ReadingOrder.Box(s,conf,new float[]{x,x+w,x+w-8,x-8},
            new float[]{y,y+1,y+h,y+h-1});
    }
    static void vertical(List<ReadingOrder.Box> out,String s,float cx,float y) {
        for(int i=0;i<s.length();i++)
            out.add(box(s.substring(i,i+1),cx-20,y+48*i,40,40,.98f));
    }
    static ReadingStrategyRouter.Result read(List<ReadingOrder.Box> b,int w,int h) {
        var r=ReadingStrategyRouter.arrange(b,w,h,true,true);
        ok(!r.centerSelected,"center selection is permanently disabled");
        ok(r.selectedBoxes==r.totalBoxes && r.totalBoxes==b.size(),
            "every OCR box must be scheduled for speech, not a subset");
        ok(ReadingStrategyRouter.preservesCharacters(b,r.text),
            "missing/duplicated source characters: "+r.strategy+" "+r.text);
        return r;
    }
    static void separateDoorSigns() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        vertical(b,"内科",190,350);
        vertical(b,"耳鼻喉科",500,330);
        vertical(b,"眼科",850,350);
        var r=read(b,1000,1000);
        ok("VERTICAL_SIGNS_FULL".equals(r.strategy),"isolated door signs route");
        ok("内科\n耳鼻喉科\n眼科".equals(r.text),
            "left-to-right separate signs, each top-to-bottom: "+r.text);
    }
    static void twoCharacterSign() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        vertical(b,"眼科",505,300);
        var r=read(b,1000,900);
        ok("眼科".equals(r.text),"two-glyph vertical sign not omitted");
    }
    static void wholeVerticalSignBoxes() {
        List<ReadingOrder.Box> b=Arrays.asList(
            box("内科",100,300,40,140,.95f),
            box("耳鼻喉科",495,320,44,265,.99f),
            box("眼科",850,300,44,130,.94f));
        var r=read(b,1000,980);
        ok("内科\n耳鼻喉科\n眼科".equals(r.text),
            "complete single-box vertical signs in left-to-right sequence: "+r.text);
    }
    static void horizontalSignNumbers() {
        List<ReadingOrder.Box> b=Arrays.asList(
            box("101 内科",70,80,150,38,.99f),
            box("102 耳鼻喉科",390,80,230,38,.97f),
            box("103 眼科",760,80,160,38,.98f));
        var r=read(b,1000,760);
        ok(r.text.indexOf("101")<r.text.indexOf("102") &&
           r.text.indexOf("102")<r.text.indexOf("103"),
           "horizontal labels should read left-to-right: "+r.text);
    }
    static void horizontalPage() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        for(int i=0;i<7;i++)
            b.add(box("横排正文第"+i+"行有价格120元和数字302",
                60,100+i*58,800,40,.96f));
        var r=read(b,1000,900);
        ok("HORIZONTAL_ROWS_FULL".equals(r.strategy),"horizontal full text, line-first reading without focus selection");
        for(int i=0;i<6;i++)ok(r.text.indexOf("第"+i+"行")<r.text.indexOf("第"+(i+1)+"行"),
            "horizontal rows follow top-to-bottom");
    }
    static void mixedDoorAndRoomNumber() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        vertical(b,"眼科",120,285);
        vertical(b,"耳鼻喉科",495,265);
        b.add(box("302",800,340,120,45,.99f));
        var r=read(b,1000,940);
        ok(r.text.contains("眼")&&r.text.contains("耳")&&r.text.contains("302"),
            "mixed numbers, signs must all survive");
    }
    static void priceListRegression() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        b.add(box("价目表",440,100,180,60,.99f));
        String[] services={"中医推拿一小时","半小时","精油开背50分钟",
            "肠胃调理50分钟","肾部保养40分钟","会员卡充值",
            "充1000送200","充2000送600"};
        String[] prices={"90元","50元","120元","60元","40元","1000元","2000元","302"};
        for(int i=0;i<services.length;i++) {
            b.add(box(services[i],85,190+i*105,255,32,.93f));
            b.add(box(prices[i],800,190+i*105,130,32,.97f));
        }
        // Dense OCR text fragments, including vertically erroneous source boxes.
        for(int i=0;i<4;i++)
            b.add(box(new String[]{"刺经滑拔","络络罐罐","放刮走","血痧罐敷"}[i],
                415+i*40,930,36,168,.86f));
        // Fill a complex page like the 35-nonblank-box field diagnostic.
        for(int i=0;i<14;i++)
            b.add(box("项目"+i,125+(i%2)*560,1110+(i/2)*65,105,31,.90f));
        ok(b.size()==35,"price-list fixture must contain 35 source boxes");
        var r=read(b,1080,1920);
        ok("HORIZONTAL_ROWS_FULL".equals(r.strategy),"35-box price list uses full row ordering");
        ok(r.text.indexOf("中医推拿一小时")<r.text.indexOf("90元") &&
           r.text.indexOf("90元")<r.text.indexOf("半小时") &&
           r.text.indexOf("半小时")<r.text.indexOf("50元"),
           "same-row service must be spoken before its price");
        ok(r.text.contains("120元")&&r.text.contains("充2000送600")&&
           r.text.contains("刺经滑拔"),"prices, digits and flawed source boxes all preserved");
    }
    static void closeVerticalPriceColumnsFallback() {
        List<ReadingOrder.Box> b=Arrays.asList(
            box("刺经滑拔",410,920,36,180,.92f),
            box("络络罐罐",451,920,36,180,.92f),
            box("放刮走",492,920,36,180,.92f),
            box("血痧罐敷",533,920,36,180,.92f));
        var r=read(b,1080,1920);
        ok(!"VERTICAL_SIGNS_FULL".equals(r.strategy),
            "closely packed transposed price columns are not independent doorplates");
    }
    static void traditionalFiveColumns() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        b.add(box("标题",760,260,140,100,.99f));
        b.add(box("终",360,350,35,36,.99f));
        String[] cols={
            "第一列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第二列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第三列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第四列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸",
            "第五列正文内容只使用真实OCR的汉字顺序甲乙丙丁戊己庚辛壬癸"
        };
        for(int i=0;i<5;i++)
            b.add(tilted(cols[i],670-60*i,350,39,600,.98f));
        var r=read(b,1080,1920);
        ok("MULTI_VERTICAL".equals(r.strategy),"five genuine traditional columns");
        for(int i=0;i<4;i++)
            ok(r.text.indexOf(cols[i])<r.text.indexOf(cols[i+1]),
                "traditional order right-to-left");
    }
    static void unchangedBookAndLowConfidence() {
        List<ReadingOrder.Box> b=Arrays.asList(
            box("甲",160,250,30,30,.20f),box("乙",310,260,30,30,.32f));
        var r=read(b,1000,800);
        ok(!"VERTICAL_SIGNS_FULL".equals(r.strategy),"low-confidence glyphs not guessed");
        var book=ReadingStrategyRouter.arrange(b,1000,800,true,false);
        ok(ReadingStrategyRouter.preservesCharacters(b,book.text),
            "book path must keep complete characters");
        ok(!ReadingStrategyRouter.preservesCharacters(b,"甲甲乙"),"cannot duplicate");
        ok(!ReadingStrategyRouter.preservesCharacters(b,"甲"),"cannot omit");
    }
    public static void main(String[]args) {
        separateDoorSigns();twoCharacterSign();wholeVerticalSignBoxes();
        horizontalSignNumbers();horizontalPage();mixedDoorAndRoomNumber();
        priceListRegression();closeVerticalPriceColumnsFallback();
        traditionalFiveColumns();unchangedBookAndLowConfidence();
        System.out.println("SIMPLE_COMPLETE_READING=PASS assertions="+checks);
    }
}
