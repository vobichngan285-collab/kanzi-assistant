package com.vobich.bluestar.cloudprobe;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Deterministic local OCR comparison: no AI grader, no network.
 * NFKC + lowercase + remove Unicode whitespace and punctuation;
 * retain Chinese characters, numbers, English letters and symbols.
 * Accuracy = max(0,1 - Levenshtein(reference,recognition)/referenceLength).
 * This metric is not equivalent to semantic correctness.
 */
final class C110ResolutionScore {
    static final class Result {
        final int referenceChars,recognizedChars,edits;
        final double characterErrorRate,accuracyPercent;
        Result(int expected,int actual,int edits) {
            this.referenceChars=expected;
            this.recognizedChars=actual;
            this.edits=edits;
            this.characterErrorRate=expected==0?Double.NaN:(double)edits/expected;
            this.accuracyPercent=expected==0?Double.NaN:
                Math.max(0.0,100.0*(1.0-characterErrorRate));
        }
    }

    static String normalize(String value) {
        if(value==null)return "";
        String text=Normalizer.normalize(value,Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length();) {
            int cp=text.codePointAt(i);
            i+=Character.charCount(cp);
            int type=Character.getType(cp);
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)||
               type==Character.CONNECTOR_PUNCTUATION||
               type==Character.DASH_PUNCTUATION||
               type==Character.START_PUNCTUATION||
               type==Character.END_PUNCTUATION||
               type==Character.INITIAL_QUOTE_PUNCTUATION||
               type==Character.FINAL_QUOTE_PUNCTUATION||
               type==Character.OTHER_PUNCTUATION)continue;
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    static Result compare(String expected,String actual) {
        int[] a=normalize(expected).codePoints().toArray();
        int[] b=normalize(actual).codePoints().toArray();
        if(a.length==0)return new Result(0,b.length,0); // unknown, not 100%
        if(a.length>2000||b.length>8000)
            throw new IllegalArgumentException("OCR对照文本太长，限制标准2000字、识别8000字");
        if(a.length<b.length) {
            int[] t=a;a=b;b=t;
        }
        int[] prev=new int[b.length+1];
        int[] next=new int[b.length+1];
        for(int j=0;j<=b.length;j++)prev[j]=j;
        for(int i=1;i<=a.length;i++){
            next[0]=i;
            for(int j=1;j<=b.length;j++){
                int replace=prev[j-1]+(a[i-1]==b[j-1]?0:1);
                int insert=next[j-1]+1;
                int delete=prev[j]+1;
                next[j]=Math.min(replace,Math.min(insert,delete));
            }
            int[] t=prev;prev=next;next=t;
        }
        return new Result(normalize(expected).codePointCount(0,normalize(expected).length()),
            normalize(actual).codePointCount(0,normalize(actual).length()),prev[b.length]);
    }

    private C110ResolutionScore(){}
}
