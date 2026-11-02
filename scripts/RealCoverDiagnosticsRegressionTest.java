import com.vobichngan.kanzi.RealtimeSpeechGuard;

/**
 * The two real Jane Eyre cover diagnostics reported by the user.
 * Their OCR overlap was ~59%, just below the old global 60% threshold,
 * triggering TTS stop() and replay of the first slogan during an unfinished
 * reading. Preserve the literal recognized text including noisy boxes.
 */
public final class RealCoverDiagnosticsRegressionTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    private static String normalize(String text) {
        return text.toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[\\s\\p{Punct}，。！？、；：…（）【】《》“”‘’·]+", "");
    }

    public static void main(String[] args) {
        String first = normalize(
            "悦读段峰\n为读好书而生\n\n简·爱\n\n" +
            "[英国]夏洛蒂·勃朗特/著\n黄源深/译\n任明高赢诚文本解读\n" +
            "高罪诚\n田文君 活动任务\n\n劳新华\n\n" +
            "趣任务阅读\n具有影响力的专业出版社\n受广大师生喜爱的译者\n\n" +
            "JANE EYRE\n黄源深，英语文学学者，翻译家。\n" +
            "曾任上海题译家协会副会长，中国\n泽协理事、上海市文联理事，上海\n" +
            "市作家协会理");
        String second = normalize(
            "(4iThy)\n\n悦读良峰\n为读好书而生\n\n简·爱\n\n" +
            "[英国]夏洛蒂·勃朗特/著\n黄源深/译\n高赢诚文本解读\n" +
            "任卿 高直诚田文君活动任务\n\n劳新华\n\n任确\n\n" +
            "趣任务阅读\n具有影响力的专业出版社\n受广大师生喜爱的译者\n\n" +
            "JANE EYRE\ns 英文字学者，题课家\n请话上4协制公件\nL08A上R9R0");

        RealtimeSpeechGuard speech = new RealtimeSpeechGuard();
        check(speech.accept(first), "first cover recognition must start speech");
        check(!speech.accept(second),
            "same cover with damaged author bio, extra boxes and changed brand MUST NOT restart speech");
        check(!speech.accept(first),
            "next clean recognition should not restart the still-playing cover");
        check(!speech.accept(normalize("悦读良峰\n为读好书而生\n简·爱")),
            "a partial detection of the SAME cover should preserve remaining speech queue");

        String other = normalize(
            "第一章 我在红房子里待了很久，舅妈和其他人都离开了房间，" +
            "窗帘遮住了窗户，外面的雨正下个不停。这里是正文的新页面。");
        check(speech.accept(other), "genuinely different book page must be able to interrupt");
        check(!speech.accept(other), "stable new page should not repeat");
        check(speech.accept(first), "return to Jane Eyre cover after a different page is valid");
        System.out.println("REAL_COVER_DIAGNOSTICS_SPEECH=PASS");
    }
}
