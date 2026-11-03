package com.vobich.kanzi.craftprobe;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * CPU CRAFT region/affinity decoder for the standalone phone diagnostic only.
 *
 * Port of scripts/craft_small_detector_ab.py:craft_regions:
 * 4-connected text+affinity components, exclude link-only pixels, rectangle
 * dilation and minimum-area rotated boxes. Works with [1,h,w,2] probabilities,
 * with CRAFT output grid at half the preprocessed input resolution.
 *
 * No text transcription or true OCR; the boxes must be visually verified.
 */
final class CraftBoxDecoder {
    private CraftBoxDecoder() {}
    static final float LOW_TEXT = .40f;
    static final float LINK = .40f;
    static final float TEXT = .70f;
    static final int MAX_BOXES = 150;

    static final class Box {
        final float[] quad;
        final float confidence;
        Box(float[] quad, float confidence) {
            this.quad = quad;
            this.confidence = confidence;
        }
    }

    static final class Result {
        final List<Box> boxes;
        final int rawComponents;
        final int survivingComponents;
        Result(List<Box> boxes, int rawComponents, int survivingComponents) {
            this.boxes = boxes;
            this.rawComponents = rawComponents;
            this.survivingComponents = survivingComponents;
        }
    }

    private static final class Component {
        final int id, area, minX, minY, maxX, maxY;
        final float maxText;
        Component(int id, int area, int minX, int minY, int maxX, int maxY, float maxText) {
            this.id=id; this.area=area; this.minX=minX; this.minY=minY;
            this.maxX=maxX; this.maxY=maxY; this.maxText=maxText;
        }
    }

    private static final class Point {
        final double x,y;
        Point(double x, double y) { this.x=x; this.y=y; }
    }

