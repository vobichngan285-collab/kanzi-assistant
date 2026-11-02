import com.vobichngan.kanzi.SelectiveOcrRescuePolicy;
import java.util.Arrays;
import java.util.List;

/** Standalone JDK smoke tests; no Android SDK, OCR model, APK or real camera. */
public final class SelectiveOcrRescuePolicySmokeTest {
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
    private static SelectiveOcrRescuePolicy.Line rect(
            String text, float confidence, float width, float height) {
        return new SelectiveOcrRescuePolicy.Line(text, confidence,
                new float[]{10,10,10+width,10,10+width,10+height,10,10+height});
    }
    private static SelectiveOcrRescuePolicy.Line skewed(
            String text, float confidence) {
        return new SelectiveOcrRescuePolicy.Line(text, confidence,
                new float[]{10,10,50,28,50,52,10,34});
    }
    private static SelectiveOcrRescuePolicy.Line perspective(
            String text, float confidence) {
        return new SelectiveOcrRescuePolicy.Line(text, confidence,
                new float[]{10,10,66,10,44,34,10,34});
    }
    private static SelectiveOcrRescuePolicy.Decision eval(
            SelectiveOcrRescuePolicy p, List<SelectiveOcrRescuePolicy.Line> lines,
            long now) {
        return p.evaluate(lines, now, now+100, false, true);
    }
    public static void main(String[] args) {
        SelectiveOcrRescuePolicy.Line narrow = rect("纯生奶", 0.52f, 32, 24);
        SelectiveOcrRescuePolicy.Line regular = rect("纯牛奶", 0.52f, 75, 24);
        SelectiveOcrRescuePolicy.Line longLine =
                rect("中华人民共和国食品安全国家标准", 0.52f, 360, 24);
        SelectiveOcrRescuePolicy defaultOff = new SelectiveOcrRescuePolicy();
        check(eval(defaultOff, Arrays.asList(narrow), 1000).reason ==
                SelectiveOcrRescuePolicy.Reason.DISABLED, "OFF by default");

        SelectiveOcrRescuePolicy p =
                new SelectiveOcrRescuePolicy(new SelectiveOcrRescuePolicy.Config(true));
        check(eval(p, Arrays.asList(narrow), 1000).action ==
                SelectiveOcrRescuePolicy.Action.CANDIDATE_STRETCH,
                "narrow low-confidence line can trigger");
        check(eval(p, Arrays.asList(narrow), 1005).reason ==
                SelectiveOcrRescuePolicy.Reason.COOLDOWN,
                "repeated frame stays in cooldown");
        check(eval(p, Arrays.asList(narrow), 4000).action ==
                SelectiveOcrRescuePolicy.Action.CANDIDATE_STRETCH,
                "cooldown eventually expires");
        p.recordExtraWork(4010, 80, 20);
        check(eval(p, Arrays.asList(narrow), 4500).reason ==
                SelectiveOcrRescuePolicy.Reason.COOLDOWN,
                "overrun blocks more rescue work");
        check(eval(p, Arrays.asList(narrow), 12011).action ==
                SelectiveOcrRescuePolicy.Action.CANDIDATE_STRETCH,
                "overrun cooldown expires");

        check(eval(new SelectiveOcrRescuePolicy(
                new SelectiveOcrRescuePolicy.Config(true)), Arrays.asList(regular), 1000)
                .action == SelectiveOcrRescuePolicy.Action.NONE,
                "ordinary short line must not stretch");
        check(eval(new SelectiveOcrRescuePolicy(
                new SelectiveOcrRescuePolicy.Config(true)), Arrays.asList(longLine), 1000)
                .action == SelectiveOcrRescuePolicy.Action.NONE,
                "normal long line wide aspect must not trigger");
        check(eval(new SelectiveOcrRescuePolicy(
                new SelectiveOcrRescuePolicy.Config(true)),
                Arrays.asList(rect("纯生奶", 0.95f, 32, 24)), 1000)
                .action == SelectiveOcrRescuePolicy.Action.NONE,
                "high confidence does not trigger blindly");
        check(eval(new SelectiveOcrRescuePolicy(
                new SelectiveOcrRescuePolicy.Config(true)),
                Arrays.asList(skewed("纯生奶", 0.52f)), 1000)
                .action == SelectiveOcrRescuePolicy.Action.NONE,
                "tilted line must not be stretched");
        check(eval(new SelectiveOcrRescuePolicy(
                new SelectiveOcrRescuePolicy.Config(true)),
                Arrays.asList(perspective("纯生奶", 0.52f)), 1000)
                .action == SelectiveOcrRescuePolicy.Action.NONE,
                "perspective trapezoid must not be stretched");

        SelectiveOcrRescuePolicy budget =
                new SelectiveOcrRescuePolicy(new SelectiveOcrRescuePolicy.Config(true));
        check(budget.evaluate(Arrays.asList(narrow), 1000, 1030, false, true)
                .reason == SelectiveOcrRescuePolicy.Reason.NO_TIME_BUDGET,
                "skip when deadline too close");
        check(budget.evaluate(Arrays.asList(narrow), 1000, 1200, true, true)
                .reason == SelectiveOcrRescuePolicy.Reason.FAST_SCAN,
                "fast scan must not trigger");
        check(budget.evaluate(Arrays.asList(narrow), 1000, 1200, false, false)
                .reason == SelectiveOcrRescuePolicy.Reason.UNSTABLE_FRAME,
                "moving camera must not trigger");

        SelectiveOcrRescuePolicy segmentation =
                new SelectiveOcrRescuePolicy(new SelectiveOcrRescuePolicy.Config(true));
        List<SelectiveOcrRescuePolicy.Line> layout = Arrays.asList(
                rect("甲乙",0.9f,40,20), rect("丙丁",0.9f,40,20),
                rect("生产日期和营养价值",0.52f,150,55),
                rect("戊己",0.9f,40,20), rect("庚辛",0.9f,40,20));
        SelectiveOcrRescuePolicy.Decision d = eval(segmentation, layout, 1000);
        check(d.action == SelectiveOcrRescuePolicy.Action.REVIEW_SEGMENTATION &&
                d.lineIndex == 2, "suspected merged rows have priority");
        SelectiveOcrRescuePolicy multi =
                new SelectiveOcrRescuePolicy(new SelectiveOcrRescuePolicy.Config(true));
        check(eval(multi, Arrays.asList(narrow, narrow, narrow), 1000)
                .action == SelectiveOcrRescuePolicy.Action.CANDIDATE_STRETCH,
                "at most one candidate per decision");
        check(multi.evaluate(null, 4000, 4100, false, true).reason ==
                SelectiveOcrRescuePolicy.Reason.NO_VALID_CANDIDATE,
                "empty OCR is safe");
        System.out.println("SELECTIVE_OCR_RESCUE_POLICY=PASS");
    }
}
