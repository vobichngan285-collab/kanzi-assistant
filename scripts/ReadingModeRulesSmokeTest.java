import com.vobichngan.kanzi.ReadingModeRules;

/** User-approved behavior: fast scan interrupts; long reading never interrupts. */
public final class ReadingModeRulesSmokeTest {
    private static void check(boolean v,String message) {
        if(!v) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(ReadingModeRules.shouldDeferOcrForSpeech(true,true),
            "LONG: do not run page 2 OCR while page 1 is speaking");
        check(!ReadingModeRules.shouldDeferOcrForSpeech(true,false),
            "LONG: page 2 becomes eligible immediately after page 1 TTS finishes");
        check(!ReadingModeRules.shouldDeferOcrForSpeech(false,true),
            "FAST: allow OCR for page 2 even when page 1 is speaking");
        check(ReadingModeRules.shouldInterruptSpeech(false,false),
            "FAST: newly detected text preempts page 1 speech");
        check(!ReadingModeRules.shouldInterruptSpeech(true,false),
            "LONG: normal page 2 never interrupts prior page");
        check(ReadingModeRules.shouldInterruptSpeech(true,true),
            "Explicit capture overrides long reading on user request");
        check(ReadingModeRules.isFastDuplicate("房号201","房号201",null),
            "FAST: same label is not reread");
        check(!ReadingModeRules.isFastDuplicate("房号202","房号201","房号201"),
            "FAST: different room number must interrupt, despite near-identical text");
        check(!ReadingModeRules.isFastDuplicate("这是第二页","这是第一页","这是第一页"),
            "FAST: new page with common text must interrupt");
        check(ReadingModeRules.isFastDuplicate("已识别","其他","已识别"),
            "FAST: currently speaking identical text must not restart");
        System.out.println("READING_MODE_RULES=PASS");
    }
}
