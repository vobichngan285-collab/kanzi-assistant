package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * EXPERIMENTAL horizontal-row proposals from detector quads and independent
 * price/text row anchors; NO OCR text/semantics and NO automatic text rewriting.
 *
 * The previously proven CrossRowOrientationAudit has a hard false positive
 * (genuinely vertical columns beside horizontal prices). A proposal is ONLY
 * an invitation to run a same-frame shadow recognition and compare visually.
 * Never suppress original boxes, feed TTS, or treat confidence as ground truth.
 */
public final class HorizontalCrossRowProposal {
    private HorizontalCrossRowProposal() {}

    private static final class Rect {
        final float x0,y0,x1,y1,cx,cy;
        Rect(float[] q) {
            float a=Float.POSITIVE_INFINITY,b=Float.POSITIVE_INFINITY,
                  c=Float.NEGATIVE_INFINITY,d=Float.NEGATIVE_INFINITY;
            for(int i=0;i<8;i+=2) {
                a=Math.min(a,q[i]);c=Math.max(c,q[i]);
                b=Math.min(b,q[i+1]);d=Math.max(d,q[i+1]);
            }
            x0=a;y0=b;x1=c;y1=d;cx=(a+c)*.5f;cy=(b+d)*.5f;
        }
    }
    public static final class Proposal {
        public final int[] suspectIds;
        public final int[] anchorIds;
        public final float[][] xyxy;
        public final String status;
        private Proposal(int[] suspect,int[] anchor,float[][] row,String why) {
            suspectIds=suspect.clone();anchorIds=anchor.clone();
            xyxy=new float[row.length][4];
            for(int i=0;i<row.length;i++)xyxy[i]=row[i].clone();
            status=why;
        }
    }
    private static Proposal abstain(String why) {
        return new Proposal(new int[0],new int[0],new float[0][0],why);
    }
    private static Rect find(Map<Integer,Rect> m,int key){return m.get(key);}
    public static Proposal propose(List<CrossRowOrientationAudit.Box> source,
                                   int width,int height, int maxRows) {
        if(source==null || width<50 || height<50 || maxRows<1 || maxRows>4)
            return abstain("INVALID_INPUT");
        final CrossRowOrientationAudit.Audit audit=
            CrossRowOrientationAudit.inspect(source,width,height);
        if(audit.suspects.size()<3)return abstain(audit.status);
        Map<Integer,Rect> boxes=new HashMap<>();
        for(CrossRowOrientationAudit.Box b:source) {
            if(b==null || b.quad==null || b.quad.length!=8 ||
                !Float.isFinite(b.confidence))continue;
            boolean ok=true;
            for(float v:b.quad)if(!Float.isFinite(v)){ok=false;break;}
            if(!ok)continue;
            Rect r=new Rect(b.quad);
            if(r.x0<0||r.y0<0||r.x1>width||r.y1>height||
                r.x1-r.x0<4||r.y1-r.y0<4)continue;
            boxes.put(b.sourceId,r);
        }
        // Anchor agreement is mandatory: no mixing unrelated price columns.
        CrossRowOrientationAudit.Alert seed=audit.suspects.get(0);
        List<Integer> suspect=new ArrayList<>();
        for(CrossRowOrientationAudit.Alert a:audit.suspects) {
            if(Arrays.equals(a.anchorIds,seed.anchorIds)&&find(boxes,a.sourceId)!=null)
                suspect.add(a.sourceId);
        }
        if(suspect.size()<3||suspect.size()>8||seed.anchorIds.length<3 ||
            seed.anchorIds.length>8) return abstain("UNSTABLE_OR_AMBIGUOUS_GROUP");

        List<Rect> anchors=new ArrayList<>();
        for(int id:seed.anchorIds) {
            Rect r=find(boxes,id);
            if(r==null)return abstain("MISSING_ANCHOR");
            anchors.add(r);
        }
        anchors.sort((a,b)->Float.compare(a.cy,b.cy));
        float[] hArr=new float[anchors.size()];
        for(int i=0;i<anchors.size();i++)hArr[i]=anchors.get(i).y1-anchors.get(i).y0;
        Arrays.sort(hArr);
        float h=(hArr[(hArr.length-1)/2]+hArr[hArr.length/2])*.5f;
        if(h<8||h>height*.18f)return abstain("BAD_TEXT_SCALE");
        List<Float> gaps=new ArrayList<>();
        for(int i=1;i<anchors.size();i++)gaps.add(anchors.get(i).cy-anchors.get(i-1).cy);
        Collections.sort(gaps);
        float step=(gaps.get((gaps.size()-1)/2)+gaps.get(gaps.size()/2))*.5f;
        if(step<h*.85f || step>h*1.65f)return abstain("ROW_GAP_OUT_OF_RANGE");
        for(float g:gaps)if(Math.abs(g-step)>h*.30f)return abstain("IRREGULAR_ANCHORS");

        float x0=Float.POSITIVE_INFINITY,x1=Float.NEGATIVE_INFINITY,
              lowest=Float.NEGATIVE_INFINITY;
        for(int id:suspect) {
            Rect r=find(boxes,id);
            x0=Math.min(x0,r.x0);x1=Math.max(x1,r.x1);
            lowest=Math.max(lowest,r.y1);
        }
        float pad=Math.round(h*.23f);
        x0=Math.max(0,Math.round(x0-pad));
        x1=Math.min(width,Math.round(x1+pad));
        float span=x1-x0;
        if(span<2*h||span>Math.min(400f,width*.45f))
            return abstain("CROP_SPAN_SUSPICIOUS");
        // A candidate row must stay separate from the anchor column.
        for(Rect r:anchors)if(r.x0<x1+h*.5f && r.x1>x0)
            return abstain("ANCHOR_OVERLAP");

        List<Float> centers=new ArrayList<>();
        for(Rect r:anchors)centers.add(r.cy);
        // Inferred fourth row only when actual suspicious detector quad reaches
        // WELL below the inferred center, not just because three prices exist.
        if(centers.size()==3&&centers.size()<maxRows) {
            float extra=centers.get(centers.size()-1)+step;
            if(lowest>=extra+.45f*h && extra<height-h)
                centers.add(extra);
        }
        if(centers.size()>maxRows)centers=centers.subList(0,maxRows);
        List<float[]> out=new ArrayList<>();
        float rowHeight=Math.round(h*1.22f);
        for(float center:centers) {
            float y0=Math.max(0,Math.round(center-rowHeight*.5f));
            float y1=Math.min(height,y0+rowHeight);
            if(y1-y0<Math.max(20f,h*.75f))return abstain("INVALID_ROW_CROP");
            out.add(new float[]{x0,y0,x1,y1});
        }
        if(out.size()<3)return abstain("INSUFFICIENT_ROW_CANDIDATES");
        int[] sus=new int[suspect.size()];
        for(int i=0;i<sus.length;i++)sus[i]=suspect.get(i);
        Arrays.sort(sus);
        return new Proposal(sus,seed.anchorIds,out.toArray(new float[0][]),
            "SHADOW_ONLY_GEOMETRIC_ROW_HYPOTHESIS");
    }
}
