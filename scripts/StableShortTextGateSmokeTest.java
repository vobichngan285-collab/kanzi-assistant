import com.vobichngan.kanzi.StableShortTextGate;
import java.util.Arrays;

public final class StableShortTextGateSmokeTest {
    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
    private static StableShortTextGate.Observation item(
            String text, float conf, float x, float y, long frame) {
        return new StableShortTextGate.Observation(text, conf, x, y, frame);
    }
    private static StableShortTextGate gate() {
        return new StableShortTextGate(new StableShortTextGate.Config(true));
    }
    private static StableShortTextGate.Decision run(
            StableShortTextGate g, StableShortTextGate.Observation item, long at) {
        return g.observe(item, at, true, false, false);
    }
    public static void main(String[] args) {
        StableShortTextGate.Observation one = item("房号  201", 0.93f, .5f, .5f, 1L);
        StableShortTextGate.Observation two = item("房号 201", 0.95f, .51f, .5f, 2L);
        StableShortTextGate disabled = new StableShortTextGate();
        check(run(disabled, one, 1000L).reason == StableShortTextGate.Reason.DISABLED,
                "default is off");
        StableShortTextGate p = gate();
        check(run(p, one, 1000L).action == StableShortTextGate.Action.WAIT_FOR_CONFIRMATION,
                "first frame waits");
        check(run(p, one, 1010L).action == StableShortTextGate.Action.WAIT_FOR_CONFIRMATION,
                "duplicate frame id does not confirm");
        check(run(p, item("房号 201", .95f, .51f, .5f, 0L), 1100L).confirmations == 1,
                "out-of-order frame cannot provide confirmation");
        check(run(p, two, 1210L).action == StableShortTextGate.Action.SPEAK,
                "two consistent observations after dwell trigger speak");
        check(run(p, item("房号 201", .91f, .52f, .5f, 3L), 1400L).action ==
                StableShortTextGate.Action.DUPLICATE,
                "same text never loops repeatedly while camera stays on label");
        p.noteSceneChanged();
        check(run(p, one, 2000L).action == StableShortTextGate.Action.WAIT_FOR_CONFIRMATION,
                "scene change resets previously spoken record");
        check(run(p, two, 2300L).action == StableShortTextGate.Action.SPEAK,
                "same text after actual scene reset can speak again");

        StableShortTextGate s = gate();
        check(run(s, one, 1000L).confirmations == 1, "first");
        check(run(s, item("房号 202", .96f, .5f,.5f, 2L), 1300L).confirmations == 1,
                "changed text resets confirmation");
        check(run(s, item("房号 202", .96f, .5f,.5f, 3L), 1410L).action ==
                StableShortTextGate.Action.WAIT_FOR_CONFIRMATION,
                "too short dwell keeps waiting");
        check(run(s, item("房号 202", .96f, .5f,.5f, 4L), 1530L).action ==
                StableShortTextGate.Action.SPEAK, "third frame allowed to reach dwell");
        StableShortTextGate drift = gate();
        run(drift, one, 1000L);
        check(run(drift, item("房号 201", .96f, .9f,.5f, 2L), 1300L).confirmations == 1,
                "new screen location resets observation");
        StableShortTextGate gap = gate();
        run(gap, one, 1000L);
        check(run(gap, item("房号 201", .96f, .5f,.5f, 2L), 4600L).confirmations == 1,
                "large time gap does not count as stable");

        StableShortTextGate bad = gate();
        check(run(bad, item("房号", Float.NaN,.5f,.5f, 1L), 1000L).reason ==
                StableShortTextGate.Reason.INVALID_OBSERVATION,
                "reject nonfinite confidence");
        check(run(bad, item("房号", .9f,1.5f,.5f, 1L), 1000L).reason ==
                StableShortTextGate.Reason.INVALID_OBSERVATION,
                "reject impossible box geometry");
        check(run(bad, item("房号", .5f,.5f,.5f, 1L), 1000L).reason ==
                StableShortTextGate.Reason.INVALID_OBSERVATION,
                "reject low confidence");
        check(run(bad, item(" ", .9f,.5f,.5f, 1L), 1000L).reason ==
                StableShortTextGate.Reason.INVALID_OBSERVATION,
                "reject empty text");
        check(run(bad, item("视力辅助\n".repeat(30), .95f,.5f,.5f, 1L), 1000L).reason ==
                StableShortTextGate.Reason.INVALID_OBSERVATION,
                "reject long document in short scan");
        check(bad.observe(one, 1200L,false,false,false).reason ==
                StableShortTextGate.Reason.FRAME_UNSTABLE,
                "camera motion rejects new candidate");
        check(bad.observe(one, 1200L,true,true,false).reason ==
                StableShortTextGate.Reason.LONG_READING,
                "long reading bypasses new gate entirely");
        check(bad.observe(one, 1200L,true,false,true).reason ==
                StableShortTextGate.Reason.SPEECH_BUSY,
                "no new speech while TTS speaks");
        StableShortTextGate mixed = gate();
        check(run(mixed, item("ABC  125mg",.9f,.5f,.5f,1L), 1000L).confirmations == 1,
                "mixed Chinese and English remains supported");
        check(run(mixed, item("abc 125mg",.9f,.5f,.5f,2L), 1300L).action ==
                StableShortTextGate.Action.SPEAK,
                "ASCII casing and internal spaces normalized for stability");
        System.out.println("STABLE_SHORT_TEXT_GATE=PASS");
    }
}