    static Result decode(FloatBuffer raw, int scoreHeight, int scoreWidth,
                         int sourceWidth, int sourceHeight,
                         int inputWidth, int inputHeight) {
        if (scoreHeight < 1 || scoreWidth < 1 ||
            scoreWidth > 2048 || scoreHeight > 2048 ||
            sourceWidth < 1 || sourceHeight < 1 || inputWidth < 32 || inputHeight < 32)
            throw new IllegalArgumentException("Invalid scoremap / source dimensions");
        if (scoreHeight * 2 != inputHeight || scoreWidth * 2 != inputWidth)
            throw new IllegalArgumentException("CRAFT scoremap must be input / 2");
        final int n = scoreWidth*scoreHeight;
        if (raw.limit() < n*2)
            throw new IllegalArgumentException("Truncated CRAFT probability tensor");

        final byte[] text = new byte[n];
        final byte[] link = new byte[n];
        final byte[] active = new byte[n];
        final int[] label = new int[n];
        final int[] queue = new int[n];
        for (int i=0; i<n; ++i) {
            float a=raw.get(i*2), b=raw.get(i*2+1);
            if (!Float.isFinite(a) || !Float.isFinite(b) ||
                a < -.25f || a > 1.25f || b < -.25f || b > 1.25f)
                throw new IllegalArgumentException("Invalid CRAFT score probability");
            text[i] = (byte) (a>LOW_TEXT?1:0);
            link[i] = (byte) (b>LINK?1:0);
            active[i] = (byte) ((text[i]!=0 || link[i]!=0)?1:0);
        }

        final List<Component> components = new ArrayList<>();
        int id=0;
        // Pure Java 4-connected BFS; runs only after a single requested photo inference.
        for (int start=0; start<n; ++start) {
            if (active[start]==0 || label[start]!=0) continue;
            ++id;
            int head=0,tail=1;
            queue[0]=start;label[start]=id;
            int area=0,minx=scoreWidth,miny=scoreHeight,maxx=0,maxy=0;
            float high=Float.NEGATIVE_INFINITY;
            while (head<tail) {
                int pos=queue[head++];
                int y=pos/scoreWidth,x=pos%scoreWidth;
                ++area;
                if (x<minx) minx=x;
                if (y<miny) miny=y;
                if (x>maxx) maxx=x;
                if (y>maxy) maxy=y;
                high=Math.max(high,raw.get(pos*2));
                // Left / right / up / down; avoid crossing row bounds.
                if (x>0) tail=push(pos-1,id,active,label,queue,tail);
                if (x+1<scoreWidth) tail=push(pos+1,id,active,label,queue,tail);
                if (y>0) tail=push(pos-scoreWidth,id,active,label,queue,tail);
                if (y+1<scoreHeight) tail=push(pos+scoreWidth,id,active,label,queue,tail);
            }
            if (area>=10 && high>=TEXT) {
                components.add(new Component(id,area,minx,miny,maxx,maxy,high));
            }
        }

        List<Box> boxes=new ArrayList<>();
        for (Component component:components) {
            int ww=component.maxX-component.minX+1;
            int hh=component.maxY-component.minY+1;
            int dilation=(int) (Math.sqrt((double) component.area * Math.min(ww,hh) /
                Math.max(1.0,(double)ww*hh)) * 2.0);
            // Port of cv2 bounding crop: max(0,min-dilation), end+padding.
            int sx=Math.max(0,component.minX-dilation);
            int sy=Math.max(0,component.minY-dilation);
            int ex=Math.min(scoreWidth,component.maxX+1+dilation+1);
            int ey=Math.min(scoreHeight,component.maxY+1+dilation+1);
            int rw=ex-sx,rh=ey-sy;
            byte[] grown=new byte[rw*rh];
            // cv2.getStructuringElement(MORPH_RECT,(1+dilation,1+dilation)).
            int kernel=dilation+1;
            int anchor=kernel/2;
            int left=kernel-1-anchor, right=anchor;
            for (int y=component.minY; y<=component.maxY; ++y) {
                for (int x=component.minX; x<=component.maxX; ++x) {
                    int pos=y*scoreWidth+x;
                    if (label[pos]!=component.id) continue;
                    // Suppress pixels which contain affinity but NO text.
                    if (link[pos]!=0 && text[pos]==0) continue;
                    for (int yy=Math.max(sy,y-left); yy<=Math.min(ey-1,y+right); ++yy) {
                        int offset=(yy-sy)*rw;
                        Arrays.fill(grown,offset+Math.max(sx,x-left)-sx,
                            offset+Math.min(ex-1,x+right)-sx+1,(byte)1);
                    }
                }
            }
            List<Point> boundary=new ArrayList<>();
            for (int y=0;y<rh;++y) {
                for (int x=0;x<rw;++x) {
                    if (grown[y*rw+x]==0) continue;
                    if (x==0 || x==rw-1 || y==0 || y==rh-1 ||
                        grown[y*rw+x-1]==0 || grown[y*rw+x+1]==0 ||
                        grown[(y-1)*rw+x]==0 || grown[(y+1)*rw+x]==0) {
                        boundary.add(new Point(sx+x,sy+y));
                    }
                }
            }
            if (boundary.size()<6) continue;
            float[] quad=minAreaRect(boundary);
            float minxx=Float.POSITIVE_INFINITY,minyy=Float.POSITIVE_INFINITY;
            float maxxx=Float.NEGATIVE_INFINITY,maxyy=Float.NEGATIVE_INFINITY;
            for (int p=0;p<4;++p) {
                // exactly input-to-original scaling: CRAFT scoremap at input/2
                float xx=clamp((float)(quad[2*p]*2.0*sourceWidth/inputWidth),0,sourceWidth);
                float yy=clamp((float)(quad[2*p+1]*2.0*sourceHeight/inputHeight),0,sourceHeight);
                quad[2*p]=xx;quad[2*p+1]=yy;
                minxx=Math.min(minxx,xx); maxxx=Math.max(maxxx,xx);
                minyy=Math.min(minyy,yy); maxyy=Math.max(maxyy,yy);
            }
            if (maxxx-minxx>=6 && maxyy-minyy>=6) {
                boxes.add(new Box(quad, component.maxText));
            }
        }
        boxes.sort((a,b)->Float.compare(b.confidence,a.confidence));
        int surviving=boxes.size();
        if (boxes.size()>MAX_BOXES)
            boxes=new ArrayList<>(boxes.subList(0,MAX_BOXES));
        return new Result(boxes,id,surviving);
    }

