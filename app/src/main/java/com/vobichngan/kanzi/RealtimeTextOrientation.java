package com.vobichngan.kanzi;

import java.util.ArrayList;
import java.util.List;

/**
 * Text-direction-aware ordering for the LIVE (non-book) camera.
 * Only uses OCR's EXISTING quadrilateral coordinates. No bitmap rotate,
 * AI inference, frame accumulation or recognition retry.
 *
 * Rotation is inferred only from repeated, high-confidence long text edges.
 * Ambiguous or mixed packaging layouts keep their native top-first order.
 */
public final class RealtimeTextOrientation {
    private RealtimeTextOrientation() {}

    /** 0, 90, or 270 degrees clockwise; 0 means no reliable global side tilt. */
    public static int inferredClockwiseRotation(List<ReadingOrder.Box> boxes) {
        if (boxes == null || boxes.size() < 2) return 0;
        int clockwise = 0, counterclockwise = 0, horizontal = 0;
        for (ReadingOrder.Box b : boxes) {
            if (b == null || b.x.length != 4 || b.y.length != 4 ||
                b.confidence < .45f || b.text == null || b.text.length() < 2) continue;
            float dx = b.x[1] - b.x[0], dy = b.y[1] - b.y[0];
            float edge = (float)Math.hypot(dx,dy);
            float side = (float)Math.hypot(b.x[3]-b.x[0],b.y[3]-b.y[0]);
            if (edge < Math.max(14f,side*2.3f)) continue;
            if (Math.abs(dy) >= Math.abs(dx)*2.4f) {
                // Reading direction points up -> rotate clockwise to upright.
                if (dy < 0) clockwise++; else counterclockwise++;
            } else if (Math.abs(dx) >= Math.abs(dy)*2.4f) {
                horizontal++;
            }
        }
        int tall = clockwise+counterclockwise;
        if(tall<2 || tall < horizontal*2 || Math.min(clockwise,counterclockwise)>0)
            return 0;
        return clockwise>0 ? 90 : 270;
    }

    public static String arrange(List<ReadingOrder.Box> incoming,int width,int height) {
        if(incoming==null || incoming.isEmpty())return "";
        int angle=inferredClockwiseRotation(incoming);
        if(angle==0 || width<=0 || height<=0)
            return ReadingOrder.arrangeRealtime(incoming);
        List<ReadingOrder.Box> rotated=new ArrayList<>(incoming.size());
        for(ReadingOrder.Box original:incoming) {
            float[] x=new float[4],y=new float[4];
            for(int i=0;i<4;i++) {
                if(angle==90) {
                    x[i]=height-original.y[i];y[i]=original.x[i];
                } else {
                    x[i]=original.y[i];y[i]=width-original.x[i];
                }
            }
            rotated.add(new ReadingOrder.Box(original.text,original.confidence,x,y));
        }
        return ReadingOrder.arrangeRealtime(rotated);
    }
}
