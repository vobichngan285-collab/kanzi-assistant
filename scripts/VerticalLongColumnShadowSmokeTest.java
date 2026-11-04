import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.VerticalLongColumnShadow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/** Anonymized geometry patterned on the user's five tall text columns.
 * No user photo, OCR transcript, tensor, or other private evidence is committed.
 */
public final class VerticalLongColumnShadowSmokeTest {
    private static int checks;
    private static void ok(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static ReadingOrder.Box box(String text, float x, float y, float w, float h, float conf) {
        return new ReadingOrder.Box(text, conf,
            new float[]{x, x + w, x + w, x},
            new float[]{y, y, y + h, y + h});
    }
    private static String longText(int i) {
        return "识别列" + i + "甲乙丙丁戊己庚辛壬癸甲乙";
    }
    private static List<ReadingOrder.Box> userLikeFiveColumns() {
        List<ReadingOrder.Box> boxes = new ArrayList<>();
        boxes.add(box("海报大字",630,209,223,194,.99f)); // #1 background title
        boxes.add(box(longText(2),335,302,58,589,.97f)); // #2 left
        boxes.add(box(longText(3),383,298,55,592,.98f)); // #3
        boxes.add(box(longText(4),431,296,54,595,.97f)); // #4
        boxes.add(box(longText(5),482,293,49,598,.98f)); // #5
        boxes.add(box("效",317,306,28,33,.99f)); // #6 short valid box
        boxes.add(box(longText(7),532,371,44,519,.98f)); // #7 right
        boxes.add(box("痧",779,365,136,142,.99f));
        boxes.add(box("中醫養生",683,409,94,163,.88f));
        boxes.add(box("ZHONG YI YANG SHENG",668,426,26,140,.96f));
        boxes.add(box("益康缘",686,652,195,111,.99f));
        boxes.add(box("专业学校毕业",651,778,167,44,.99f));
        boxes.add(box("中医盲人推拿",724,830,172,40,.99f));
        boxes.add(box("以り",888,1379,35,60,.60f));
        return boxes;
    }
    private static void verifyPreservation(List<ReadingOrder.Box> boxes,
            VerticalLongColumnShadow.Result r) {
        ok(r.exactPermutation, "every original OCR frame ID must survive");
        ok(r.allIds.size() == boxes.size(), "wrong count");
        ok(new HashSet<>(r.allIds).size() == boxes.size(), "duplicate OCR ID");
        String description=r.describe(boxes);
        for (int id=0;id<boxes.size();id++)
            ok(description.contains(boxes.get(id).text),
                "diagnostic dropped original string for ID " + (id+1));
    }
    public static void main(String[] args) {
        List<ReadingOrder.Box> sample=userLikeFiveColumns();
        VerticalLongColumnShadow.Result result=VerticalLongColumnShadow.inspect(sample);
        ok(result.proposed, "five real-style long columns should trigger shadow");
        ok(result.columnIds.equals(Arrays.asList(6,4,3,2,1)),
            "right-to-left columns must follow OCR source IDs 7/5/4/3/2: "+result.columnIds);
        ok(result.remainingIds.size()==9,"other valid OCR boxes retained");
        verifyPreservation(sample,result);
        String output=result.describe(sample);
        ok(output.contains("仅供人工核对") && output.contains("原始OCR字符"),
            "shadow status and immutability warning required");
        for (int i=0;i<result.columnIds.size()-1;i++) {
            String before=longText(result.columnIds.get(i)+1);
            String after=longText(result.columnIds.get(i+1)+1);
            ok(output.indexOf(before)<output.indexOf(after), "wrong printed column order");
        }
        ok(result.allIds.get(0)==6 && result.allIds.get(1)==4,"ID provenance preserved");
        ok(sample.get(6).text.equals(longText(7)),"source immutable");

        List<ReadingOrder.Box> horizontal=new ArrayList<>();
        for(int i=0;i<8;i++)
            horizontal.add(box("普通横排教材第"+i+"行完整文字",90,50+i*40,600,26,.99f));
        ok(!VerticalLongColumnShadow.inspect(horizontal).proposed,
            "normal horizontal text must not create a vertical proposal");

        List<ReadingOrder.Box> three=sample.subList(0,5);
        ok(!VerticalLongColumnShadow.inspect(Arrays.asList(
            three.get(0),three.get(1),three.get(2),three.get(3))).proposed,
            "three long columns are insufficient");

        List<ReadingOrder.Box> separated=Arrays.asList(
            box(longText(1),140,200,30,430,.98f),
            box(longText(2),180,200,30,430,.98f),
            box(longText(3),580,200,30,430,.98f),
            box(longText(4),620,200,30,430,.98f));
        ok(!VerticalLongColumnShadow.inspect(separated).proposed,
            "two distant text panels must not be spliced");

        List<ReadingOrder.Box> disconnected=Arrays.asList(
            box(longText(1),200,50,40,310,.98f),
            box(longText(2),250,80,40,310,.98f),
            box(longText(3),300,600,40,310,.98f),
            box(longText(4),350,620,40,310,.98f));
        ok(!VerticalLongColumnShadow.inspect(disconnected).proposed,
            "non-overlapping vertical passages must not be merged");

        List<ReadingOrder.Box> duplicates=Arrays.asList(
            box(longText(1),200,100,40,420,.98f),
            box(longText(2),202,100,40,420,.98f),
            box(longText(3),250,100,40,420,.98f),
            box(longText(4),300,100,40,420,.98f));
        ok(!VerticalLongColumnShadow.inspect(duplicates).proposed,
            "overlapping duplicate OCR boxes must not look like columns");

        List<ReadingOrder.Box> unsafe=new ArrayList<>(sample);
        unsafe.set(1,new ReadingOrder.Box("不可定位",.95f,
            new float[]{Float.NaN,350,350,330},
            new float[]{302,302,880,880}));
        VerticalLongColumnShadow.Result rejected=VerticalLongColumnShadow.inspect(unsafe);
        ok(!rejected.proposed && rejected.exactPermutation,
            "nonfinite geometry must fail closed while retaining box IDs");

        // Geometry could look the same for a 90-degree sideways horizontal page.
        // It is NOT a classifier: this output is diagnostic only; live ordering
        // must NOT call this proposal before true-direction GT validation.
        ok(!output.contains("自动采纳"),"no fake acceptance claim");
        System.out.println("VERTICAL_LONG_COLUMN_SHADOW_SMOKE=PASS checks="+checks);
    }
}
