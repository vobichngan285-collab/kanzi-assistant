package com.vobich.kanzi.craftprobe;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** No Android runtime required; verifies exact PP-OCRv6 Small CTC blank/repeat mapping. */
public final class SmallRecCtcSelfTest {
    private static void check(boolean p, String msg) {
        if(!p) throw new AssertionError(msg);
    }
    private static FloatBuffer ctc(int classes, int... indices) {
        float[] x=new float[classes*indices.length];
        Arrays.fill(x,-.01f);
        for(int i=0;i<indices.length;i++)
            x[i*classes+indices[i]]=.93f;
        return FloatBuffer.wrap(x);
    }
    public static void main(String[] args) {
        List<String> labels=Arrays.asList("你","好"," ","A");
        SmallRecCtc.Result one=SmallRecCtc.decode(
            ctc(5,0,1,1,0,1,2,2,0,3,4),
            new long[]{1,10,5},labels);
        check("你你好 A".equals(one.text), "CTC repeat/blank decoded incorrectly: "+one.text);
        check(Math.abs(one.confidence-.93f)<1e-4f, "Confidence mean wrong");
        SmallRecCtc.Result none=SmallRecCtc.decode(
            ctc(5,0,0,0,0),new long[]{1,4,5},labels);
        check(none.text.isEmpty() && none.confidence==0f, "Blank-only not empty");
        SmallRecCtc.Result unique=SmallRecCtc.decode(
            ctc(5,2,2,0,2),new long[]{1,4,5},labels);
        check("好好".equals(unique.text),"Repeated glyph should require blank separator");
        boolean refused=false;
        try {
            SmallRecCtc.decode(ctc(5,2),new long[]{1,1,6},labels);
        } catch(IllegalArgumentException expected){refused=true;}
        check(refused,"Wrong ONNX class-count should fail closed");
        System.out.println("SMALL_REC_CTC_SELFTEST_PASS");
    }
}
