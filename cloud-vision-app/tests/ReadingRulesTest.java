package com.vobich.bluestar.cloudprobe;

public final class ReadingRulesTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(ReadingRules.PROMPT.contains("整张书页"),"full-page prompt");
        check(ReadingRules.PROMPT.contains("不猜测"),"no guessing");
        check(ReadingRules.title("  我的书\\n").equals("我的书\\n".trim().replaceAll("[\\r\\n\\t]+"," "))==false?"unused".equals("never"):true,"no op");
        check(ReadingRules.title("  我的书\n第二行  ").equals("我的书 第二行"),"title sanitized");
        check(ReadingRules.chapter("第十二章 开始\n你好").equals("第十二章 开始"),"chapter");
        check(ReadingRules.chapter("序言\n第一章 出发").equals("第一章 出发"),"near title");
        check(ReadingRules.chapter("普通正文\n再一行").isEmpty(),"no invented chapter");
        check(ReadingRules.truncated("length")&&!ReadingRules.truncated("stop"),"truncation");
        check(!ReadingRules.usable("  ")&&ReadingRules.usable("读书"),"valid content");
        System.out.println("RC22_READING_BOOK_RULES=PASS");
    }
}
