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
            "左一\n左二\n右一\n右二", "two-column page");
        equals(ReadingOrder.arrange(Arrays.asList(box("5元 50% ￥", 10, 10, 200, 25))),
            "5元 50% ￥", "preserve numeric punctuation");
        equals(ReadingOrder.arrange(Arrays.asList(box("清晰文字", 10, 10, 130, 25))),
            "清晰文字", "preserve low-confidence but readable content");
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
        equals(String.join("|", SpeechText.chunks("标题\n第一段内容\n第二段内容")), "标题|第一段内容|第二段内容", "paragraph pauses");
        System.out.println("READING_FLOW_SMOKE_TEST=PASS");
    }
}
