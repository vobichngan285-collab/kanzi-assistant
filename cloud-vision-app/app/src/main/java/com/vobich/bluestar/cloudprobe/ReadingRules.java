package com.vobich.bluestar.cloudprobe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Book pages must preserve source text; headings are inferred only from explicit text. */
final class ReadingRules {
    static final String TASK_ID="reading-page";
    static final int MAX_PAGE_CHARS=20000;
    // Official text-only OCR prompt for qwen-vl-ocr in OpenAI-compatible API.
    // Using the model's explicit text-recognition task wording avoids
    // accidentally requesting advanced text bounding-box coordinates.
    static final String DEDICATED_TEXT_PROMPT=
        "Please output only the text content from the image without any additional descriptions or formatting.";
    static final String PROMPT=OcrModelCatalog.PROMPT+
        "请完整提取整张书页所有清晰可见的正文，保持原文、段落和自然阅读顺序。"+
        "只返回原文，不要解释、不要JSON、不要文字框坐标或定位数据。";
    private static final Pattern CHAPTER=Pattern.compile(
        "^\\s*(第[一二三四五六七八九十百千万零〇0-9]{1,12}[章节回][^\\r\\n]{0,40})\\s*$");
    // Screenshot RC22 returned row-after-row of x,y,width,height,angle values.
    // Do not feed those bounding boxes into TTS or store them as page text.
    private static final Pattern COORDINATE_ROW=Pattern.compile(
        "^\\s*[\\[\\(]?\\d{1,5}(?:\\s*[,，]\\s*\\d{1,5}){4}[\\]\\)]?\\s*$");
    static boolean coordinateLine(String text){
        return text!=null&&COORDINATE_ROW.matcher(text.trim()).matches();
    }
    static boolean coordinatesOnly(String text){
        if(text==null||text.trim().isEmpty())return false;
        String[] lines=text.trim().split("\\r?\\n");
        int coords=0,other=0;
        for(String line:lines){
            if(line.trim().isEmpty())continue;
            if(coordinateLine(line))coords++;
            else other++;
        }
        return coords>0&&(other==0||coords>=3&&coords>=other*2);
    }
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
