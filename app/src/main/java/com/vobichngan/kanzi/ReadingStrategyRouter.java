package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An independent, inexpensive policy router over EXISTING OCR text/boxes:
 *
 *   coherent page -> previously verified traditional columns or old flow;
 *   one vertical door sign -> single-column character order;
 *   several independent signs -> prioritize the camera-center COMPLETE sign;
 *   ordinary horizontal book -> original full-text policy.
 *
 * All original OCR detections remain available for overlay and diagnosis.
 * This class never rotates pixels, calls an inference model, edits characters
 * or cancels TTS. Selection is only applied to LIVE QUICK SCAN, never BOOK.
 */
public final class ReadingStrategyRouter {
    private ReadingStrategyRouter(){}

    public static final class Result {
        public final String text,description;
        public final String strategy;
        public final int regions,selectedBoxes,totalBoxes;
        public final boolean centerSelected;
        Result(String s,String d,String type,int regions,int chosen,int total,boolean center) {
            text=s;description=d;strategy=type;
            this.regions=regions;selectedBoxes=chosen;totalBoxes=total;
            centerSelected=center;
        }
    }

    private static String keyChars(String s) {
        return s==null?"":s.replaceAll("[\\p{Z}\\p{Cc}\\p{Cf}]+","");
    }
    /** Multiset equality detects accidental deletion/duplication, not word meaning. */
    public static boolean preservesCharacters(List<ReadingOrder.Box> source,String output) {
        Map<Integer,Integer> expected=new HashMap<>(),actual=new HashMap<>();
        for(ReadingOrder.Box b:source) {
            if(b==null||b.text==null)return false;
            for(int ch:keyChars(b.text).codePoints().toArray())
                expected.merge(ch,1,Integer::sum);
        }
        for(int ch:keyChars(output).codePoints().toArray())
            actual.merge(ch,1,Integer::sum);
        return expected.equals(actual);
    }
    private static boolean strongHorizontalPage(List<ReadingOrder.Box> boxes) {
        int rows=0;
        for(ReadingOrder.Box b:boxes) {
            float w=b.right()-b.left(),h=b.bottom()-b.top();
            if(w>Math.max(260f,h*6f) &&
               b.text.codePointCount(0,b.text.length())>=10 &&
               b.confidence>=.72f)rows++;
        }
        return rows>=3;
    }

    public static Result arrange(List<ReadingOrder.Box> boxes,int width,int height,
                                 boolean automaticVertical,boolean quickScan,
                                 CenterTargetTracker focus) {
        if(boxes==null||boxes.isEmpty())
            return new Result("","无OCR文字","EMPTY",0,0,0,false);
        final String legacy=RealtimeTextOrientation.arrange(boxes,width,height);
        if(!quickScan)
            return new Result(legacy,"长文模式保持原排序","BOOK_FALLBACK",0,boxes.size(),boxes.size(),false);

        // Most body columns belong to ONE article. Its traditional right-to-
        // left order must win BEFORE focus can mistakenly choose just one.
        VerticalLongColumnLive.Result multi =
            VerticalLongColumnLive.tryArrange(automaticVertical,boxes,width,height);
        if(multi.used && preservesCharacters(boxes,multi.text))
            return new Result(multi.text,multi.status,"MULTI_VERTICAL",
                1,boxes.size(),boxes.size(),false);

        if(strongHorizontalPage(boxes))
            return new Result(legacy,"连续横排书页：使用原有完整文字流","HORIZONTAL_PAGE",
                1,boxes.size(),boxes.size(),false);

        List<TextRegionPartition.Region> regions=TextRegionPartition.partition(boxes);
        if(regions.size()<2 || focus==null)
            return new Result(legacy,"单区域或无法独立分区：原有文字流",
                "LEGACY_FLOW",regions.size(),boxes.size(),boxes.size(),false);

        TextRegionPartition.Region selected=focus.choose(regions,boxes,width,height);
        if(selected==null)
            return new Result(legacy,"无可信中央区域：原有文字流",
                "NO_TARGET",regions.size(),boxes.size(),boxes.size(),false);
        // A label must be sufficiently near the optical center. If camera is
        // pointed at empty space, never pretend an off-screen sign was aimed at.
        float centerDx=(selected.centerX-width*.5f)/width;
        float centerDy=(selected.centerY-height*.5f)/height;
        if(Math.hypot(centerDx,centerDy)>.48f)
            return new Result(legacy,"中央没有清晰文字目标，回退全部文字",
                "OFF_CENTER",regions.size(),boxes.size(),boxes.size(),false);

        List<ReadingOrder.Box> target=new ArrayList<>();
        for(int id:selected.ids)target.add(boxes.get(id));
        String oneColumn=SingleVerticalFlow.tryArrange(target);
        String candidate=oneColumn!=null?oneColumn:
            RealtimeTextOrientation.arrange(target,width,height);
        if(candidate.isBlank()||!preservesCharacters(target,candidate))
            return new Result(legacy,"区域排序未能保持原文字，回退全部OCR",
                "INTEGRITY_FALLBACK",regions.size(),boxes.size(),boxes.size(),false);
        String strategy=oneColumn!=null?"SINGLE_VERTICAL":"CENTER_REGION";
        String description="中央优先："+regions.size()+"个独立区域；选中"+
            target.size()+"/"+boxes.size()+"个源框；"+strategy+
            "；所有其他框仍保留在诊断/预览";
        return new Result(candidate,description,strategy,regions.size(),
            target.size(),boxes.size(),true);
    }
}
