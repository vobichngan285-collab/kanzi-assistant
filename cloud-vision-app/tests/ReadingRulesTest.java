package com.vobich.bluestar.cloudprobe;

public final class ReadingRulesTest {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args){
        check(ReadingRules.PROMPT.contains("整张书页"),"full-page prompt");
        check(ReadingRules.PROMPT.contains("不猜测"),"no guessing");
        check(ReadingRules.title("  我的书\n第二行  ").equals("我的书 第二行"),"title sanitized");
        check(ReadingRules.chapter("第十二章 开始\n你好").equals("第十二章 开始"),"chapter");
        check(ReadingRules.chapter("序言\n第一章 出发").equals("第一章 出发"),"near title");
        check(ReadingRules.chapter("普通正文\n再一行").isEmpty(),"no invented chapter");
        check(ReadingRules.coordinateLine("244,346,21,59,82"),"coordinate screenshot");
        check(ReadingRules.coordinateLine(" [ 320, 377,14,157,83 ] "),"bracket coordinates");
        check(!ReadingRules.coordinateLine("第一章 认识世界"),"real text not coordinates");
        check(!ReadingRules.coordinateLine("价格 244,346"),"numbers in text preserved");
        check(ReadingRules.coordinatesOnly("244,346,21,59,82\\n320,377,14,157,83\\n282,402,10,59,83".replace("\\\\n","\\n")),"coordinate page blocked");
        check(!ReadingRules.coordinatesOnly("第一章 认识世界\\n234,346,21,59,82".replace("\\\\n","\\n")),"mixed valid text not blocked");
        check(ReadingRules.DEDICATED_TEXT_PROMPT.contains("only the text content"),"official qwen text mode");
        check(ReadingRules.truncated("length")&&!ReadingRules.truncated("stop"),"truncation");
        check(!ReadingRules.usable("  ")&&ReadingRules.usable("读书"),"valid content");
        System.out.println("RC22_READING_BOOK_RULES=PASS");
    }
}
