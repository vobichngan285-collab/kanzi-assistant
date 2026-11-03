package com.vobich.kanzi.craftprobe;

import java.nio.FloatBuffer;

public final class CraftBoxDecoderSelfTest {
    private static void assertTrue(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static float[] create(int w,int h) {
        return new float[w*h*2];
    }
    private static void mark(float[] raw,int w,int x0,int y0,int x1,int y1,
                             float text,float affinity) {
        for (int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++) {
            raw[(y*w+x)*2]=text;
            raw[(y*w+x)*2+1]=affinity;
        }
    }
    public static void main(String[] args) {
        int w=64,h=64;
        float[] map=create(w,h);
        // One horizontal word; original photo 1536x2048 but the CRAFT
        // neural input is exactly 128x128 for this synthetic fixture.
        mark(map,w,10,13,34,19,.92f,.12f);
        CraftBoxDecoder.Result one=CraftBoxDecoder.decode(
            FloatBuffer.wrap(map),h,w,1536,2048,128,128);
        assertTrue(one.boxes.size()==1, "one expected box, got "+one.boxes.size());
        float[] quad=one.boxes.get(0).quad;
        float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
        float minY=Float.POSITIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
        for(int i=0;i<4;i++) {
            minX=Math.min(minX,quad[2*i]);maxX=Math.max(maxX,quad[2*i]);
            minY=Math.min(minY,quad[2*i+1]);maxY=Math.max(maxY,quad[2*i+1]);
        }
        assertTrue(minX<maxX && minY<maxY, "non-degenerate quad");
        assertTrue(minX>100 && maxX<1300, "source coordinate mapping invalid");
        assertTrue(minY>200 && maxY<1600, "portrait coordinate mapping invalid");
        // Below text threshold => component should be rejected.
        float[] faint=create(w,h);
        mark(faint,w,5,6,25,15,.50f,.30f);
        CraftBoxDecoder.Result rejected=CraftBoxDecoder.decode(
            FloatBuffer.wrap(faint),h,w,512,512,128,128);
        assertTrue(rejected.boxes.isEmpty(), "low-confidence box should be rejected");
        // Two separate boxes.
        float[] doubleMap=create(w,h);
        mark(doubleMap,w,3,4,17,10,.91f,0);
        mark(doubleMap,w,38,39,58,47,.93f,0);
        CraftBoxDecoder.Result two=CraftBoxDecoder.decode(
            FloatBuffer.wrap(doubleMap),h,w,768,768,128,128);
        assertTrue(two.boxes.size()==2, "two boxes expected, found "+two.boxes.size());
        // Link-only region ignored if no high text score.
        float[] links=create(w,h);
        mark(links,w,10,10,30,20,.20f,.70f);
        CraftBoxDecoder.Result noText=CraftBoxDecoder.decode(
            FloatBuffer.wrap(links),h,w,768,768,128,128);
        assertTrue(noText.boxes.isEmpty(), "link-only region must not be classified as text");
        System.out.println("CRAFT_BOX_JAVA_SYNTHETIC_TESTS_PASS");
    }
}
