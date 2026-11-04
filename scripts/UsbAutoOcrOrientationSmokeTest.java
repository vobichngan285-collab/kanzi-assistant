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
    public static void main(String[] args) {
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
