package com.vobich.bluestar.cloudprobe;

public final class C110ResolutionScoreTest {
    private static void check(boolean condition,String why){
        if(!condition)throw new AssertionError(why);
    }
    private static void approximate(double actual,double expected){
        if(Math.abs(actual-expected)>0.001)
            throw new AssertionError("expected "+expected+", got "+actual);
    }
    public static void main(String[] args){
        C110ResolutionScore.Result a=C110ResolutionScore.compare(
            "你好，世界！","你好世界");
        check(a.referenceChars==4&&a.recognizedChars==4,"strip Unicode punctuation");
        check(a.edits==0,"exact normalized Chinese");
        approximate(a.accuracyPercent,100.0);

        C110ResolutionScore.Result b=C110ResolutionScore.compare(
            "中华人民共和国","中华人民共国");
        check(b.referenceChars==7&&b.edits==1,"missing Chinese character");
        approximate(b.accuracyPercent,100.0*(1.0-1.0/7.0));

        C110ResolutionScore.Result c=C110ResolutionScore.compare(
            "ＡＢＣ １２３","abc123");
        check(c.referenceChars==6&&c.edits==0,"NFKC digits and Latin");
        approximate(c.accuracyPercent,100.0);

        C110ResolutionScore.Result d=C110ResolutionScore.compare(
            "test","completely different");
        check(d.edits>=4&&d.accuracyPercent>=0&&d.accuracyPercent<=100,
            "accuracy clipped 0..100");

        C110ResolutionScore.Result noReference=C110ResolutionScore.compare("","OCR");
        check(Double.isNaN(noReference.accuracyPercent)&&
            noReference.referenceChars==0,"missing ground truth is never 100%");

        C110ResolutionScore.Result emoji=C110ResolutionScore.compare("🙂文本","🙂文");
        check(emoji.referenceChars==3&&emoji.edits==1,
            "count Unicode code points not UTF-16 surrogate halves");
        System.out.println("PASS C110+ OCR score: CER normalized, exact, insertion/deletion, empty-reference unknown");
    }
}
