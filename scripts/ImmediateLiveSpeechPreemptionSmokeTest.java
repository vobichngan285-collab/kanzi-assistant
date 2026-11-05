import com.vobichngan.kanzi.RealtimeSpeechGuard;

/**
 * v56 no second OCR pass: ONE genuinely changed recognized text is enough.
 * This pure-Java test does not synthesize audio or pretend to verify hardware.
 */
public final class ImmediateLiveSpeechPreemptionSmokeTest {
    private static int checks;
    private static void require(boolean ok,String msg) {
        checks++;if(!ok)throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        RealtimeSpeechGuard g=new RealtimeSpeechGuard();
        String first="编写说明随着教学课程的发展各单元附有详细的阅读设计";
        String minor=first.replace("教学课程","教学课桯");
        String partial="编写说明随着教学课程的发展";
        String next="扉页目录第一章春天的故事第二章美丽的故乡";
        require(!g.accept(""),"empty cannot speak or interrupt");
        require(g.accept(first),"first real OCR result speaks immediately");
        require(!g.accept(first),"unchanged frame must not restart");
        require(!g.accept(minor),"single glyph uncertainty must not restart");
        require(!g.accept(partial),"partial text of same page must not restart");
        // Simulate an audio chunk currently playing: the guard accepts a
        // different text immediately without needing repeated confirmation.
        require(g.accept(next),"one new OCR result must be enough to preempt");
        require(!g.accept(next),"new target must not immediately repeat");
        require(g.accept(first),"return to the first page is truly a new target");
        g.reset();
        require(g.accept(next),"manual reset permits a new initial target");
        System.out.println("IMMEDIATE_LIVE_SPEECH_PREEMPT=PASS checks="+checks);
    }
}
