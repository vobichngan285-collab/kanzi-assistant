package com.vobich.kanzi.craftprobe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * CPU-only CRAFT map-to-text-box reconstruction for the standalone mobile test.
 *
 * Port of the experimental fixed-threshold algorithm used in
 * scripts/craft_small_detector_ab.py:
 * region>.4 OR affinity>.4, 4-connected components, min 10 pixels,
 * max region score >=.7, link-only removal, square dilation, min-area quad.
 *
 * This class never recognizes any characters. Box accuracy relative to the
 * OpenCV-based reference is verified separately on deterministic mask tests;
 * actual camera / Chinese text correctness still needs user photos.
 */
public final class CraftBoxPostprocessor {
    private static final float LOW_TEXT = .4f;
    private static final float LINK = .4f;
    private static final float HIGH_TEXT = .7f;
    private static final int MIN_AREA = 10;
    private static final int MAX_BOXES = 150;

    private CraftBoxPostprocessor() { }

    public static final class Box {
        public final float[] quad; // x,y pairs in original source pixels
        public final float left, top, right, bottom, confidence;
        Box(float[] q, float score) {
            this.quad = q;
            this.confidence = score;
            float x0 = Float.POSITIVE_INFINITY, x1 = Float.NEGATIVE_INFINITY;
            float y0 = Float.POSITIVE_INFINITY, y1 = Float.NEGATIVE_INFINITY;
            for (int i=0; i<4; i++) {
                x0 = Math.min(x0, q[2*i]); x1 = Math.max(x1,q[2*i]);
                y0 = Math.min(y0, q[2*i+1]); y1 = Math.max(y1,q[2*i+1]);
            }
            left=x0; top=y0; right=x1; bottom=y1;
        }
    }

    public static final class Result {
        public final int rawComponentCount;
        public final List<Box> boxes;
        public final int heatmapWidth, heatmapHeight;
        Result(int n, List<Box> boxes, int w, int h) {
            rawComponentCount=n;
            this.boxes=Collections.unmodifiableList(boxes);
            heatmapWidth=w; heatmapHeight=h;
        }
    }

    private static final class Component {
        int id, area;
        int minX, minY, maxX, maxY;
        float maxText;
        Component(int id,int x,int y) {
            this.id=id; area=0; minX=maxX=x; minY=maxY=y;
        }
        void add(int x,int y,float score) {
            area++;
            minX=Math.min(x,minX); maxX=Math.max(x,maxX);
            minY=Math.min(y,minY); maxY=Math.max(y,maxY);
            maxText=Math.max(score,maxText);
        }
    }

    private static final class Point {
        final float x,y;
        Point(float x,float y) {this.x=x;this.y=y;}
    }

    public static Result reconstruct(float[] output, int scoreW, int scoreH,
                                     int originalW, int originalH) {
        if (scoreW<=0 || scoreH<=0 || originalW<=0 || originalH<=0
                || (long)scoreW*scoreH>2_000_000L
                || output.length!=(long)scoreW*scoreH*2L)
            throw new IllegalArgumentException("Invalid CRAFT score heatmap shape");
        final int n=scoreW*scoreH;
        boolean[] text=new boolean[n];
        boolean[] link=new boolean[n];
        byte[] union=new byte[n];
        for(int i=0;i<n;i++) {
            float region=output[2*i];
            float affinity=output[2*i+1];
            if (!Float.isFinite(region)||!Float.isFinite(affinity)||
                    region<-.25f||region>1.25f||affinity<-.25f||affinity>1.25f)
                throw new IllegalArgumentException("CRAFT score outside expected range");
            text[i]=region>LOW_TEXT;
            link[i]=affinity>LINK;
            union[i]=(byte)(text[i]||link[i]?1:0);
        }
        int[] labels=new int[n];
        int[] queue=new int[n];
        ArrayList<Component> comps=new ArrayList<>();
        int id=0;
        for(int seed=0;seed<n;seed++) {
            if (union[seed]==0||labels[seed]!=0)continue;
            id++;
            final Component component=new Component(id,seed%scoreW,seed/scoreW);
            int start=0,end=1;
            queue[0]=seed;labels[seed]=id;
            while(start<end) {
                int pos=queue[start++];
                int x=pos%scoreW,y=pos/scoreW;
                component.add(x,y,Math.min(1f,Math.max(0f,output[2*pos])));
                if (x>0 && union[pos-1]!=0 && labels[pos-1]==0) {
                    labels[pos-1]=id;queue[end++]=pos-1;
                }
                if (x+1<scoreW && union[pos+1]!=0 && labels[pos+1]==0) {
                    labels[pos+1]=id;queue[end++]=pos+1;
                }
                if (y>0 && union[pos-scoreW]!=0 && labels[pos-scoreW]==0) {
                    labels[pos-scoreW]=id;queue[end++]=pos-scoreW;
                }
                if (y+1<scoreH && union[pos+scoreW]!=0 && labels[pos+scoreW]==0) {
                    labels[pos+scoreW]=id;queue[end++]=pos+scoreW;
                }
            }
            if(component.area>=MIN_AREA && component.maxText>=HIGH_TEXT)comps.add(component);
        }
        ArrayList<Box> boxes=new ArrayList<>();
        for(Component component:comps) {
            int rectW=component.maxX-component.minX+1;
            int rectH=component.maxY-component.minY+1;
            int dilation=(int)(Math.sqrt((double)component.area*Math.min(rectW,rectH)
                    /Math.max(1,rectW*rectH))*2.0);
            int fromX=Math.max(0,component.minX-dilation);
            int fromY=Math.max(0,component.minY-dilation);
            int toX=Math.min(scoreW,component.maxX+dilation+2);
            int toY=Math.min(scoreH,component.maxY+dilation+2);
            int pw=toX-fromX,ph=toY-fromY;
            if(pw<=0||ph<=0)continue;
            // Remove affinity-only pixels before morphological dilation.
            byte[] seed=new byte[pw*ph];
            int active=0;
            for(int y=0;y<ph;y++) {
                int pos=(y+fromY)*scoreW+fromX;
                for(int x=0;x<pw;x++) {
                    int p=pos+x;
                    if (labels[p]==component.id && !(link[p]&&!text[p])) {
                        seed[y*pw+x]=1;active++;
                    }
                }
            }
            if(active<1)continue;

            int kernelSize=1+dilation;
            int left=(kernelSize-1)/2;
            int right=kernelSize/2;
            byte[] horizontal=new byte[pw*ph];
            for(int y=0;y<ph;y++) {
                int row=y*pw, count=0;
                for(int x=0;x<=Math.min(pw-1,right);x++)count+=seed[row+x];
                for(int x=0;x<pw;x++) {
                    if(count>0)horizontal[row+x]=1;
                    if(x-left>=0)count-=seed[row+x-left];
                    if(x+right+1<pw)count+=seed[row+x+right+1];
                }
            }
            byte[] dilated=new byte[pw*ph];
            for(int x=0;x<pw;x++) {
                int count=0;
                for(int y=0;y<=Math.min(ph-1,right);y++)count+=horizontal[y*pw+x];
                for(int y=0;y<ph;y++) {
                    if(count>0)dilated[y*pw+x]=1;
                    if(y-left>=0)count-=horizontal[(y-left)*pw+x];
                    if(y+right+1<ph)count+=horizontal[(y+right+1)*pw+x];
                }
            }

            ArrayList<Point> boundary=new ArrayList<>();
            for(int y=0;y<ph;y++)for(int x=0;x<pw;x++) {
                int p=y*pw+x;
                if (dilated[p]==0)continue;
                if (x==0||x==pw-1||y==0||y==ph-1||
                        dilated[p-1]==0||dilated[p+1]==0||
                        dilated[p-pw]==0||dilated[p+pw]==0)
                    boundary.add(new Point(x+fromX,y+fromY));
            }
            if(boundary.size()<4)continue;
            float[] quad=minAreaQuad(boundary);
            for(int j=0;j<4;j++) {
                quad[2*j]=clamp(quad[2*j]*originalW/scoreW,0,originalW);
                quad[2*j+1]=clamp(quad[2*j+1]*originalH/scoreH,0,originalH);
            }
            Box box=new Box(quad,component.maxText);
            if(box.right-box.left>=6 && box.bottom-box.top>=6)
                boxes.add(box);
        }
        boxes.sort((a,b)->Float.compare(b.confidence,a.confidence));
        if(boxes.size()>MAX_BOXES) boxes=new ArrayList<>(boxes.subList(0,MAX_BOXES));
        return new Result(id,boxes,scoreW,scoreH);
    }

