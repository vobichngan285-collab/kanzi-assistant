package com.vobichngan.kanzi;

import java.util.List;

/**
 * Offline, geometry-only page rotation gate. A detection hypothesis is NOT an
 * OCR correction: a second real SDK OCR pass must improve the observed pixels.
 * Conservative by design: mixed-direction labels and true vertical scripts
 * default to 0 degrees.
 */
public final class AutoPageRotation {
    private AutoPageRotation() {}

    /** Degrees clockwise to make strongly sideways horizontal lines upright. */
    public static int candidate(List<ReadingOrder.Box> boxes) {
        if (boxes == null || boxes.size() < 4) return 0;
        int clockwise=0, counterclockwise=0, horizontal=0;
        for (ReadingOrder.Box b : boxes) {
            if (b == null || b.text == null || b.text.length() < 12 ||
                b.confidence < 0.84f || b.x.length != 4 || b.y.length != 4) continue;
            float dx=b.x[1]-b.x[0], dy=b.y[1]-b.y[0];
            float edge=(float)Math.hypot(dx,dy);
            float side=(float)Math.hypot(b.x[3]-b.x[0],b.y[3]-b.y[0]);
            if (edge < Math.max(65f,side*4f)) continue;
            if (Math.abs(dy)>Math.abs(dx)*3f) {
                if (dy<0) clockwise++; else counterclockwise++;
            } else if(Math.abs(dx)>Math.abs(dy)*3f) horizontal++;
        }
        int vertical=clockwise+counterclockwise;
        if(vertical<3 || vertical < Math.max(3,horizontal*2) ||
           Math.min(clockwise,counterclockwise)>0) return 0;
        return clockwise>0 ? 90 : 270;
    }

    /** 180 degrees is geometrically indistinguishable from 0.
     * Only trial on unusually low-quality, mostly horizontal printed text.
     */
    public static boolean considerUpsideDown(List<ReadingOrder.Box> boxes) {
        if(boxes==null || boxes.size()<6 || boxes.size()>100 || candidate(boxes)!=0) return false;
        int horizontal=0, poor=0;
        for(ReadingOrder.Box b:boxes) {
            if(b==null || b.text==null || b.x.length!=4 || b.y.length!=4)continue;
            float dx=b.x[1]-b.x[0],dy=b.y[1]-b.y[0];
            if(Math.abs(dx)>Math.abs(dy)*3f && Math.hypot(dx,dy)>70)horizontal++;
            if(b.confidence<.55f)poor++;
        }
        return horizontal>=5 && poor>=boxes.size()*0.65f;
    }

    /** Accept only substantially better, genuine OCR evidence, never text prediction. */
    public static boolean accept(List<ReadingOrder.Box> original,
                                 List<ReadingOrder.Box> corrected) {
        if(original==null || corrected==null) return false;
        int goodLines=0;
        for(ReadingOrder.Box b:corrected) {
            if(b==null || b.text==null || b.text.length()<10 || b.confidence<.83f)continue;
            float dx=b.x[1]-b.x[0],dy=b.y[1]-b.y[0];
            if(Math.abs(dx)>Math.abs(dy)*2f)goodLines++;
        }
        if(goodLines<3) return false;
        double base=score(original), trial=score(corrected);
        return trial>base*1.22 && trial-base>=35;
    }

    /** Count high-confidence printable source characters in coherent long rows. */
    private static double score(List<ReadingOrder.Box> boxes) {
        double total=0;
        for(ReadingOrder.Box b:boxes) {
            if(b==null || b.text==null || b.confidence<.60f) continue;
            int n=Math.min(140,b.text.trim().length());
            if(n<4)continue;
            float dx=b.x[1]-b.x[0],dy=b.y[1]-b.y[0];
            float edge=(float)Math.hypot(dx,dy);
            float side=(float)Math.hypot(b.x[3]-b.x[0],b.y[3]-b.y[0]);
            boolean coherent=edge>side*2f;
            if(coherent) total+= n*b.confidence;
        }
        return total;
    }
}
