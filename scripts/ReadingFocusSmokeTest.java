import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.ReadingFocus;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/** Regression vectors from the user's real 0.9.4/0.9.5 720x1280 book diagnostics. */
public final class ReadingFocusSmokeTest {
    private static ReadingOrder.Box box(String text, float left, float top,
                                        float width, float height, float conf) {
        return new ReadingOrder.Box(text, conf,
            new float[] {left,left+width,left+width,left},
            new float[] {top,top,top+height,top+height});
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static ReadingFocus.Selection select(List<ReadingOrder.Box> boxes,
                                                  ReadingFocus.Mode mode) {
        return ReadingFocus.choose(boxes, 720, 1280, mode, Float.NaN);
    }
    public static void main(String[] unused) {
        // 0.9.5 Small: right-hand body complete, cropped neighbor at x=0,
        // small-font marginal citations near x=605; same photo contains all.
        List<ReadingOrder.Box> small = new ArrayList<>();
        int[] top = {405,419,433,450,465,481,505,515,535,552,570,589,606,626,645,666,
                     686,708,729,751,775,800,824,847,872,898,924,950,977};
        int[] x = {311,298,284,294,273,271,269,268,275,263,272,270,248,246,243,240,
                   239,237,234,233,230,228,247,224,221,218,215,213,210};
        for (int i=0;i<top.length;i++)
            small.add(box("正文第"+i+"行，罗切斯特先生在这一页继续完整的叙述。",
                x[i],top[i],576-x[i]+i,20,.97f));
        int[] leftTop = {578,597,616,637,658,678,739,767,795,818,845,873,902,960};
        for (int i=0;i<leftTop.length;i++)
            small.add(box("被截断的左侧残页"+i,0,leftTop[i],80+ i%3*2,20,.93f));
        int[] marginTop = {566,579,739,753,767,781,796,810,825,839};
        for (int i=0;i<marginTop.length;i++)
            small.add(box("脚注"+i+"与注释",603,marginTop[i],84,14,.9f));
        small.add(box("LaeB",400,381,103,18,.31f));
        ReadingFocus.Selection main = select(small,ReadingFocus.Mode.AUTO);
        check(main.isolated, "must isolate main page in partial-open-book view");
        check(main.text.contains("正文第0行") && main.text.contains("正文第28行"),
              "cannot truncate complete page: "+main.description);
        check(!main.text.contains("残页") && !main.text.contains("脚注") &&
              !main.text.contains("LaeB"),
              "side pages, notes and spurious low confidence label must not speak: "+main.text);
        ReadingFocus.Selection right = select(small,ReadingFocus.Mode.RIGHT);
        check(right.text.contains("正文第0行") && !right.text.contains("脚注"),
              "manual RIGHT must mean right book page, not far-right footnotes");
        ReadingFocus.Selection left = select(small,ReadingFocus.Mode.LEFT);
        check(left.text.contains("残页") && !left.text.contains("正文"),
              "manual LEFT should deliberately select partially visible left page: "+left.description);
        ReadingFocus.Selection all = select(small,ReadingFocus.Mode.ALL);
        check(all.text.contains("残页") && all.text.contains("正文") && all.text.contains("脚注"),
              "ALL must preserve old full-image diagnostic/reading behavior");

        // 0.9.4 Tiny: left x=0-175 and right x=190-550, plus narrow x=548 notes.
        List<ReadingOrder.Box> tiny = new ArrayList<>();
        for (int i=0;i<15;i++)
            tiny.add(box("左侧边缘露出的半句话"+i,0,539+i*27,155-i*3,23,.8f));
        for (int i=0;i<14;i++)
            tiny.add(box("完整主文章节，正在阅读的是这一面的整页书"+i,
                223-i*3,691+i*24,325+i*3,24,.95f));
        for (int i=0;i<9;i++)
            tiny.add(box("旁边小字"+i,545,460+i*22,74,13,.8f));
        ReadingFocus.Selection tinyFocus=select(tiny,ReadingFocus.Mode.AUTO);
        check(tinyFocus.isolated && tinyFocus.text.contains("完整主文") &&
            !tinyFocus.text.contains("边缘") && !tinyFocus.text.contains("小字"),
            "Tiny screenshot book regions: "+tinyFocus.description);

        // A single normal paragraph, sign, or upright vertical Chinese text
        // should keep all text and should not accidentally focus one glyph.
        List<ReadingOrder.Box> paragraph = new ArrayList<>();
        for(int i=0;i<9;i++)
            paragraph.add(box("单页第"+i+"行书本正文",170,270+i*31,355,24,.96f));
        check(!select(paragraph,ReadingFocus.Mode.AUTO).isolated,
              "single page must keep legacy reading");
        List<ReadingOrder.Box> upright = new ArrayList<>();
        for(int col=0;col<3;col++)for(int row=0;row<5;row++)
            upright.add(box("中", 450-col*55,200+row*29,22,23,.96f));
        check(!select(upright,ReadingFocus.Mode.AUTO).isolated,
              "true vertical glyph columns should keep vertical handling");

        // Two complete independent pages and a small corner label.
        List<ReadingOrder.Box> spread=new ArrayList<>();
        for(int i=0;i<10;i++){
            spread.add(box("左边正文第"+i+"行完整",35,240+i*32,260,23,.98f));
            spread.add(box("右边正文第"+i+"行完整",390,240+i*32,263,23,.98f));
        }
        check(select(spread,ReadingFocus.Mode.LEFT).text.contains("左边正文") &&
              !select(spread,ReadingFocus.Mode.LEFT).text.contains("右边正文"),
              "manual left page on full spread");
        check(select(spread,ReadingFocus.Mode.RIGHT).text.contains("右边正文") &&
              !select(spread,ReadingFocus.Mode.RIGHT).text.contains("左边正文"),
              "manual right page on full spread");
        ReadingFocus.Tracker tracker=new ReadingFocus.Tracker();
        ReadingFocus.Selection held=tracker.choose(spread,720,1280,ReadingFocus.Mode.AUTO);
        ReadingFocus.Selection again=tracker.choose(spread,720,1280,ReadingFocus.Mode.AUTO);
        check(held.centerFraction == again.centerFraction,
              "focus must be stable across repeated captures");
        tracker.reset();
        check(!Float.isNaN(tracker.choose(spread,720,1280,ReadingFocus.Mode.AUTO).centerFraction),
              "explicit reset must allow fresh page selection");
        System.out.println("BOOK_READING_FOCUS_SMOKE_TEST=PASS");
    }
}
