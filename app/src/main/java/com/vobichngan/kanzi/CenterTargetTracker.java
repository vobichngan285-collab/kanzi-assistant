package com.vobichngan.kanzi;

import java.util.List;

/**
 * Stable, light-weight center-first target choice for separate signs/labels.
 * Does not do OCR, interpret medical meaning, read camera previews or TTS.
 *
 * A target may move slightly with hand shake without forcing speech to jump.
 * It is reselected when it leaves view or another region becomes clearly
 * better centered. No target is ever created from absent OCR evidence.
 */
public final class CenterTargetTracker {
    private String previousText="";
    private float previousX=.5f,previousY=.5f;

    public synchronized void reset(){previousText="";previousX=.5f;previousY=.5f;}

    private static double score(TextRegionPartition.Region r,int w,int h) {
        float x=w*.5f,y=h*.5f;
        float edgeX=x<r.left?(r.left-x)/w:x>r.right?(x-r.right)/w:0f;
        float edgeY=y<r.top?(r.top-y)/h:y>r.bottom?(y-r.bottom)/h:0f;
        float centerX=Math.abs(r.centerX-x)/w;
        float centerY=Math.abs(r.centerY-y)/h;
        // Region actually crossing the optical center wins over a distant
        // caption, even if the caption contains more recognized characters.
        return 2.3*Math.hypot(edgeX,edgeY)+
            .42*Math.hypot(centerX,centerY)+
            (r.confidence<.75f?.07:0);
    }
    private static String signature(TextRegionPartition.Region r,
                                    List<ReadingOrder.Box> boxes) {
        StringBuilder s=new StringBuilder();
        for(int id:r.ids)s.append(boxes.get(id).text);
        return s.toString();
    }
    private static boolean credible(TextRegionPartition.Region r) {
        return r.chars>=2 && r.confidence>=.62f;
    }
    public synchronized TextRegionPartition.Region choose(
        List<TextRegionPartition.Region> regions,List<ReadingOrder.Box> boxes,
        int width,int height) {
        if(regions==null||regions.isEmpty()||width<=0||height<=0)return null;
        TextRegionPartition.Region best=null,previous=null;
        double bestScore=Double.MAX_VALUE,previousScore=Double.MAX_VALUE;
        for(TextRegionPartition.Region r:regions) {
            if(!credible(r))continue;
            double sc=score(r,width,height);
            if(sc<bestScore){best=r;bestScore=sc;}
            if(!previousText.isEmpty() &&
               previousText.equals(signature(r,boxes)) &&
               Math.abs(r.centerX/width-previousX)<.24f &&
               Math.abs(r.centerY/height-previousY)<.24f) {
                previous=r;previousScore=sc;
            }
        }
        if(best==null)return null;
        // Keep the same target through small movements and exposure jitter.
        if(previous!=null && previousScore<=bestScore*1.24+.035)
            best=previous;
        String text=signature(best,boxes);
        previousText=text;
        previousX=best.centerX/width;
        previousY=best.centerY/height;
        return best;
    }
}
