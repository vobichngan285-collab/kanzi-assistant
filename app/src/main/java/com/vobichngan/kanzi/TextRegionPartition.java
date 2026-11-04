package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure geometry-only connected regions for a single OCR result.
 *
 * Do NOT run a layout model, rerun OCR, alter recognized text, or assume
 * that the whole camera image has one writing direction. Only the selected
 * region may be sent to live TTS; all OCR boxes remain in the diagnosis.
 */
public final class TextRegionPartition {
    private TextRegionPartition(){}

    public static final class Region {
        public final List<Integer> ids;
        public final float left,top,right,bottom,centerX,centerY;
        public final float confidence;
        public final int chars;
        Region(List<Integer> sourceIds,List<ReadingOrder.Box> boxes) {
            ids=Collections.unmodifiableList(new ArrayList<>(sourceIds));
            float l=Float.MAX_VALUE,t=Float.MAX_VALUE,r=-Float.MAX_VALUE,b=-Float.MAX_VALUE;
            double sum=0;
            int n=0;
            for(int id:ids) {
                ReadingOrder.Box v=boxes.get(id);
                l=Math.min(l,v.left());r=Math.max(r,v.right());
                t=Math.min(t,v.top());b=Math.max(b,v.bottom());
                n+=v.text.codePointCount(0,v.text.length());
                sum+=v.confidence;
            }
            left=l;top=t;right=r;bottom=b;
            centerX=(l+r)*.5f;centerY=(t+b)*.5f;
            confidence=ids.isEmpty()?0:(float)(sum/ids.size());
            chars=n;
        }
    }

    private static float overlap(float a,float b,float c,float d) {
        return Math.max(0f,Math.min(b,d)-Math.max(a,c));
    }

    private static boolean valid(ReadingOrder.Box v) {
        if(v==null||v.text==null||v.text.isBlank()||
            v.x.length!=4||v.y.length!=4)return false;
        if(!Float.isFinite(v.left())||!Float.isFinite(v.right())||
           !Float.isFinite(v.top())||!Float.isFinite(v.bottom()))return false;
        return v.right()>v.left() && v.bottom()>v.top();
    }

    private static float strokeWidth(ReadingOrder.Box b) {
        float e1=(float)Math.hypot(b.x[1]-b.x[0],b.y[1]-b.y[0]);
        float e2=(float)Math.hypot(b.x[2]-b.x[1],b.y[2]-b.y[1]);
        return Math.max(1f,Math.min(e1,e2));
    }

    private static boolean touches(ReadingOrder.Box a,ReadingOrder.Box b) {
        float aw=a.right()-a.left(), bw=b.right()-b.left();
        float ah=a.bottom()-a.top(),bh=b.bottom()-b.top();
        float gapX=Math.max(0f,Math.max(a.left()-b.right(),b.left()-a.right()));
        float gapY=Math.max(0f,Math.max(a.top()-b.bottom(),b.top()-a.bottom()));
        float xOverlap=overlap(a.left(),a.right(),b.left(),b.right());
        float yOverlap=overlap(a.top(),a.bottom(),b.top(),b.bottom());
        float xa=Math.abs(a.centerX()-b.centerX());
        float ya=Math.abs(a.centerY()-b.centerY());
        boolean aTall=ah>=Math.max(95f,aw*2.5f);
        boolean bTall=bh>=Math.max(95f,bw*2.5f);

        // Chinese long parallel lines, including trapezoids from perspective:
        // horizontal X bounds can overlap, so use real quad short-edge width.
        if(aTall && bTall) {
            float stroke=Math.max(strokeWidth(a),strokeWidth(b));
            return xa<=Math.max(22f,stroke*2.25f) &&
                yOverlap>=Math.min(ah,bh)*.44f;
        }
        // Genuine horizontal paragraphs: adjacent lines share large X overlap.
        boolean aWide=aw>=Math.max(70f,ah*2.7f);
        boolean bWide=bw>=Math.max(70f,bh*2.7f);
        if(aWide && bWide) {
            return xOverlap>=Math.min(aw,bw)*.37f &&
                gapY<=Math.max(12f,Math.min(ah,bh)*1.15f);
        }
        // A sequence of independent glyph OCR boxes in ONE vertical sign.
        boolean glyphs=a.text.codePointCount(0,a.text.length())<=2 &&
                       b.text.codePointCount(0,b.text.length())<=2;
        float narrow=Math.min(aw,bw);
        if(glyphs && xa<=Math.max(9f,narrow*.52f) &&
           gapY<=Math.max(12f,Math.min(ah,bh)*1.35f) &&
           ya>=Math.min(ah,bh)*.50f)
            return true;
        // Components of the same horizontally laid-out label.
        if(glyphs && yOverlap>=Math.min(ah,bh)*.55f &&
           gapX<=Math.max(9f,Math.min(ah,bh)*.65f) &&
           xa>=Math.min(aw,bw)*.55f)
            return true;
        return false;
    }
    private static int root(int[] parents,int i) {
        while(parents[i]!=i){parents[i]=parents[parents[i]];i=parents[i];}
        return i;
    }
    private static void union(int[] parents,int i,int j) {
        int a=root(parents,i),b=root(parents,j);
        if(a!=b) parents[Math.max(a,b)]=Math.min(a,b);
    }

    public static List<Region> partition(List<ReadingOrder.Box> boxes) {
        if(boxes==null||boxes.isEmpty())return Collections.emptyList();
        int n=boxes.size();
        int[] parents=new int[n];
        for(int i=0;i<n;i++)parents[i]=i;
        for(int i=0;i<n;i++) {
            if(!valid(boxes.get(i)))continue;
            for(int j=i+1;j<n;j++) {
                if(!valid(boxes.get(j)))continue;
                if(touches(boxes.get(i),boxes.get(j)))union(parents,i,j);
            }
        }
        List<Integer> roots=new ArrayList<>();
        List<List<Integer>> groups=new ArrayList<>();
        for(int i=0;i<n;i++) {
            if(!valid(boxes.get(i)))continue;
            int root=root(parents,i),idx=roots.indexOf(root);
            if(idx<0){roots.add(root);groups.add(new ArrayList<>());idx=groups.size()-1;}
            groups.get(idx).add(i);
        }
        List<Region> out=new ArrayList<>();
        for(List<Integer> group:groups)out.add(new Region(group,boxes));
        out.sort(Comparator.comparingDouble((Region r)->r.centerX)
            .thenComparingDouble(r->r.centerY));
        return out;
    }
}