    private static int push(int pos,int id,byte[] active,int[] labels,int[] q,int tail) {
        if (active[pos]!=0 && labels[pos]==0) {
            labels[pos]=id;q[tail++]=pos;
        }
        return tail;
    }

    private static float clamp(float value,float lo,float hi) {
        return Math.max(lo,Math.min(hi,value));
    }

    private static double cross(Point a,Point b,Point c) {
        return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
    }

    private static List<Point> hull(List<Point> points) {
        points.sort((a,b) -> a.x==b.x?Double.compare(a.y,b.y):Double.compare(a.x,b.x));
        List<Point> lower=new ArrayList<>(),upper=new ArrayList<>();
        for (Point v:points) {
            while (lower.size()>=2 && cross(lower.get(lower.size()-2),lower.get(lower.size()-1),v)<=0)
                lower.remove(lower.size()-1);
            lower.add(v);
        }
        for (int i=points.size()-1;i>=0;--i) {
            Point v=points.get(i);
            while (upper.size()>=2 && cross(upper.get(upper.size()-2),upper.get(upper.size()-1),v)<=0)
                upper.remove(upper.size()-1);
            upper.add(v);
        }
        if (!lower.isEmpty()) lower.remove(lower.size()-1);
        if (!upper.isEmpty()) upper.remove(upper.size()-1);
        lower.addAll(upper);
        return lower;
    }

    private static float[] minAreaRect(List<Point> points) {
        List<Point> poly=hull(points);
        if (poly.size()<2) {
            Point p=poly.get(0);
            return new float[]{(float)p.x,(float)p.y,(float)p.x,(float)p.y,
                (float)p.x,(float)p.y,(float)p.x,(float)p.y};
        }
        double bestArea=Double.POSITIVE_INFINITY,bestCos=1,bestSin=0;
        double bestMinU=0,bestMaxU=0,bestMinV=0,bestMaxV=0;
        for (int i=0;i<poly.size();++i) {
            Point a=poly.get(i),b=poly.get((i+1)%poly.size());
            double angle=Math.atan2(b.y-a.y,b.x-a.x);
            double cos=Math.cos(angle),sin=Math.sin(angle);
            double u0=Double.POSITIVE_INFINITY,u1=Double.NEGATIVE_INFINITY;
            double v0=Double.POSITIVE_INFINITY,v1=Double.NEGATIVE_INFINITY;
            for (Point p:poly) {
                double u=p.x*cos+p.y*sin;
                double v=-p.x*sin+p.y*cos;
                u0=Math.min(u0,u);u1=Math.max(u1,u);
                v0=Math.min(v0,v);v1=Math.max(v1,v);
            }
            double area=(u1-u0)*(v1-v0);
            if (area<bestArea) {
                bestArea=area;
                bestCos=cos;bestSin=sin;
                bestMinU=u0;bestMaxU=u1;bestMinV=v0;bestMaxV=v1;
            }
        }
        double[] uv={bestMinU,bestMinV,bestMaxU,bestMinV,
            bestMaxU,bestMaxV,bestMinU,bestMaxV};
        float[] result=new float[8];
        for (int k=0;k<4;++k) {
            result[2*k]=(float)(uv[2*k]*bestCos-uv[2*k+1]*bestSin);
            result[2*k+1]=(float)(uv[2*k]*bestSin+uv[2*k+1]*bestCos);
        }
        return result;
    }
}
