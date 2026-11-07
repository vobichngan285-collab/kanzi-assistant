package com.vobich.bluestar.cloudprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Book pages must preserve source text; headings are inferred only from explicit text. */
final class ReadingRules {
    static final String TASK_ID="reading-page";
    static final int MAX_PAGE_CHARS=20000;
    static final String PROMPT="请识别照片里整张书页上所有清晰可辨的文字，按书页真实阅读顺序完整输出。"
        +"保留原文字词、标点和自然分行，不概括、不解释、不润色、不补充、不猜测模糊内容。"
        +"不添加无关说明或Markdown。每行文字后换行，方便按行朗读。";
    private static final Pattern CHAPTER=Pattern.compile(
        "^\\s*(第[一二三四五六七八九十百千万零〇0-9]{1,12}[章节回][^\\r\\n]{0,40})\\s*$");
    private ReadingRules(){}
    static String title(String value){
        if(value==null)return "";
        String s=value.trim().replaceAll("[\\r\\n\\t]+"," ");
        return s.length()>60?s.substring(0,60):s;
    }
    static String chapter(String original){
        if(original==null)return "";
        String[] lines=original.split("\\r?\\n",7);
        for(int i=0;i<Math.min(5,lines.length);i++){
            Matcher matcher=CHAPTER.matcher(lines[i].trim());
            if(matcher.matches())return matcher.group(1).trim();
        }
        return "";
    }
    static boolean truncated(String finishReason){return "length".equals(finishReason);}
    static boolean usable(String text){return text!=null&&!text.trim().isEmpty()
        &&text.length()<=MAX_PAGE_CHARS;}
}
