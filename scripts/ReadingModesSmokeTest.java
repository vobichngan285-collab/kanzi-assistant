import com.vobichngan.kanzi.FrameChangeGate;
import com.vobichngan.kanzi.SpeechText;
import java.util.Arrays;
import java.util.List;

public class ReadingModesSmokeTest {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static byte[] frame(int width, int height, int luma) {
        byte[] nv21 = new byte[width * height * 3 / 2];
        Arrays.fill(nv21, 0, width * height, (byte) luma);
        return nv21;
    }
    public static void main(String[] args) {
        FrameChangeGate gate = new FrameChangeGate();
        byte[] initial = frame(64, 48, 90);
        check(gate.accept(initial, 64, 48, 1000L, true), "first page must be captured");
        check(!gate.accept(initial, 64, 48, 4000L, true), "long mode must skip identical page even after time");
        check(gate.accept(frame(64, 48, 200), 64, 48, 4100L, true), "new page must be accepted");
        gate.reset();
        check(gate.accept(initial, 64, 48, 5000L, false), "scan starts with one image");
        check(!gate.accept(initial, 64, 48, 5300L, false), "same scan frame should be skipped");
        check(gate.accept(initial, 64, 48, 8001L, false), "scan can recheck subtle changes");
        gate.reset();
        check(gate.accept(initial, 64, 48, 9000L, false), "switching modes resets image fingerprint");
        String full = "说明书第一行较长的文字。\n说明书第二行还有很多文字。\n说明书第三行需要长文阅读。";
        List<String> longChunks = SpeechText.chunks(full);
        check(longChunks.size() == 3, "long mode reads all paragraphs");
        check("说明书第一行较长的文字。|说明书第二行还有很多文字。|说明书第三行需要长文阅读。"
            .equals(String.join("|", longChunks)), "long mode preserves full text and order");
        check("商品名称\n生产日期\n其他文字".equals(SpeechText.scanExcerpt("商品名称\n生产日期\n其他文字")),
              "scan mode preserves all detected text");
        check(SpeechText.scanExcerpt(null).isEmpty(), "empty camera text is safe");
        System.out.println("DUAL_READING_MODES_SMOKE_TEST=PASS");
    }
}
