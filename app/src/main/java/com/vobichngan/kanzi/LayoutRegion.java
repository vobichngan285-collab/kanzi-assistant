package com.vobichngan.kanzi;

import java.util.Collections;
import java.util.List;

/**
 * Optional ML layout hints. Only OCR owns the recognized text; layout labels
 * never generate, rewrite or discard any of its characters.
 */
public final class LayoutRegion {
    public final int category;
    public final float confidence;
    public final float left, top, right, bottom;

    public LayoutRegion(int category, float confidence, float left, float top,
                        float right, float bottom) {
        this.category = category;
        this.confidence = confidence;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * The detector's 23 upstream categories include:
     * 0 paragraph_title, 2 text, 5 content, 8 table, 11 doc_title,
     * 12 footnote, 22 aside_text. All classes can be grouping anchors.
     */
    public static int[] assign(List<ReadingOrder.Box> boxes,
                               List<LayoutRegion> regions) {
        int[] assigned = new int[boxes.size()];
        java.util.Arrays.fill(assigned, -1);
        if (regions == null || regions.size() < 2) return assigned;
        int reliable = 0;
        for (LayoutRegion region : regions)
            if (region.confidence >= .53f && region.right > region.left &&
                region.bottom > region.top) reliable++;
        if (reliable < 2) return assigned;

        for (int i=0;i<boxes.size();i++) {
            ReadingOrder.Box box = boxes.get(i);
            float best=-1f;
            for (int j=0;j<regions.size();j++) {
                LayoutRegion region=regions.get(j);
                if(region.confidence < .53f || region.right <= region.left ||
                   region.bottom <= region.top) continue;
                float overlapWidth=Math.min(box.right(),region.right)-
                    Math.max(box.left(),region.left);
                float overlapHeight=Math.min(box.bottom(),region.bottom)-
                    Math.max(box.top(),region.top);
                if(overlapWidth<=0 || overlapHeight<=0)continue;
                float ratio=overlapWidth*overlapHeight/
                    Math.max(1f,(box.right()-box.left())*(box.bottom()-box.top()));
                if(ratio < .60f)continue;
                // Prefer a smaller specific paragraph/footnote region over a
                // whole-page parent, while still respecting detector score.
                float area=(region.right-region.left)*(region.bottom-region.top);
                float score=ratio*1.2f+region.confidence+
                    Math.min(.3f,25000f/Math.max(1f,area)*.12f);
                if(score>best){best=score;assigned[i]=j;}
            }
        }
        int covered=0;
        java.util.HashSet<Integer> ids=new java.util.HashSet<>();
        for(int id:assigned)if(id>=0){covered++;ids.add(id);}
        // If AI only explained a minority of OCR boxes, don't let it destroy
        // readability: revert atomically to geometry-only grouping.
        if(ids.size()<2 || covered<Math.max(4,Math.ceil(boxes.size()*.42f))) {
            java.util.Arrays.fill(assigned,-1);
        }
        return assigned;
    }

    private LayoutRegion() {
        this(0,0,0,0,0,0);
    }
}
