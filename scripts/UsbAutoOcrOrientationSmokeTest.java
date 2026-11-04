import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.UsbAutoOcrOrientation;
import java.util.ArrayList;
import java.util.List;

/** Synthetic coordinates and dummy text; never store user camera samples. */
public final class UsbAutoOcrOrientationSmokeTest {
    private static int checks=0;
    static void yes(boolean value,String name) {
        checks++; if(!value)throw new AssertionError(name);
    }
    static ReadingOrder.Box box(String text,float confidence,float x,float y,float w,float h) {
        return new ReadingOrder.Box(text,confidence,
            new float[]{x,x+w,x+w,x},
            new float[]{y,y,y+h,y+h});
    }
    static List<ReadingOrder.Box> sideOnUsb() {
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(int i=0;i<5;i++)
            boxes.add(box(new String[]{"中","模糊","字","短字","无"}[i],
                .65f,1090,335+52*i,620,30));
        boxes.add(box("店铺",.97f,1190,690,120,150));
        return boxes;
    }
    static List<ReadingOrder.Box> correctlyOriented() {
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(int i=0;i<5;i++) {
            float x=720-57*i;
            boxes.add(box("测试列甲乙丙丁戊己庚辛壬癸承接下一行结束",.97f,
                x,390,38,600));
        }
        boxes.add(box("商店标签",.97f,850,450,110,50));
        return boxes;
    }
    static List<ReadingOrder.Box> clearHorizontal() {
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(int i=0;i<6;i++)
            boxes.add(box("正常横排中文文字已经完整识别而且不需要旋转",.98f,
                110,200+i*47,630,34));
        return boxes;
    }
    /** Mirror anonymous GEOMETRY of two observed 1920x1080 USB failures.
     * Test strings are synthetic and are not copied from private photos. */
    static List<ReadingOrder.Box> readableButSidewaysPriceList() {
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(int i=0;i<15;i++)
            boxes.add(box("测试价格120元",.98f,610+i*42,485,35,198));
        boxes.add(box("结束",.97f,1140,740,28,42));
        return boxes;
    }
    static List<ReadingOrder.Box> uprightPriceList() {
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(int i=0;i<15;i++)
            boxes.add(box("测试价格120元",.98f,170,240+i*65,210,36));
        return boxes;
    }
    static List<ReadingOrder.Box> genuineVerticalArticle() {
        List<ReadingOrder.Box> boxes=new ArrayList<>();
        for(int i=0;i<5;i++)
            boxes.add(box("原始竖排测试正文甲乙丙丁戊己庚辛壬癸",.97f,
                810-i*55,320,35,500));
        return boxes;
    }
    static void sidewaysCacheRegression() {
        var price=UsbAutoOcrOrientation.quality(readableButSidewaysPriceList());
        var upright=UsbAutoOcrOrientation.quality(uprightPriceList());
        var genuine=UsbAutoOcrOrientation.quality(genuineVerticalArticle());
        yes(price.brokenWideRows==0,"sideways price text can be fluent not garbled");
        yes(price.sidewaysTallRows>=12,"sideways price columns detected from OCR geometry");
        yes(UsbAutoOcrOrientation.needsProbe(price,1920,1080),
            "sideways readable price MUST be offered a real rotated OCR A/B");
        yes(UsbAutoOcrOrientation.accept(price,upright),
            "real horizontally reoriented text wins with no missing characters");
        yes(!UsbAutoOcrOrientation.accept(upright,price),
            "reverse sideways candidate must be rejected");
        yes(!UsbAutoOcrOrientation.accept(price,price),
            "geometry-only guess never auto-adopted");
        yes(!UsbAutoOcrOrientation.needsProbe(price,1080,1920),
            "do not rotate portrait video just because it has vertical columns");
        yes(!UsbAutoOcrOrientation.needsProbe(genuine,1920,1080),
            "natural long Chinese vertical article should stay upright");
        yes(UsbAutoOcrOrientation.shouldRevisit(price,3),
            "cached verified angle can be reconsidered after repeated sideways geometry");
        var garbled=UsbAutoOcrOrientation.quality(sideOnUsb());
        yes(UsbAutoOcrOrientation.needsProbe(garbled,1920,1080),
            "five long wide broken rows still trigger first-frame recalibration");
        yes(!UsbAutoOcrOrientation.accept(garbled,price),
            "candidate with insufficient OCR character improvement must fail");
    }
    public static void main(String[] args) {
        sidewaysCacheRegression();
        var bad=UsbAutoOcrOrientation.quality(sideOnUsb());
        var good=UsbAutoOcrOrientation.quality(correctlyOriented());
        var normal=UsbAutoOcrOrientation.quality(clearHorizontal());
        yes(bad.brokenWideRows==5,"5 defective sideways wide crops recognized");
        yes(good.plausibleVerticalColumns==5,"5 real complete vertical body columns recognized");
        yes(UsbAutoOcrOrientation.needsProbe(bad,1920,1080),
            "sideways USB original must trigger rare auto calibration");
        yes(!UsbAutoOcrOrientation.needsProbe(bad,1080,1920),
            "portrait input must not be rotated just because it has wide labels");
        yes(!UsbAutoOcrOrientation.needsProbe(normal,1920,1080),
            "good horizontal ordinary text needs no calibration");
        yes(UsbAutoOcrOrientation.accept(bad,good),
            "actual OCR of rotated pixels must win against fragmented source");
        yes(!UsbAutoOcrOrientation.accept(good,bad),
            "garbage candidate must never replace good real OCR");
        yes(!UsbAutoOcrOrientation.accept(bad,bad),
            "no improvement means no automatic adoption");
        yes(UsbAutoOcrOrientation.normalizePreference(-1)==-1,
            "automatic preference must survive normalization");
        yes(UsbAutoOcrOrientation.normalizePreference(270)==270,
            "manual fallback must survive normalization");
        yes(UsbAutoOcrOrientation.normalizePreference(-90)==0,
            "invalid angle must fail closed");
        yes(UsbAutoOcrOrientation.shouldRevisit(bad,3),
            "cached angle may be reconsidered after three bad readings");
        yes(!UsbAutoOcrOrientation.shouldRevisit(bad,1),
            "must not recalibrate on every transient bad frame");
        yes(!UsbAutoOcrOrientation.shouldRevisit(good,9),
            "good cached direction never recalibrates");
        System.out.println("USB_AUTO_OCR_DIRECTION=PASS checks="+checks);
    }
}
