package com.vobichngan.kanzi;

import java.util.Arrays;
import java.util.Comparator;

/**
 * SHADOW-ONLY. Finds a concrete geometry failure risk in upstream PaddleOCR
 * v3.7.0 QuadGeometry.orderMinAreaRectPoints, which sorts minAreaRect vertices
 * by X and assumes the two leftmost vertices belong to the left edge.
 *
 * In a long, skewed, narrow TRUE vertical line, the two bottom vertices can
 * both lie to the left of both top vertices (or the reverse). Then X sorting
 * mistakes the long vertical axis for the crop width, and the subsequent
 * QuadTextCrop rotate-if-tall rule never fires. This class does NOT change OCR.
 *
 * Inputs are four points of a minAreaRect quad, in any order. No text
 * processing, model, Android APIs, image reads, or TTS.
 */
public final class VerticalCropAxisGuard {
    private VerticalCropAxisGuard() {}

    public enum Status {
        INVALID, NOT_LONG_VERTICAL, STABLE_X_GROUPS, X_SORT_AXIS_RISK
    }
    public static final class Inspection {
        public final Status status;
        public final float horizontalSeparation;
        public final float verticalToHorizontalRatio;
        /**
         * Clockwise top-left, top-right, bottom-right, bottom-left for
         * potential off-line comparison only; never use to mutate live OCR.
         */
        public final float[] topBottomOrdered;
        Inspection(Status status, float margin, float ratio, float[] points) {
            this.status = status;
            this.horizontalSeparation = margin;
            this.verticalToHorizontalRatio = ratio;
            this.topBottomOrdered = points.clone();
        }
    }

    public static Inspection inspect(float[] q8) {
        if (q8 == null || q8.length != 8)
            return new Inspection(Status.INVALID, Float.NaN, Float.NaN, new float[8]);
        float[][] p = new float[4][2];
        float minx=Float.POSITIVE_INFINITY,maxx=Float.NEGATIVE_INFINITY;
        float miny=Float.POSITIVE_INFINITY,maxy=Float.NEGATIVE_INFINITY;
        for(int i=0;i<4;i++) {
            float x=q8[2*i], y=q8[2*i+1];
            if(!Float.isFinite(x)||!Float.isFinite(y))
                return new Inspection(Status.INVALID,Float.NaN,Float.NaN,new float[8]);
            p[i][0]=x; p[i][1]=y;
            minx=Math.min(minx,x); maxx=Math.max(maxx,x);
            miny=Math.min(miny,y); maxy=Math.max(maxy,y);
        }
        float width=maxx-minx,height=maxy-miny;
        if(width < 2f || height < 2f)
            return new Inspection(Status.INVALID, Float.NaN, Float.NaN, new float[8]);
        float ratio=height/width;
        if(ratio<2.6f || height<60f)
            return new Inspection(Status.NOT_LONG_VERTICAL,0f,ratio,new float[8]);

        // Y grouping is an *independent* reference: sorted top pair / bottom
        // pair, and X-order within each. Does not rely on supplied index order.
        Arrays.sort(p,Comparator.comparingDouble((float[] r)->r[1])
            .thenComparingDouble(r->r[0]));
        float[] top0=p[0],top1=p[1],bottom0=p[2],bottom1=p[3];
        if(top0[0]>top1[0]) {float[] t=top0;top0=top1;top1=t;}
        if(bottom0[0]>bottom1[0]) {float[] t=bottom0;bottom0=bottom1;bottom1=t;}
        float[] out={
            top0[0],top0[1],top1[0],top1[1],
            bottom1[0],bottom1[1],bottom0[0],bottom0[1]
        };
        float topMin=top0[0],topMax=top1[0];
        float bottomMin=bottom0[0],bottomMax=bottom1[0];
        // Positive when the 2 top points and 2 bottom points are completely
        // disjoint along X. Exactly the regime where upstream's X grouping
        // categorically cannot distinguish top/bottom correctly.
        float margin=Math.max(topMin-bottomMax,bottomMin-topMax);
        Status status=margin>0f?Status.X_SORT_AXIS_RISK:Status.STABLE_X_GROUPS;
        return new Inspection(status,margin,ratio,out);
    }
}
