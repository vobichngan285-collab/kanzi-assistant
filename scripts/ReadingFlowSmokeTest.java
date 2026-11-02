import com.vobichngan.kanzi.ReadingOrder;
import com.vobichngan.kanzi.SpeechText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReadingFlowSmokeTest {
    private static ReadingOrder.Box box(String text, float left, float top, float width, float height) {
        return new ReadingOrder.Box(text, 0.4f,
            new float[]{left, left + width, left + width, left},
            new float[]{top, top, top + height, top + height});
    }
    private static void equals(String actual, String expected, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + " expected: " + expected + " actual: " + actual);
    }
    public static void main(String[] args) {
        // Two nearby but separate lines must not be merged even with skewed AABBs.
        List<ReadingOrder.Box> skewed = Arrays.asList(
            new ReadingOrder.Box("第二行文字", .65f, new float[]{100, 500, 500, 100}, new float[]{135, 103, 143, 175}),
            new ReadingOrder.Box("第一行文字", .65f, new float[]{100, 500, 500, 100}, new float[]{90, 58, 98, 130})
        );
        equals(ReadingOrder.arrange(skewed), "第一行文字\n第二行文字", "deskewed rows");
        equals(ReadingOrder.arrange(Arrays.asList(box("保存", 300, 100, 50, 25), box("食品名称", 10, 100, 100, 25))),
            "食品名称保存", "single line left to right");
        equals(ReadingOrder.arrange(Arrays.asList(
            box("右二", 450, 190, 100, 25), box("左一", 10, 100, 100, 25),
            box("右一", 450, 100, 100, 25), box("左二", 10, 190, 100, 25))),
            "左一\n左二\n\n右一\n右二", "two-column page");
        equals(ReadingOrder.arrange(Arrays.asList(box("5元 50% ￥", 10, 10, 200, 25))),
            "5元 50% ￥", "preserve numeric punctuation");
        equals(ReadingOrder.arrange(Arrays.asList(box("清晰文字", 10, 10, 130, 25))),
            "清晰文字", "preserve low-confidence but readable content");
        equals(ReadingOrder.arrange(Arrays.asList(
            box("商", 180, 10, 23, 24), box("品", 180, 39, 23, 24),
            box("名", 180, 68, 23, 24), box("称", 180, 97, 23, 24),
            box("生", 110, 10, 23, 24), box("产", 110, 39, 23, 24),
            box("日", 110, 68, 23, 24), box("期", 110, 97, 23, 24)
        )), "商品名称\n\n生产日期", "traditional vertical columns right-to-left");
        equals(ReadingOrder.arrange(Arrays.asList(
            box("保", 85, 10, 23, 24), box("质", 85, 39, 23, 24),
            box("期", 85, 68, 23, 24)
        )), "保质期", "packaging short upright vertical column");
        StringBuilder page = new StringBuilder();
        for (int i = 0; i < 50; i++) page.append("完整阅读不能中途截断。");
        List<String> chunks = SpeechText.chunks(page.toString());
        if (chunks.size() < 2) throw new AssertionError("long text should split into TTS chunks");
        StringBuilder reconstructed = new StringBuilder();
        for (String s : chunks) {
            if (s.length() > 180) throw new AssertionError("TTS chunk too long");
            reconstructed.append(s);
        }
        equals(reconstructed.toString(), page.toString(), "no missing chars across speech chunks");
        equals(String.join("|", SpeechText.chunks("标题\n第一段内容\n第二段内容")),
            "标题第一段内容第二段内容", "soft OCR line wrapping without choppy TTS");
        equals(String.join("|", SpeechText.chunks("第一片段\n第二行\n\n无关区域")),
            "第一片段第二行|无关区域", "hard boundaries across independent text flows");
        // Regression from a real 720x1280 book-page diagnostic: 49 OCR
        // boxes were formerly spliced into one row because the inner gutter
        // was too small for the previous global-x-gap-only splitter.
        List<ReadingOrder.Box> actualPageGeometry = new ArrayList<>();
        int[] leftTop = {539,578,616,636,657,679,701,722,769,792,841,866,892,918,945};
        int[] leftRight = {186,174,172,170,168,163,157,156,150,147,140,135,131,129,123};
        for (int i = 0; i < leftTop.length; i++)
            actualPageGeometry.add(box("LEFT" + i, 0, leftTop[i],
                leftRight[i], 24));
        int[] mainTop = {691,711,734,757,779,803,827,851,879,904,929,956,982,1009};
        int[] mainLeft = {223,204,201,199,195,194,191,187,188,200,184,179,176,172};
        for (int i = 0; i < mainTop.length; i++)
            actualPageGeometry.add(box("MAIN" + i, mainLeft[i], mainTop[i],
                548 - mainLeft[i], 25));
        int[] marginTop = {459,470,482,491,505,631,644,656,668};
        for (int i = 0; i < marginTop.length; i++)
            actualPageGeometry.add(box("SIDE" + i, 545, marginTop[i], 77, 14));
        // A valid isolated small OCR box must not alter column grouping.
        actualPageGeometry.add(box("书名", 13, 434, 64, 23));
        actualPageGeometry.add(box("1", 240, 500, 10, 9));
        String reorderedBook = ReadingOrder.arrange(actualPageGeometry);
        if (reorderedBook.isEmpty()) throw new AssertionError("lost diagnostic page");
        int lastOffset = -1;
        for (int i = 0; i < leftTop.length; i++) {
            int offset = reorderedBook.indexOf("LEFT" + i + "\n");
            if (offset < 0 && i == leftTop.length - 1)
                offset = reorderedBook.indexOf("LEFT" + i);
            if (offset <= lastOffset) throw new AssertionError(
                "left page not consecutive before other streams: " + reorderedBook);
            lastOffset = offset;
        }
        int leftEnd = reorderedBook.indexOf("LEFT14");
        int mainStart = reorderedBook.indexOf("MAIN0");
        int marginStart = reorderedBook.indexOf("SIDE0");
        if (!(leftEnd >= 0 && mainStart > leftEnd && marginStart > mainStart))
            throw new AssertionError("book columns interleaved: " + reorderedBook);
        int seen = 0;
        for (int i = 0; i < leftTop.length; i++)
            if (reorderedBook.contains("LEFT" + i)) seen++;
        for (int i = 0; i < mainTop.length; i++)
            if (reorderedBook.contains("MAIN" + i)) seen++;
        for (int i = 0; i < marginTop.length; i++)
            if (reorderedBook.contains("SIDE" + i)) seen++;
        if (seen != leftTop.length + mainTop.length + marginTop.length)
            throw new AssertionError("missing a detected book line");

        // Nearly touching left/right lines must STILL be read one full page at a time.
        List<ReadingOrder.Box> tightColumns = new ArrayList<>();
        tightColumns.add(box("跨栏标题", 0, 0, 550, 25));
        for (int row = 0; row < 5; row++) {
            tightColumns.add(box("左页" + row, 0, 80 + row * 29, 163, 23));
            tightColumns.add(box("右页" + row, 187, 80 + row * 29, 348, 23));
        }
        String split = ReadingOrder.arrange(tightColumns);
        if (!split.startsWith("跨栏标题\n\n左页0\n") ||
            split.indexOf("左页4") > split.indexOf("右页0"))
            throw new AssertionError("narrow gutter + page header broken: " + split);

        // One continuous single-column paragraph must NOT be split by its
        // changing line lengths, even when there are multiple OCR boxes.
        List<ReadingOrder.Box> single = new ArrayList<>();
        for (int row = 0; row < 14; row++)
            single.add(box("连续段落" + row, 12 + (row % 3) * 9,
                50 + row * 30, 220 + (row % 4) * 16, 24));
        String singleText = ReadingOrder.arrange(single);
        if (singleText.indexOf("连续段落0") > singleText.indexOf("连续段落1") ||
            singleText.indexOf("连续段落9") > singleText.indexOf("连续段落10"))
            throw new AssertionError("single column fragmented: " + singleText);
        System.out.println("READING_FLOW_SMOKE_TEST=PASS");
    }
}
