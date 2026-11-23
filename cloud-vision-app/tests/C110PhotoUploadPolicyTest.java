package com.vobich.bluestar.cloudprobe;
public final class C110PhotoUploadPolicyTest {
    static void test(boolean b){if(!b)throw new AssertionError("C110 upload mode");}
    static void near(double a,double b){test(Math.abs(a-b)<.001);}
    public static void main(String[] args){
        test(C110PhotoUploadPolicy.normalize(-1)==0);
        test(C110PhotoUploadPolicy.normalize(2)==0);
        test(C110PhotoUploadPolicy.normalize(C110PhotoUploadPolicy.P1440)==1);
        test(C110PhotoUploadPolicy.normalize(C110PhotoUploadPolicy.ORIGINAL)==0);
        near(C110PhotoUploadPolicy.scaleFor(5888,3312,C110PhotoUploadPolicy.P1440),
             2560.0/5888.0);
        // Disabling optimization restores an unlimited original-JPEG pass-through.
        near(C110PhotoUploadPolicy.scaleFor(5888,3312,C110PhotoUploadPolicy.ORIGINAL),1);
        test(C110PhotoUploadPolicy.MAX_PIXELS==3686400L);
        near(C110PhotoUploadPolicy.scaleFor(5888,3312,0),1);
        near(5888*C110PhotoUploadPolicy.scaleFor(5888,3312,1),2560);
        near(3312*C110PhotoUploadPolicy.scaleFor(5888,3312,1),1440);
        near(C110PhotoUploadPolicy.scaleFor(1920,1080,1),1);
        try{C110PhotoUploadPolicy.scaleFor(0,100,1);throw new AssertionError("invalid");}
        catch(IllegalArgumentException correct){}
        System.out.println("PASS: C110 native photo unchanged; 1440P only after download");
    }
}
