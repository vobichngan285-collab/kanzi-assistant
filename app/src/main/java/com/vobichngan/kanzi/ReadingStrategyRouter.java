package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v52: a minimal, OFFLINE, geometry-only reading order policy.
 * Every valid OCR source box must be spoken exactly once. No center selection,
 * text replacement, new inference, scene classification or extra model call.
 *
 * - Ordinary / mixed / price-list content: complete top-first reading flow.
 * - One or several separated vertical door signs: top-down in each sign,
 *   signs left-to-right.
 * - Confident traditional long vertical article: columns right-to-left.
 * - Unclear layout: complete legacy flow; never choose a partial region.
 *
 * BOOK still uses its original implementation outside this router.
 */
public final class ReadingStrategyRouter {
    private ReadingStrategyRouter() {}

    public static final class Result {
        public final String text, description, strategy;
        public final int regions, selectedBoxes, totalBoxes;
        /** Kept for diagnostic compatibility; v52 never selects the center. */
        public final boolean centerSelected = false;
        Result(String text, String description, String strategy, int regions, int total) {
            this.text=text; this.description=description; this.strategy=strategy;
            this.regions=regions; this.selectedBoxes=total; this.totalBoxes=total;
        }
    }

    private static String withoutWhitespace(String s) {
        return s==null?"":s.replaceAll("[\\p{Z}\\p{Cc}\\p{Cf}]+","");
    }

    /** Character multiset equality: no source character may vanish or appear twice. */
    public static boolean preservesCharacters(List<ReadingOrder.Box> source,String output) {
        if(source==null || output==null)return false;
        Map<Integer,Integer> expected=new HashMap<>(), actual=new HashMap<>();
        for(ReadingOrder.Box b:source) {
            if(b==null||b.text==null)return false;
            withoutWhitespace(b.text).codePoints().forEach(c -> expected.merge(c,1,Integer::sum));
        }
        withoutWhitespace(output).codePoints().forEach(c -> actual.merge(c,1,Integer::sum));
        return expected.equals(actual);
    }

    private static String originalBoxes(List<ReadingOrder.Box> boxes) {
        StringBuilder result=new StringBuilder();
        for(ReadingOrder.Box b:boxes) {
            if(b==null||b.text==null||b.text.isBlank())continue;
            if(result.length()>0)result.append('\n');
            result.append(b.text);
        }
        return result.toString();
    }

    private static String completeFlow(List<ReadingOrder.Box> boxes,int width,int height) {
        String legacy=RealtimeTextOrientation.arrange(boxes,width,height);
        return preservesCharacters(boxes,legacy)?legacy:originalBoxes(boxes);
    }

    /** This is deliberately NOT a general-purpose layout classifier. */
    private static String separateVerticalSigns(List<ReadingOrder.Box> boxes) {
        // Complicated price lists are never cropped to sign-like regions.
        if(boxes.size()>24)return null;
        List<TextRegionPartition.Region> parts=TextRegionPartition.partition(boxes);
        if(parts.isEmpty() || parts.size()>6)return null;
        StringBuilder speech=new StringBuilder();
        TextRegionPartition.Region previous=null;
        for(TextRegionPartition.Region region:parts) {
            List<ReadingOrder.Box> source=new ArrayList<>();
            for(int id:region.ids)source.add(boxes.get(id));
            String column=SingleVerticalFlow.tryArrange(source);
            if(column==null || !preservesCharacters(source,column))return null;
            // Independent doorplates have clear space between their X bounds.
            // Closely packed price-list columns must NOT be reinterpreted as signs.
            if(previous!=null) {
                float gap=region.left-previous.right;
                float widths=Math.max(region.right-region.left,
                                      previous.right-previous.left);
                if(gap<Math.max(20f,widths*.60f))return null;
            }
            if(speech.length()>0)speech.append('\n');
            speech.append(column);
            previous=region;
        }
        if(!preservesCharacters(boxes,speech.toString()))return null;
        return speech.toString();
    }

    public static Result arrange(List<ReadingOrder.Box> boxes,int width,int height,
                                 boolean automaticVertical, boolean quickScan) {
        if(boxes==null||boxes.isEmpty())
            return new Result("","没有OCR文字","EMPTY",0,0);
        final String full=completeFlow(boxes,width,height);
        if(!quickScan)
            return new Result(full,"完整文字流（长文模式不截断）","BOOK_FALLBACK",0,boxes.size());

        VerticalLongColumnLive.Result longVertical=
            VerticalLongColumnLive.tryArrange(automaticVertical,boxes,width,height);
        if(longVertical.used && preservesCharacters(boxes,longVertical.text))
            return new Result(longVertical.text,
                "传统竖排：从右往左逐列读，完整保留原框",
                "MULTI_VERTICAL",1,boxes.size());

        String signs=separateVerticalSigns(boxes);
        if(signs!=null && !signs.isBlank())
            return new Result(signs,"独立竖排标牌：从左到右，每列从上到下（全文）",
                "VERTICAL_SIGNS_FULL",TextRegionPartition.partition(boxes).size(),boxes.size());

        return new Result(full,"普通横排或混排：从上往下完整朗读，不选中央、不丢框",
            "FULL_READING",0,boxes.size());
    }
}
