import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.VerticalLongColumnLive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Synthetic analogues only. No uploaded user image, private OCR string,
 * source frame or personal text stored in this public repository.
 */
public final class AutoVerticalReadingSmokeTest {
    private static int tests;
    private static void check(boolean ok,String message) {
        tests++;
        if(!ok) throw new AssertionError(message);
    }
    private static ReadingOrder.Box box(String text,float conf,float[] x,float[] y) {
        return new ReadingOrder.Box(text,conf,x,y);
    }
    private static ReadingOrder.Box rect(String text,float conf,
                                          float x,float y,float w,float h) {
        return box(text,conf,new float[]{x,x+w,x+w,x},
             new float[]{y,y,y+h,y+h});
    }
    private static String chinese(int n) {
        return "第"+n+"段中文原样测试离线摄像头实时识字顺序完整无修改甲乙丙丁";
    }
    private static List<ReadingOrder.Box> realPerspective() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        b.add(rect("标题甲",.99f,715,439,220,180));       // #1
        b.add(rect("尾",.99f,365,474,34,37));             // #2 leftmost continuation
        b.add(box(chinese(3),.999f,                    // #3 upright anchor, RIGHT
            new float[]{615,652,616,579},
            new float[]{571,574,1148,1146}));
        b.add(rect("标题乙",.99f,863,597,145,150));       // #4
        b.add(rect("书写说明",.75f,757,628,114,182));       // #5
        b.add(rect("ZHONG YI",.98f,745,645,30,150));     // #6
        b.add(rect("店铺名称",.99f,757,889,211,145));      // #7
        b.add(rect("毕业学校",.99f,721,1028,191,62));    // #8
        b.add(rect("专业培训",.99f,803,1093,196,63));    // #9
        b.add(box(chinese(10),.989f,                   // #10 LEFT
            new float[]{343,417,452,378},
            new float[]{1127,475,479,1131}));
        b.add(box(chinese(11),.974f,                   // #11
            new float[]{402,467,503,438},
            new float[]{1131,478,481,1134}));
        b.add(box(chinese(12),.966f,                   // #12
            new float[]{459,518,558,498},
            new float[]{1136,481,485,1139}));
        b.add(box(chinese(13),.982f,                   // #13
            new float[]{521,570,605,556},
            new float[]{1141,486,489,1143}));
        b.add(rect("其他注释",.69f,1001,1796,30,58));// #14
        return b;
    }
    private static List<ReadingOrder.Box> rotatedHorizontal() {
        List<ReadingOrder.Box> b=new ArrayList<>();
        for(int i=0;i<5;i++) {
            float x=370+54*i;
            // Landscape book turned sideways: the FIRST OCR edge runs upward
            // 600px and the short edge runs horizontally. Geometry is tall
            // but there is no upright body anchor.
            b.add(box(chinese(i),.99f,
                new float[]{x,x+57,x+92,x+35},
                new float[]{1080,445,448,1084}));
        }
        return b;
    }
    public static void main(String[]args) {
        List<ReadingOrder.Box> real=realPerspective();
        var result=VerticalLongColumnLive.tryArrange(true,real,1080,1920);
        check(result.used,"upright 5-column sign should auto-match: "+result.status);
        check(result.columnIds.equals(Arrays.asList(2,12,11,10,9)),
            "right-to-left order must use original frame indices: "+result.columnIds);
        check(result.trailingGlyphId==1,"proven final glyph must be appended once");
        check(result.preservedBoxCount==14,"must retain all 14 original OCR regions");
        check(result.text.startsWith(chinese(3)+chinese(13)+chinese(12)+chinese(11)+chinese(10)+"尾"),
            "must preserve each original column's characters and join exact order");
        check(!VerticalLongColumnLive.tryArrange(false,real,1080,1920).used,
            "emergency user disable must restore old behavior");
        var rotated=VerticalLongColumnLive.tryArrange(true,rotatedHorizontal(),1080,1920);
        check(!rotated.used,
            "rotated horizontal book must NOT be misclassified as traditional vertical: "+rotated.status);
        List<ReadingOrder.Box> damaged=new ArrayList<>(real);
        damaged.set(11,box("乱码",.35f,
            new float[]{459,518,558,498},
            new float[]{1136,481,485,1139}));
        check(!VerticalLongColumnLive.tryArrange(true,damaged,1080,1920).used,
            "missing long column must fail closed, never invent characters");
        List<ReadingOrder.Box> horizontal=new ArrayList<>();
        for(int i=0;i<7;i++)
            horizontal.add(rect("正常横排文字内容甲乙丙丁戊己庚",.99f,120,310+i*40,700,24));
        check(!VerticalLongColumnLive.tryArrange(true,horizontal,1080,1920).used,
            "normal book should retain original realtime order");
        System.out.println("AUTO_VERTICAL_HAN_READING=PASS checks="+tests);
    }
}
