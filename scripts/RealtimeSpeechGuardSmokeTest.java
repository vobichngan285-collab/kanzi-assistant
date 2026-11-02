import com.vobichngan.kanzi.RealtimeSpeechGuard;

/**
 * Regression: after reading the Jane Eyre cover's top slogan the live
 * camera must NOT clear the queued title, author and translator to read
 * the slogan again. Luma scene generations are deliberately not inputs.
 */
public final class RealtimeSpeechGuardSmokeTest {
    private static void check(boolean result,String message) {
        if (!result) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        RealtimeSpeechGuard guard = new RealtimeSpeechGuard();
        String cover = "悦读段峰为读好书而生简爱英国夏洛蒂勃朗特著" +
            "黄源深译任明高赢诚文本解读趣任务阅读" +
            "具有影响力的专业出版社受广大师生喜爱的译者" +
            "JANEEYRE黄源深英语文学学者翻译家" +
            "曾任上海翻译家协会副会长中国译协理事上海市文联理事";
        String slightOCRChange = cover.replace("悦读段峰", "悦读巅峰")
            .replace("译协理事", "译家理事");
        String samePartial = "悦读段峰为读好书而生简爱英国夏洛蒂勃朗特著";
        String anotherPage = "第二页在桑菲尔德庄园我认识了罗切斯特先生" +
            "这一章的内容与书籍封面的出版社宣传语完全不同";
        check(guard.accept(cover), "first recognized cover must read immediately");
        // One OCR/scene cycle finishes while the first TTS chunk is playing.
        check(!guard.accept(cover), "new scene generation must NOT replay same cover");
        check(!guard.accept(slightOCRChange), "minor OCR errors must not reset speech");
        check(!guard.accept(samePartial), "partial cover recognition must not restart first line");
        // After the last TTS chunk, remaining still must also remain silent.
        check(!guard.accept(cover), "finished unchanged cover must remain quiet");
        check(guard.accept(anotherPage), "genuinely new page must interrupt old text");
        check(!guard.accept(anotherPage), "second page must not repeat");
        check(guard.accept(cover), "returning to cover after another recognized page must re-read");
        check(!guard.accept(""), "empty recognition cannot interrupt speech");
        guard.reset();
        check(guard.accept(cover), "explicit restart should allow re-reading current cover");
        System.out.println("REALTIME_SPEECH_GUARD=PASS");
    }
}