    private static float clamp(float v,float min,float max) {
        return Math.min(max,Math.max(min,v));
    }

    private static float cross(Point a,Point b,Point c) {
        return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
    }

    private static float[] minAreaQuad(List<Point> pts) {
        ArrayList<Point> sorted=new ArrayList<>(pts);
        sorted.sort(Comparator.comparingDouble((Point p)->p.x).thenComparingDouble(p->p.y));
        ArrayList<Point> hull=new ArrayList<>();
        for(Point p:sorted) {
            while(hull.size()>=2&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)
                hull.remove(hull.size()-1);
            hull.add(p);
        }
        int lower=hull.size();
        for(int k=sorted.size()-2;k>=0;k--) {
            Point p=sorted.get(k);
            while(hull.size()>lower&&cross(hull.get(hull.size()-2),hull.get(hull.size()-1),p)<=0)
                hull.remove(hull.size()-1);
            hull.add(p);
        }
        if(hull.size()>1)hull.remove(hull.size()-1);
        if(hull.size()<2) {
            Point p=sorted.get(0);
            return new float[]{p.x,p.y,p.x,p.y,p.x,p.y,p.x,p.y};
        }
        double bestArea=Double.POSITIVE_INFINITY;
        double[] best={1,0,0,0,0,0};
        for(int i=0;i<hull.size();i++) {
            Point a=hull.get(i),b=hull.get((i+1)%hull.size());
            double dx=b.x-a.x,dy=b.y-a.y;
            double length=Math.hypot(dx,dy);
            if(length<1e-7)continue;
            double ux=dx/length,uy=dy/length;
            double vx=-uy,vy=ux;
            double minU=Double.POSITIVE_INFINITY,maxU=Double.NEGATIVE_INFINITY;
            double minV=Double.POSITIVE_INFINITY,maxV=Double.NEGATIVE_INFINITY;
            for(Point p:hull) {
                double pu=ux*p.x+uy*p.y,pv=vx*p.x+vy*p.y;
                minU=Math.min(minU,pu);maxU=Math.max(maxU,pu);
                minV=Math.min(minV,pv);maxV=Math.max(maxV,pv);
            }
            double area=(maxU-minU)*(maxV-minV);
            if(area<bestArea) {
                bestArea=area;
                best=new double[]{ux,uy,minU,maxU,minV,maxV};
            }
        }
        float[] quad=new float[8];
        double ux=best[0],uy=best[1],vx=-uy,vy=ux;
        double[][] points={{best[2],best[4]},{best[3],best[4]},{best[3],best[5]},{best[2],best[5]}};
        for(int j=0;j<4;j++) {
            quad[2*j]=(float)(ux*points[j][0]+vx*points[j][1]);
            quad[2*j+1]=(float)(uy*points[j][0]+vy*points[j][1]);
        }
        return quad;
    }
}
